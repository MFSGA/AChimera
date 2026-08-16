mod controller;
mod controller_models;
mod controller_provider_dns;
mod controller_transport;
mod core_lifecycle;
mod core_state;
mod jni_bridge;
pub mod log;
#[cfg(test)]
mod protocol_compat_tests;
mod runtime_config;
pub mod util;
#[cfg(test)]
mod util_tests;

#[global_allocator]
static GLOBAL: ::mimalloc::MiMalloc = ::mimalloc::MiMalloc;

#[cfg(test)]
use clash_lib::config::def::{Config as ConfigDef, Port};
use clash_lib::{
    Config as ClashConfig, SocketProtector, config::def::DNSMode, set_socket_protector,
    shutdown as clash_shutdown, start,
};
use ipnet::{IpNet, Ipv4Net, Ipv6Net};
use jni::objects::{JString, JValue};
use jni::signature::{JavaType, MethodSignature, Primitive};
use jni::{EnvUnowned, Outcome, jni_str};
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::net::{Ipv4Addr, Ipv6Addr, SocketAddr};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Once, OnceLock};
use std::time::Duration;
use tokio::sync::broadcast;
use tracing::{error, info};
use tracing_subscriber::filter::LevelFilter;

static SOCKET_PROTECTOR_INSTALLED: OnceLock<()> = OnceLock::new();
static INIT: Once = Once::new();

use core_state::{
    CoreMetadata, CoreState, INSTANCE, clear_last_error, instance, runtime, set_last_error,
};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ChimeraError {
    #[error("{details}")]
    Runtime { details: String },
}

#[derive(uniffi::Record)]
pub struct ProfileOverride {
    pub tun_fd: i32,
    pub log_file_path: String,

    #[uniffi(default = false)]
    pub allow_lan: bool,

    #[uniffi(default = 7890)]
    pub mixed_port: u16,

    #[uniffi(default = None)]
    pub http_port: Option<u16>,

    #[uniffi(default = None)]
    pub socks_port: Option<u16>,

    #[uniffi(default = false)]
    pub fake_ip: bool,

    #[uniffi(default = "198.18.0.2/16")]
    pub fake_ip_range: String,

    #[uniffi(default = false)]
    pub ipv6: bool,
}

#[derive(uniffi::Record, Default)]
pub struct FinalProfile {
    #[uniffi(default = 7890)]
    pub mixed_port: u16,
}

struct AndroidSocketProtector;

impl SocketProtector for AndroidSocketProtector {
    fn protect_socket_handle(&self, handle: usize) -> std::io::Result<()> {
        let fd = i32::try_from(handle).map_err(|_| {
            std::io::Error::other(format!("socket handle out of i32 range: {handle}"))
        })?;
        let inst = instance();
        let protected = inst
            .jvm
            .attach_current_thread(|env| {
                let protect_socket_sig = unsafe {
                    MethodSignature::from_raw_parts(
                        jni_str!("(I)Z"),
                        &[JavaType::Primitive(Primitive::Int)],
                        JavaType::Primitive(Primitive::Boolean),
                    )
                };
                env.call_method(
                    inst.chimera_ffi.as_obj(),
                    jni_str!("protectSocket"),
                    protect_socket_sig,
                    &[JValue::Int(fd)],
                )
                .and_then(|value| value.z())
            })
            .map_err(|error| {
                std::io::Error::other(format!(
                    "failed to call ChimeraFfi.protectSocket({fd}): {error}"
                ))
            })?;

        if protected {
            Ok(())
        } else {
            Err(std::io::Error::other(format!(
                "VpnService.protect({fd}) returned false"
            )))
        }
    }
}

fn install_socket_protector() {
    if SOCKET_PROTECTOR_INSTALLED.get().is_some() {
        return;
    }

    set_socket_protector(Arc::new(AndroidSocketProtector));
    let _ = SOCKET_PROTECTOR_INSTALLED.set(());
}

fn runtime_error(message: impl Into<String>) -> ChimeraError {
    ChimeraError::Runtime {
        details: message.into(),
    }
}

use core_lifecycle::{
    CORE_START_TIMEOUT, CORE_STOP_TIMEOUT, should_notify_core_stopped, wait_for_core_ready,
    wait_for_worker_shutdown,
};
#[cfg(test)]
use runtime_config::{apply_listener_defaults, validate_runtime_proxy_handlers};
use runtime_config::{load_runtime_config, validate_profile_runtime_handlers};

