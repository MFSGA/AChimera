mod controller;

use clash_lib::{
    initialize_logging, set_socket_protector, start, Config as ClashConfig, SocketProtector,
};
use ipnet::Ipv4Net;
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::sys::{jboolean, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::{JNIEnv, JavaVM};
use reqwest::redirect::Policy;
use serde_yaml::{Mapping, Number, Value};
use std::collections::hash_map::DefaultHasher;
use std::fs::{self, File, OpenOptions};
use std::hash::{Hash, Hasher};
use std::io::Write;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, Once, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::runtime::Runtime;
use tokio::sync::broadcast;
use tokio::task::JoinHandle;
use tokio_stream::StreamExt;

static CORE_RUNNING: AtomicBool = AtomicBool::new(false);
static CORE_STATE: OnceLock<Mutex<Option<CoreState>>> = OnceLock::new();
static LAST_ERROR: OnceLock<Mutex<Option<String>>> = OnceLock::new();
static JVM: OnceLock<JavaVM> = OnceLock::new();
static CHIMERA_FFI: OnceLock<GlobalRef> = OnceLock::new();
static SOCKET_PROTECTOR_INSTALLED: OnceLock<()> = OnceLock::new();
static RT: OnceLock<Runtime> = OnceLock::new();
static INIT: Once = Once::new();

struct CoreState {
    worker: JoinHandle<()>,
    metadata: CoreMetadata,
}

#[derive(Clone)]
struct CoreMetadata {
    profile_name: String,
    profile_checksum: u64,
    tun_fd: i32,
    work_dir: PathBuf,
    log_path: PathBuf,
    socket_path: PathBuf,
    started_at_epoch_secs: u64,
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
    fn protect_socket_fd(&self, fd: i32) -> std::io::Result<()> {
        let vm = JVM
            .get()
            .ok_or_else(|| std::io::Error::other("JavaVM not initialized"))?;
        let chimera_ffi = CHIMERA_FFI
            .get()
            .ok_or_else(|| std::io::Error::other("ChimeraFfi object not initialized"))?;
        let mut env = vm.attach_current_thread_permanently().map_err(|error| {
            std::io::Error::other(format!("failed to attach current thread: {error}"))
        })?;
        let protected = env
            .call_method(
                chimera_ffi.as_obj(),
                "protectSocket",
                "(I)Z",
                &[JValue::Int(fd)],
            )
            .and_then(|value| value.z())
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
    RT.get_or_init(|| {
        let mut builder = tokio::runtime::Builder::new_multi_thread();
        builder.enable_all();
        builder.on_thread_start(|| {
            if let Some(vm) = JVM.get() {
                let _ = vm.attach_current_thread_permanently();
            }
        });
        builder
            .build()
            .expect("failed to create chimera tokio runtime")
    })
}

fn core_state() -> &'static Mutex<Option<CoreState>> {
    CORE_STATE.get_or_init(|| Mutex::new(None))
}

fn last_error_state() -> &'static Mutex<Option<String>> {
    LAST_ERROR.get_or_init(|| Mutex::new(None))
}

fn now_epoch_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|it| it.as_secs())
        .unwrap_or(0)
}

fn profile_checksum(content: &str) -> u64 {
    let mut hasher = DefaultHasher::new();
    content.hash(&mut hasher);
    hasher.finish()
}

fn set_last_error(message: impl Into<String>) {
    if let Ok(mut guard) = last_error_state().lock() {
        *guard = Some(message.into());
    }
}

fn clear_last_error() {
    if let Ok(mut guard) = last_error_state().lock() {
        *guard = None;
    }
}

fn read_last_error() -> Option<String> {
    last_error_state().lock().ok().and_then(|it| it.clone())
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
    let file = OpenOptions::new().append(true).create(true).open(log_path);
    let Ok(mut file) = file else {
        return;
    };
    let _ = writeln!(file, "[{}] {}", now_epoch_secs(), message);
}

fn yaml_key(key: &str) -> Value {
    Value::String(key.to_string())
}

fn yaml_string(value: impl Into<String>) -> Value {
    Value::String(value.into())
}

fn yaml_bool(value: bool) -> Value {
    Value::Bool(value)
}

fn yaml_u16(value: u16) -> Value {
    Value::Number(Number::from(value))
}

fn mapping_mut(value: &mut Value) -> Result<&mut Mapping, String> {
    value
        .as_mapping_mut()
        .ok_or_else(|| "config root is not a mapping".to_string())
}

fn ensure_mapping<'a>(parent: &'a mut Mapping, key: &str) -> &'a mut Mapping {
    let key_value = yaml_key(key);
    let needs_replace = !matches!(parent.get(&key_value), Some(Value::Mapping(_)));
    if needs_replace {
        parent.insert(key_value.clone(), Value::Mapping(Mapping::new()));
    }

    parent
        .get_mut(&key_value)
        .and_then(Value::as_mapping_mut)
        .expect("mapping entry should exist")
}

