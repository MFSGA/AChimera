mod controller;
pub mod log;

#[global_allocator]
static GLOBAL: ::mimalloc::MiMalloc = ::mimalloc::MiMalloc;

use clash_lib::{
    config::def::DNSMode,
    set_socket_protector, start, Config as ClashConfig, SocketProtector,
};
use ipnet::{IpNet, Ipv4Net};
use jni::objects::{Global, JObject, JString, JValue};
use jni::signature::{JavaType, MethodSignature, Primitive};
use jni::sys::{jboolean, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::{jni_str, EnvUnowned, JavaVM, Outcome};
use reqwest::redirect::Policy;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::net::Ipv4Addr;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, Once, OnceLock};
use tokio::runtime::Runtime;
use tokio::sync::broadcast;
use tokio::task::JoinHandle;
use tokio_stream::StreamExt;
use tracing::{error, info};
use tracing_subscriber::filter::LevelFilter;

static INSTANCE: OnceLock<ClashInstance> = OnceLock::new();
static SOCKET_PROTECTOR_INSTALLED: OnceLock<()> = OnceLock::new();
static INIT: Once = Once::new();

struct ClashInstance {
    jvm: JavaVM,
    chimera_ffi: Global<JObject<'static>>,
    rt: OnceLock<Runtime>,
    core_state: Mutex<Option<CoreState>>,
    last_error: Mutex<Option<String>>,
    core_running: AtomicBool,
}

impl ClashInstance {
    fn runtime(&self) -> &Runtime {
        self.rt.get_or_init(|| {
            let jvm = self.jvm.clone();
            let mut builder = tokio::runtime::Builder::new_multi_thread();
            builder.enable_all();
            builder.on_thread_start(move || {
                let _ = jvm.attach_current_thread(|_| Ok::<(), jni::errors::Error>(()));
            });
            builder
                .build()
                .expect("failed to create chimera tokio runtime")
        })
    }
}

fn instance() -> &'static ClashInstance {
    INSTANCE.get().expect("ClashInstance not initialized")
}

use log::init_logger;

struct CoreState {
    worker: JoinHandle<()>,
    metadata: CoreMetadata,
}

#[derive(Clone)]
struct CoreMetadata {
    profile_name: String,
    tun_fd: i32,
    work_dir: PathBuf,
    log_path: PathBuf,
    socket_path: PathBuf,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ChimeraError {
    #[error("{details}")]
    Runtime { details: String },
}

#[derive(uniffi::Record)]
pub struct ProfileOverride {
    pub tun_fd: i32,
    pub log_file_path: String,

    #[uniffi(default = 7890)]
    pub mixed_port: u16,

    #[uniffi(default = false)]
    pub fake_ip: bool,

    #[uniffi(default = "198.18.0.2/16")]
    pub fake_ip_range: String,