fn log_line(log_path: &Path, message: &str) {
    info!("{message}");
    let file = OpenOptions::new().append(true).create(true).open(log_path);
    let Ok(mut file) = file else {
        return;
    };
    let _ = writeln!(file, "[{message}]");
}

fn extract_jstring(
    env: &mut EnvUnowned<'_>,
    value: JString<'_>,
    field_name: &str,
) -> Result<String, String> {
    match env.with_env(|env| value.try_to_string(env)).into_outcome() {
        Outcome::Ok(value) => Ok(value),
        Outcome::Err(error) => Err(format!("failed to read JNI string {field_name}: {error}")),
        Outcome::Panic(_) => Err(format!("failed to read JNI string {field_name}: JNI panic")),
    }
}

fn notify_core_stopped(message: &str) {
    let inst = instance();
    let result = inst.jvm.attach_current_thread(|env| {
        let message = env.new_string(message)?;
        let callback_sig = unsafe {
            MethodSignature::from_raw_parts(
                jni_str!("(Ljava/lang/String;)V"),
                &[JavaType::Object],
                JavaType::Primitive(Primitive::Void),
            )
        };
        env.call_method(
            inst.chimera_ffi.as_obj(),
            jni_str!("onCoreStopped"),
            callback_sig,
            &[JValue::Object(&message)],
        )?;
        Ok::<(), jni::errors::Error>(())
    });

    if let Err(error) = result {
        error!("failed to notify Android about core exit: {error}");
    }
}

fn stop_core_internal() -> Result<(), String> {
    let running = {
        let mut guard = instance()
            .core_state
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        guard.take()
    };

    let result = if let Some(state) = running {
        let CoreState {
            worker,
            notify_exit,
            metadata,
        } = state;
        notify_exit.store(false, Ordering::SeqCst);
        let shutdown_sent = clash_shutdown();
        log_line(
            &metadata.log_path,
            if shutdown_sent {
                "chimera core graceful stop requested"
            } else {
                "chimera core stop requested without active shutdown token"
            },
        );
        let result = runtime().block_on(wait_for_worker_shutdown(worker, CORE_STOP_TIMEOUT));
        let _ = fs::remove_file(metadata.socket_path);
        result
    } else {
        Ok(())
    };

    instance().core_running.store(false, Ordering::SeqCst);
    if result.is_ok() {
        clear_last_error();
    }
    result
}