fn set_or_insert(parent: &mut Mapping, key: &str, value: Value) {
    parent.insert(yaml_key(key), value);
}

fn insert_if_missing(parent: &mut Mapping, key: &str, value: Value) {
    parent.entry(yaml_key(key)).or_insert(value);
}

fn insert_sequence_if_missing(parent: &mut Mapping, key: &str, values: &[Value]) {
    if parent.contains_key(yaml_key(key)) {
        return;
    }
    parent.insert(yaml_key(key), Value::Sequence(values.to_vec()));
}

fn current_mixed_port(root: &Mapping, default_port: u16) -> u16 {
    root.get(yaml_key("mixed-port"))
        .and_then(Value::as_u64)
        .and_then(|value| u16::try_from(value).ok())
        .unwrap_or(default_port)
}

fn build_runtime_config(
    profile_content: &str,
    socket_path: &Path,
    over: &ProfileOverride,
) -> Result<(String, FinalProfile), String> {
    let mut value: Value = serde_yaml::from_str(profile_content)
        .map_err(|error| format!("failed to parse profile yaml: {error}"))?;
    value
        .apply_merge()
        .map_err(|error| format!("failed to resolve yaml anchors: {error}"))?;

    let root = mapping_mut(&mut value)?;
    insert_if_missing(root, "mixed-port", yaml_u16(over.mixed_port));
    if let Some(http_port) = over.http_port {
        insert_if_missing(root, "port", yaml_u16(http_port));
    }
    if let Some(socks_port) = over.socks_port {
        insert_if_missing(root, "socks-port", yaml_u16(socks_port));
    }

    set_or_insert(
        root,
        "external-controller-unix",
        yaml_string(socket_path.to_string_lossy().to_string()),
    );
    set_or_insert(root, "mmdb", yaml_string("Country.mmdb"));
    set_or_insert(root, "geosite", yaml_string("geosite.dat"));
    set_or_insert(root, "asn-mmdb", Value::Null);
    set_or_insert(root, "ipv6", yaml_bool(over.ipv6));

    let tun = ensure_mapping(root, "tun");
    set_or_insert(tun, "enable", yaml_bool(true));
    set_or_insert(tun, "device", yaml_string(format!("fd://{}", over.tun_fd)));
    set_or_insert(tun, "route-all", yaml_bool(false));
    set_or_insert(tun, "routes", Value::Sequence(Vec::new()));
    set_or_insert(tun, "gateway", yaml_string("10.0.0.1/30"));
    set_or_insert(tun, "gateway-v6", Value::Null);
    set_or_insert(tun, "mtu", Value::Null);
    set_or_insert(tun, "so-mark", Value::Null);
    set_or_insert(tun, "route-table", Value::Number(Number::from(0)));
    set_or_insert(tun, "dns-hijack", yaml_bool(true));

    let dns = ensure_mapping(root, "dns");
    set_or_insert(dns, "enable", yaml_bool(true));
    set_or_insert(dns, "ipv6", yaml_bool(over.ipv6));

    let listen = ensure_mapping(dns, "listen");
    set_or_insert(listen, "udp", yaml_string("127.0.0.1:53553"));

    insert_sequence_if_missing(
        dns,
        "nameserver",
        &[
            yaml_string("https://223.5.5.5:443"),
            yaml_string("https://223.6.6.6:443"),
            yaml_string("https://120.53.53.53:443"),
            yaml_string("https://1.12.12.12:443"),
        ],
    );
    insert_sequence_if_missing(
        dns,
        "default-nameserver",
        &[yaml_string("223.6.6.6"), yaml_string("8.8.8.8")],
    );

    if over.fake_ip {
        set_or_insert(dns, "enhanced-mode", yaml_string("fake-ip"));
        set_or_insert(
            dns,
            "fake-ip-range",
            yaml_string(over.fake_ip_range.clone()),
        );
    } else {
        set_or_insert(dns, "enhanced-mode", yaml_string("normal"));
    }

    let final_profile = FinalProfile {
        mixed_port: current_mixed_port(root, over.mixed_port),
    };
    let rendered = serde_yaml::to_string(&value)
        .map_err(|error| format!("failed to render runtime config: {error}"))?;
    Ok((rendered, final_profile))
}

fn extract_jstring(
    env: &mut JNIEnv<'_>,
    value: JString<'_>,
    field_name: &str,
) -> Result<String, String> {
    env.get_string(&value)
        .map(|it| it.to_string_lossy().into_owned())
        .map_err(|_| format!("failed to read JNI string: {field_name}"))
}