    #[uniffi(default = true)]
    pub ipv6: bool,
}

#[derive(uniffi::Record, Default)]
pub struct FinalProfile {
    #[uniffi(default = 7890)]
    pub mixed_port: u16,
}

#[derive(uniffi::Record)]
pub struct DownloadResult {
    pub success: bool,
    pub file_size: u64,
    pub error_message: Option<String>,
}

#[derive(uniffi::Record, Clone)]
pub struct DownloadProgress {
    pub downloaded: u64,
    pub total: u64,
}

#[uniffi::export(callback_interface)]
pub trait DownloadProgressCallback: Send + Sync {
    fn on_progress(&self, progress: DownloadProgress);
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

fn runtime() -> &'static Runtime {
    instance().runtime()
}

fn set_last_error(message: impl Into<String>) {
    let message = message.into();
    error!("{message}");
    if let Ok(mut guard) = instance().last_error.lock() {
        *guard = Some(message);
    }
}

fn clear_last_error() {
    if let Ok(mut guard) = instance().last_error.lock() {
        *guard = None;
    }
}

fn runtime_error(message: impl Into<String>) -> ChimeraError {
    ChimeraError::Runtime {
        details: message.into(),
    }
}

fn reqwest_error(prefix: &str, error: impl std::fmt::Display) -> ChimeraError {
    runtime_error(format!("{prefix}: {error}"))
}

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
    match env
        .with_env(|env| value.try_to_string(env))
        .into_outcome()
    {
        Outcome::Ok(value) => Ok(value),
        Outcome::Err(error) => {
            Err(format!("failed to read JNI string {field_name}: {error}"))
        }
        Outcome::Panic(_) => Err(format!("failed to read JNI string {field_name}: JNI panic")),
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

    if let Some(state) = running {
        state.worker.abort();
        let _ = fs::remove_file(state.metadata.socket_path);
        log_line(&state.metadata.log_path, "chimera core stop requested");
    }

    instance().core_running.store(false, Ordering::SeqCst);
    clear_last_error();
    Ok(())
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

    let mut config = ClashConfig::File(profile_path_string.clone())
        .try_parse()
        .map_err(|error| {
            format!(
                "failed to parse profile {}: {error}",
                profile_path.display()
            )
        })?;

    config.tun.enable = true;
    config.tun.device_id = format!("fd://{tun_fd}");
    config.tun.route_all = false;
    config.tun.routes = Vec::new();
    config.tun.gateway = Ipv4Net::new(Ipv4Addr::new(10, 0, 0, 1), 30)
        .map_err(|error| format!("failed to build tun gateway: {error}"))?;
    config.tun.gateway_v6 = None;
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
    config.dns.listen.udp = Some("127.0.0.1:53553".parse().unwrap());
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

    let mixed_port = config
        .listeners
        .iter()
        .find_map(|l| {
            if let clash_lib::config::listener::InboundOpts::Mixed { common_opts, .. } = l {
                Some(common_opts.port)
            } else {
                None
            }
        })
        .unwrap_or(over.mixed_port);
    let final_profile = FinalProfile { mixed_port };

    let runtime_log_path = log_path.clone();
    let worker = runtime().spawn(async move {
        let (log_tx, _) = broadcast::channel(100);
        log_line(
            &runtime_log_path,
            &format!(
                "starting clash core: profile={} tun_fd={} work_dir={}",
                profile_path_string, tun_fd, work_dir_string
            ),
        );
        if let Err(error) = start(
            config,
            work_dir_string,
            Some(profile_path_string.clone()),
            log_tx,
        )
        .await
        {
            let message = format!("clash core exited with error: {error}");
            set_last_error(message.clone());
            log_line(&runtime_log_path, &message);
        } else {
            log_line(&runtime_log_path, "clash core exited");
        }
        instance().core_running.store(false, Ordering::SeqCst);
    });

    {
        let mut guard = instance()
            .core_state
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        *guard = Some(CoreState { worker, metadata });
    }
    instance().core_running.store(true, Ordering::SeqCst);
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

    Ok("Config is valid".to_string())
}

#[uniffi::export]
fn shutdown() -> Result<(), ChimeraError> {
    stop_core_internal().map_err(runtime_error)
}

#[uniffi::export(async_runtime = "tokio")]
async fn download_file(
    url: String,
    output_path: String,
    user_agent: Option<String>,
    proxy_url: Option<String>,
) -> Result<DownloadResult, ChimeraError> {
    download_file_with_progress(url, output_path, user_agent, proxy_url, None).await
}

#[uniffi::export(async_runtime = "tokio")]
async fn download_file_with_progress(
    url: String,
    output_path: String,
    user_agent: Option<String>,
    proxy_url: Option<String>,
    progress_callback: Option<Box<dyn DownloadProgressCallback>>,
) -> Result<DownloadResult, ChimeraError> {
    let user_agent = user_agent.unwrap_or_else(|| "chimera-android/0.1.0".to_string());
    let mut client_builder = reqwest::Client::builder()
        .user_agent(user_agent)
        .redirect(Policy::limited(10));

    if let Some(proxy_url) = proxy_url.filter(|it| !it.trim().is_empty()) {
        let proxy = reqwest::Proxy::all(&proxy_url)
            .map_err(|error| reqwest_error("invalid proxy url", error))?;
        client_builder = client_builder.proxy(proxy);
    }

    let client = client_builder
        .build()
        .map_err(|error| reqwest_error("failed to build http client", error))?;

    let response = client
        .get(&url)
        .send()
        .await
        .map_err(|error| reqwest_error("failed to send request", error))?;

    let status = response.status();
    if !status.is_success() {
        return Ok(DownloadResult {
            success: false,
            file_size: 0,
            error_message: Some(format!(
                "HTTP {} - {}",
                status.as_u16(),
                status.canonical_reason().unwrap_or("Unknown")
            )),
        });
    }

    let total_size = response.content_length().unwrap_or(0);
    if let Some(callback) = progress_callback.as_ref() {
        callback.on_progress(DownloadProgress {
            downloaded: 0,
            total: total_size,
        });
    }

    let mut stream = response.bytes_stream();
    let mut downloaded = 0_u64;
    let mut buffer = Vec::new();

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|error| reqwest_error("failed to read response chunk", error))?;
        downloaded += chunk.len() as u64;
        buffer.extend_from_slice(&chunk);

        if let Some(callback) = progress_callback.as_ref() {
            callback.on_progress(DownloadProgress {
                downloaded,
                total: total_size,
            });
        }
    }

    if let Some(parent) = Path::new(&output_path).parent() {
        tokio::fs::create_dir_all(parent)
            .await
            .map_err(|error| runtime_error(format!("failed to create output dir: {error}")))?;
    }

    tokio::fs::write(&output_path, &buffer)
        .await
        .map_err(|error| runtime_error(format!("failed to write downloaded file: {error}")))?;

    Ok(DownloadResult {
        success: true,
        file_size: buffer.len() as u64,
        error_message: None,
    })
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

    let chimera_ffi = match env.with_env(|env| env.new_global_ref(&_this)).into_outcome() {
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
        mixed_port: 7890,
        fake_ip: false,
        fake_ip_range: "198.18.0.2/16".to_string(),
        ipv6: true,
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

uniffi::setup_scaffolding!("chimera_ffi");