fn start_core_internal(
    profile_path: String,
    cache_dir: String,
    tun_fd: i32,
    over: ProfileOverride,
) -> Result<FinalProfile, String> {
    if profile_path.trim().is_empty() {
        return Err("profile path is empty".to_string());
    }
    if cache_dir.trim().is_empty() {
        return Err("cache dir is empty".to_string());
    }
    if tun_fd <= 0 {
        return Err(format!("invalid tun fd: {tun_fd}"));
    }
    if over.log_file_path.trim().is_empty() {
        return Err("log file path is empty".to_string());
    }
    let profile_path = PathBuf::from(profile_path);
    if !profile_path.exists() {
        return Err(format!(
            "profile file not found: {}",
            profile_path.display()
        ));
    }
    if !profile_path.is_file() {
        return Err(format!(
            "profile path is not a file: {}",
            profile_path.display()
        ));
    }

    let work_dir = PathBuf::from(cache_dir);
    fs::create_dir_all(&work_dir)
        .map_err(|error| format!("failed to create work dir {}: {error}", work_dir.display()))?;

    let mut log_path = PathBuf::from(&over.log_file_path);
    if !log_path.is_absolute() {
        log_path = work_dir.join(log_path);
    }
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)
            .map_err(|error| format!("failed to create log dir {}: {error}", parent.display()))?;
    }
    File::create(&log_path)
        .map_err(|error| format!("failed to create log file {}: {error}", log_path.display()))?;

    let socket_path = work_dir.join("clash.sock");
    let _ = fs::remove_file(&socket_path);

    stop_core_internal()?;
    clear_last_error();

    let work_dir_string = work_dir
        .to_str()
        .ok_or_else(|| "work dir contains invalid UTF-8".to_string())?
        .to_string();

    std::env::set_current_dir(&work_dir).map_err(|error| {
        format!(
            "failed to switch process cwd to {}: {error}",
            work_dir.display()
        )
    })?;

    let profile_path_string = profile_path
        .to_str()
        .ok_or_else(|| "profile path contains invalid UTF-8".to_string())?
        .to_string();

    let (mut config, mixed_port) = load_runtime_config(&profile_path, &over)?;

    config.tun.enable = true;
    config.tun.device_id = format!("fd://{tun_fd}");
    config.tun.route_all = false;
    config.tun.routes = Vec::new();
    config.tun.gateway = Ipv4Net::new(Ipv4Addr::new(10, 0, 0, 1), 30)
        .map_err(|error| format!("failed to build tun gateway: {error}"))?;
    config.tun.gateway_v6 = if over.ipv6 {
        Some(
            Ipv6Net::new(
                "fdfe:dcba:9876::1"
                    .parse::<Ipv6Addr>()
                    .map_err(|error| format!("failed to parse tun IPv6 gateway: {error}"))?,
                126,
            )
            .map_err(|error| format!("failed to build tun IPv6 gateway: {error}"))?,
        )
    } else {
        None
    };
    config.tun.mtu = None;
    config.tun.so_mark = None;
    config.tun.route_table = 0;
    config.tun.dns_hijack = true;

    config.general.ipv6 = over.ipv6;
    config.general.mmdb = Some("Country.mmdb".to_string());
    config.general.controller.external_controller_ipc =
        Some(socket_path.to_string_lossy().to_string());

    config.dns.enable = true;
    config.dns.ipv6 = over.ipv6;
    config.dns.listen.udp = Some(SocketAddr::from(([127, 0, 0, 1], 53_553)));
    if over.fake_ip {
        config.dns.enhance_mode = DNSMode::FakeIp;
        config.dns.fake_ip_range = over
            .fake_ip_range
            .parse::<IpNet>()
            .map_err(|error| format!("invalid fake-ip-range: {error}"))?;
    } else {
        config.dns.enhance_mode = DNSMode::Normal;
    }

    let profile_name = profile_path
        .file_name()
        .and_then(|it| it.to_str())
        .unwrap_or("profile.yaml")
        .to_string();
    let metadata = CoreMetadata {
        profile_name,
        tun_fd,
        work_dir: work_dir.clone(),
        log_path: log_path.clone(),
        socket_path: socket_path.clone(),
    };

    let final_profile = FinalProfile { mixed_port };

    let runtime_log_path = log_path.clone();
    let profile_label = PathBuf::from(&profile_path_string)
        .file_name()
        .and_then(|name| name.to_str())
        .filter(|name| !name.is_empty())
        .unwrap_or("unknown-profile")
        .to_string();
    let ready = Arc::new(AtomicBool::new(false));
    let worker_ready = ready.clone();
    instance().core_running.store(true, Ordering::SeqCst);
    let mut worker = runtime().spawn(async move {
        let (log_tx, _) = broadcast::channel(100);
        log_line(
            &runtime_log_path,
            &format!("starting clash core: profile={profile_label} tun_fd={tun_fd}"),
        );
        let result = start(
            config,
            work_dir_string,
            Some(profile_path_string.clone()),
            log_tx,
        )
        .await
        .map_err(|error| format!("clash core exited with error: {error}"));

        let exit_message = match &result {
            Ok(()) => {
                let message = "clash core exited unexpectedly".to_string();
                log_line(&runtime_log_path, &message);
                message
            }
            Err(message) => {
                set_last_error(message.clone());
                log_line(&runtime_log_path, message);
                message.clone()
            }
        };
        instance().core_running.store(false, Ordering::SeqCst);
        if should_notify_core_stopped(worker_ready.as_ref()) {
            notify_core_stopped(&exit_message);
        }
        result
    });

    if let Err(error) = runtime().block_on(wait_for_core_ready(
        &socket_path,
        &mut worker,
        ready.as_ref(),
        CORE_START_TIMEOUT,
    )) {
        let _ = clash_shutdown();
        worker.abort();
        let _ = fs::remove_file(&socket_path);
        instance().core_running.store(false, Ordering::SeqCst);
        set_last_error(error.clone());
        log_line(&log_path, &error);
        return Err(error);
    }

    {
        let mut guard = instance()
            .core_state
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        *guard = Some(CoreState {
            worker,
            notify_exit: ready,
            metadata,
        });
    }
    log_line(&log_path, "clash core controller is ready");
    Ok(final_profile)
}