fn stop_core_internal() -> Result<(), String> {
    let running = {
        let mut guard = core_state()
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        guard.take()
    };

    if let Some(state) = running {
        state.worker.abort();
        let _ = fs::remove_file(state.metadata.socket_path);
        log_line(&state.metadata.log_path, "chimera core stop requested");
    }

    CORE_RUNNING.store(false, Ordering::SeqCst);
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

    let profile_content = fs::read_to_string(&profile_path)
        .map_err(|error| format!("failed to read profile file: {error}"))?;
    if profile_content.trim().is_empty() {
        return Err("profile file is empty".to_string());
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
    let (runtime_config, final_profile) =
        build_runtime_config(&profile_content, &socket_path, &over)?;

    let mut config = ClashConfig::Str(runtime_config)
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
    config.general.mmdb = Some("Country.mmdb".to_string());

    config.dns.enable = true;
    config.dns.listen.udp = Some(SocketAddr::new(
        IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)),
        53553,
    ));

    INIT.call_once(|| unsafe {
        std::env::set_var("RUST_BACKTRACE", "1");
        std::env::set_var("NO_COLOR", "1");
    });

    let profile_name = profile_path
        .file_name()
        .and_then(|it| it.to_str())
        .unwrap_or("profile.yaml")
        .to_string();
    let metadata = CoreMetadata {
        profile_name,
        profile_checksum: profile_checksum(&profile_content),
        tun_fd,
        work_dir: work_dir.clone(),
        log_path: log_path.clone(),
        socket_path: socket_path.clone(),
        started_at_epoch_secs: now_epoch_secs(),
    };

    let runtime_log_path = log_path.clone();
    let worker = runtime().spawn(async move {
        let (log_tx, _) = broadcast::channel(100);
        initialize_logging(
            config.general.log_level,
            &work_dir_string,
            Some(runtime_log_path.to_string_lossy().into_owned()),
            log_tx.clone(),
        );
        log_line(
            &runtime_log_path,
            &format!(
                "starting clash core: profile={} tun_fd={} work_dir={}",
                profile_path_string, tun_fd, work_dir_string
            ),
        );
        if let Err(error) = start(config, work_dir_string, log_tx).await {
            let message = format!("clash core exited with error: {error}");
            set_last_error(message.clone());
            log_line(&runtime_log_path, &message);
        } else {
            log_line(&runtime_log_path, "clash core exited");
        }
        CORE_RUNNING.store(false, Ordering::SeqCst);
    });

    {
        let mut guard = core_state()
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        *guard = Some(CoreState { worker, metadata });
    }
    CORE_RUNNING.store(true, Ordering::SeqCst);
    Ok(final_profile)
}

fn build_hello_message() -> String {
    if JVM.get().is_none() {
        return "ffi: jni not setup".to_string();
    }

    if CORE_RUNNING.load(Ordering::SeqCst) {
        let guard = core_state().lock();
        if let Ok(guard) = guard {
            if let Some(state) = guard.as_ref() {
                return format!(
                    "ffi: core running {} tun={} ({})",
                    state.metadata.profile_name,
                    state.metadata.tun_fd,
                    state.metadata.work_dir.display()
                );
            }
        }
        return "ffi: core running".to_string();
    }

    if let Some(last_error) = read_last_error() {
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
    ClashConfig::File(config_path)
        .try_parse()
        .map_err(|error| runtime_error(format!("failed to verify config: {error}")))?;

    let content = fs::read_to_string(&path)
        .map_err(|error| runtime_error(format!("failed to read config file: {error}")))?;
    let mut value: serde_yaml::Value = serde_yaml::from_str(&content)
        .map_err(|error| runtime_error(format!("failed to parse config yaml: {error}")))?;
    value
        .apply_merge()
        .map_err(|error| runtime_error(format!("failed to resolve yaml anchors: {error}")))?;

    serde_yaml::to_string(&value)
        .map_err(|error| runtime_error(format!("failed to serialize verified config: {error}")))
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

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeSetup(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jboolean {
    match env.get_java_vm() {
        Ok(vm) => {
            if !(JVM.set(vm).is_ok() || JVM.get().is_some()) {
                set_last_error("failed to persist JavaVM");
                return JNI_FALSE;
            }

            let chimera_ffi = match env.new_global_ref(&_this) {
                Ok(reference) => reference,
                Err(error) => {
                    set_last_error(format!("failed to create ChimeraFfi global ref: {error}"));
                    return JNI_FALSE;
                }
            };

            if !(CHIMERA_FFI.set(chimera_ffi).is_ok() || CHIMERA_FFI.get().is_some()) {
                set_last_error("failed to persist ChimeraFfi global ref");
                return JNI_FALSE;
            }

            install_socket_protector();
            let _ = runtime();
            clear_last_error();
            JNI_TRUE
        }
        Err(error) => {
            set_last_error(format!("failed to get JavaVM: {error}"));
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeHello(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env.new_string(build_hello_message()) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStart(
    mut env: JNIEnv<'_>,
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
        ipv6: true,
    };

    match start_core_internal(profile_path, cache_dir, tun_fd, over) {
        Ok(_) => JNI_TRUE,
        Err(error) => {
            set_last_error(error);
            CORE_RUNNING.store(false, Ordering::SeqCst);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStop(
    _env: JNIEnv<'_>,
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
