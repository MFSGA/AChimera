mod android_bridge;
mod controller;
mod controller_models;
mod controller_provider_dns;
mod controller_transport;
mod core_lifecycle;
mod core_runtime;
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

use clash_lib::Config as ClashConfig;
#[cfg(test)]
use clash_lib::config::def::{Config as ConfigDef, Port};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::Once;
#[cfg(test)]
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use tracing::info;

static INIT: Once = Once::new();

use android_bridge::install_socket_protector;
use core_runtime::{start_core_internal, stop_core_internal};
use core_state::INSTANCE;

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

fn runtime_error(message: impl Into<String>) -> ChimeraError {
    ChimeraError::Runtime {
        details: message.into(),
    }
}

#[cfg(test)]
use core_lifecycle::{should_notify_core_stopped, wait_for_core_ready, wait_for_worker_shutdown};
use runtime_config::validate_profile_runtime_handlers;
#[cfg(test)]
use runtime_config::{
    apply_listener_defaults, load_runtime_config, validate_runtime_proxy_handlers,
};

pub(crate) fn log_line(log_path: &Path, message: &str) {
    info!("{message}");
    let file = OpenOptions::new().append(true).create(true).open(log_path);
    let Ok(mut file) = file else {
        return;
    };
    let _ = writeln!(file, "[{message}]");
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
    use std::time::Duration;
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