fn build_hello_message() -> String {
    let inst = match INSTANCE.get() {
        Some(inst) => inst,
        None => return "ffi: jni not setup".to_string(),
    };

    if inst.core_running.load(Ordering::SeqCst) {
        if let Ok(guard) = inst.core_state.lock()
            && let Some(state) = guard.as_ref()
        {
            return format!(
                "ffi: core running {} tun={} ({})",
                state.metadata.profile_name,
                state.metadata.tun_fd,
                state.metadata.work_dir.display()
            );
        }
        return "ffi: core running".to_string();
    }

    if let Some(last_error) = inst.last_error.lock().ok().and_then(|it| it.clone()) {
        return format!("ffi: core stopped ({last_error})");
    }
    "ffi: core stopped".to_string()
}

#[uniffi::export]
fn hello() -> String {
    build_hello_message()
}

#[uniffi::export]
fn run_clash(
    config_path: String,
    work_dir: String,
    over: ProfileOverride,
) -> Result<FinalProfile, ChimeraError> {
    let tun_fd = over.tun_fd;
    start_core_internal(config_path, work_dir, tun_fd, over).map_err(runtime_error)
}

#[uniffi::export]
fn verify_config(config_path: String) -> Result<String, ChimeraError> {
    let path = PathBuf::from(&config_path);
    let profile_content = fs::read_to_string(&path)
        .map_err(|error| runtime_error(format!("failed to read config file: {error}")))?;

    if profile_content.trim().is_empty() {
        return Err(runtime_error("config file is empty"));
    }

    let _config = ClashConfig::File(config_path)
        .try_parse()
        .map_err(|error| runtime_error(format!("invalid config: {error}")))?;
    validate_profile_runtime_handlers(&path)
        .map_err(|error| runtime_error(format!("invalid runtime config: {error}")))?;

    Ok("Config is valid".to_string())
}

#[uniffi::export]
fn shutdown() -> Result<(), ChimeraError> {
    stop_core_internal().map_err(runtime_error)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeSetup(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jboolean {
    let vm = match env.with_env(|env| env.get_java_vm()).into_outcome() {
        Outcome::Ok(vm) => vm,
        Outcome::Err(error) => {
            set_last_error(format!("failed to get JavaVM: {error}"));
            return JNI_FALSE;
        }
        Outcome::Panic(_) => {
            set_last_error("failed to get JavaVM: JNI panic");
            return JNI_FALSE;
        }
    };

    let chimera_ffi = match env
        .with_env(|env| env.new_global_ref(&_this))
        .into_outcome()
    {
        Outcome::Ok(reference) => reference,
        Outcome::Err(error) => {
            set_last_error(format!("failed to create ChimeraFfi global ref: {error}"));
            return JNI_FALSE;
        }
        Outcome::Panic(_) => {
            set_last_error("failed to create ChimeraFfi global ref: JNI panic");
            return JNI_FALSE;
        }
    };

    let instance = ClashInstance {
        jvm: vm,
        chimera_ffi,
        rt: OnceLock::new(),
        core_state: Mutex::new(None),
        last_error: Mutex::new(None),
        core_running: AtomicBool::new(false),
    };

    if !(INSTANCE.set(instance).is_ok() || INSTANCE.get().is_some()) {
        set_last_error("failed to initialize ClashInstance");
        return JNI_FALSE;
    }

    INIT.call_once(|| unsafe {
        let level = if cfg!(debug_assertions) {
            LevelFilter::DEBUG
        } else {
            LevelFilter::INFO
        };
        std::env::set_var("RUST_BACKTRACE", "1");
        init_logger(level);
        let _ = color_eyre::install();

        // Route panic backtraces through tracing so Android logcat keeps them.
        let previous_hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |info| {
            let backtrace = std::backtrace::Backtrace::force_capture();
            error!(target: "panic", "thread panicked: {info}\n{backtrace}");
            previous_hook(info);
        }));

        // Install aws-lc-rs as the default crypto provider
        let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();
        info!("native logger initialized");
    });

    install_socket_protector();
    let _ = runtime();
    clear_last_error();
    info!("native setup complete");
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeHello(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env
        .with_env(|env| env.new_string(build_hello_message()))
        .into_outcome()
    {
        Outcome::Ok(value) => value.into_raw(),
        _ => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStart(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
    profile_path: JString<'_>,
    cache_dir: JString<'_>,
    tun_fd: jint,
    log_file_path: JString<'_>,
) -> jboolean {
    let profile_path = match extract_jstring(&mut env, profile_path, "profile_path") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };
    let cache_dir = match extract_jstring(&mut env, cache_dir, "cache_dir") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };
    let log_file_path = match extract_jstring(&mut env, log_file_path, "log_file_path") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };

    let over = ProfileOverride {
        tun_fd,
        log_file_path,
        allow_lan: false,
        mixed_port: 7890,
        http_port: None,
        socks_port: None,
        fake_ip: false,
        fake_ip_range: "198.18.0.2/16".to_string(),
        ipv6: false,
    };

    match start_core_internal(profile_path, cache_dir, tun_fd, over) {
        Ok(_) => JNI_TRUE,
        Err(error) => {
            set_last_error(error);
            instance().core_running.store(false, Ordering::SeqCst);
            JNI_FALSE
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStop(
    _env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jboolean {
    match stop_core_internal() {
        Ok(()) => JNI_TRUE,
        Err(error) => {
            set_last_error(error);
            JNI_FALSE
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::time::sleep;

    fn profile_override() -> ProfileOverride {
        ProfileOverride {
            tun_fd: 1,
            log_file_path: "chimera.log".to_string(),
            allow_lan: true,
            mixed_port: 7890,
            http_port: Some(7891),
            socks_port: Some(7892),
            fake_ip: false,
            fake_ip_range: "198.18.0.2/16".to_string(),
            ipv6: false,
        }
    }

    fn unique_temp_path(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "chimera-{name}-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos(),
        ))
    }

    #[test]
    fn minimal_profile_parses_with_current_clash_dependency() {
        let profile_path = unique_temp_path("compatible-profile.yaml");
        fs::write(
            &profile_path,
            "mixed-port: 7890\nproxies: []\nproxy-groups: []\nrules: []\n",
        )
        .unwrap();

        let mut config = ConfigDef::try_from(profile_path).unwrap();
        let mixed_port = apply_listener_defaults(&mut config, &profile_override()).unwrap();
        let _runtime_config: clash_lib::config::RuntimeConfig = config.try_into().unwrap();

        assert_eq!(7890, mixed_port);
    }

    #[test]
    fn invalid_profile_reports_parse_error() {
        let profile_path = unique_temp_path("invalid-profile.yaml");
        fs::write(&profile_path, "mixed-port: [invalid").unwrap();

        let error = load_runtime_config(&profile_path, &profile_override())
            .err()
            .expect("invalid profile should fail");

        assert!(error.contains("failed to parse profile"));
        assert!(error.contains(profile_path.to_string_lossy().as_ref()));
        let _ = fs::remove_file(profile_path);
    }

    #[test]
    fn listener_defaults_preserve_profile_ports() {
        let mut config = ConfigDef {
            mixed_port: Some(Port(9000)),
            port: Some(Port(9001)),
            socks_port: Some(Port(9002)),
            ..Default::default()
        };

        let mixed_port = apply_listener_defaults(&mut config, &profile_override()).unwrap();

        assert_eq!(9000, mixed_port);
        assert!(matches!(config.mixed_port, Some(Port(9000))));
        assert!(matches!(config.port, Some(Port(9001))));
        assert!(matches!(config.socks_port, Some(Port(9002))));
        assert_eq!(Some(true), config.allow_lan);
    }

    #[test]
    fn listener_defaults_fill_missing_profile_ports() {
        let mut config = ConfigDef::default();

        let mixed_port = apply_listener_defaults(&mut config, &profile_override()).unwrap();

        assert_eq!(7890, mixed_port);
        assert!(matches!(config.mixed_port, Some(Port(7890))));
        assert!(matches!(config.port, Some(Port(7891))));
        assert!(matches!(config.socks_port, Some(Port(7892))));
    }

    #[test]
    fn listener_defaults_reject_zero_for_missing_port() {
        let mut config = ConfigDef::default();
        let mut over = profile_override();
        over.mixed_port = 0;

        let error = apply_listener_defaults(&mut config, &over).unwrap_err();

        assert_eq!("mixed port must be between 1 and 65535", error);
    }

    #[test]
    fn listener_defaults_ignore_invalid_fallback_when_profile_has_value() {
        let mut config = ConfigDef {
            mixed_port: Some(Port(9000)),
            ..Default::default()
        };
        let mut over = profile_override();
        over.mixed_port = 0;

        let mixed_port = apply_listener_defaults(&mut config, &over).unwrap();

        assert_eq!(9000, mixed_port);
    }

    #[test]
    fn listener_defaults_validate_only_missing_optional_ports() {
        let mut config = ConfigDef {
            mixed_port: Some(Port(9000)),
            port: Some(Port(9001)),
            ..Default::default()
        };
        let mut over = profile_override();
        over.http_port = Some(0);
        over.socks_port = Some(0);

        let error = apply_listener_defaults(&mut config, &over).unwrap_err();

        assert_eq!("socks port must be between 1 and 65535", error);
        assert!(matches!(config.port, Some(Port(9001))));
    }

    #[test]
    fn readiness_reports_worker_failure() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        let socket_path = std::env::temp_dir().join("chimera-readiness-missing.sock");

        let error = runtime.block_on(async {
            let ready = AtomicBool::new(false);
            let mut worker = tokio::spawn(async { Err("startup failed".to_string()) });
            wait_for_core_ready(&socket_path, &mut worker, &ready, Duration::from_secs(1))
                .await
                .unwrap_err()
        });

        assert_eq!("startup failed", error);
    }

    #[test]
    fn readiness_propagates_listener_port_conflict() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        let socket_path = unique_temp_path("port-conflict.sock");

        let error = runtime.block_on(async {
            let occupied = tokio::net::TcpListener::bind(("127.0.0.1", 0))
                .await
                .unwrap();
            let address = occupied.local_addr().unwrap();
            let ready = AtomicBool::new(false);
            let mut worker = tokio::spawn(async move {
                tokio::net::TcpListener::bind(address)
                    .await
                    .map(|_| ())
                    .map_err(|error| format!("failed to bind mixed-port {address}: {error}"))
            });

            wait_for_core_ready(&socket_path, &mut worker, &ready, Duration::from_secs(1))
                .await
                .unwrap_err()
        });

        assert!(error.contains("failed to bind mixed-port"));
    }

    #[cfg(unix)]
    #[test]
    fn readiness_accepts_connectable_controller_socket() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        let socket_path = std::env::temp_dir().join(format!(
            "chimera-readiness-{}-{}.sock",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos(),
        ));

        runtime.block_on(async {
            let listener = tokio::net::UnixListener::bind(&socket_path).unwrap();
            let ready = AtomicBool::new(false);
            let mut worker = tokio::spawn(async {
                std::future::pending::<()>().await;
                Ok(())
            });

            wait_for_core_ready(&socket_path, &mut worker, &ready, Duration::from_secs(1))
                .await
                .unwrap();
            assert!(ready.load(Ordering::SeqCst));

            worker.abort();
            drop(listener);
        });
        let _ = std::fs::remove_file(socket_path);
    }

    #[test]
    fn core_exit_notification_only_runs_after_ready() {
        let notify_exit = AtomicBool::new(false);

        assert!(!should_notify_core_stopped(&notify_exit));
        notify_exit.store(true, Ordering::SeqCst);
        assert!(should_notify_core_stopped(&notify_exit));
        notify_exit.store(false, Ordering::SeqCst);
        assert!(!should_notify_core_stopped(&notify_exit));
    }

    #[test]
    fn shutdown_waits_for_worker_completion() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();

        runtime.block_on(async {
            let worker = tokio::spawn(async {
                sleep(Duration::from_millis(10)).await;
                Ok(())
            });

            wait_for_worker_shutdown(worker, Duration::from_secs(1))
                .await
                .unwrap();
        });
    }

    #[test]
    fn shutdown_accepts_worker_error_after_exit() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();

        runtime.block_on(async {
            let worker = tokio::spawn(async { Err("runtime failed".to_string()) });

            wait_for_worker_shutdown(worker, Duration::from_secs(1))
                .await
                .unwrap();
        });
    }

    #[test]
    fn shutdown_aborts_worker_after_timeout() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();

        let error = runtime.block_on(async {
            let worker = tokio::spawn(async {
                std::future::pending::<()>().await;
                Ok(())
            });

            wait_for_worker_shutdown(worker, Duration::from_millis(10))
                .await
                .unwrap_err()
        });

        assert!(error.contains("timed out waiting for clash core shutdown"));
    }
}

uniffi::setup_scaffolding!("chimera_ffi");
