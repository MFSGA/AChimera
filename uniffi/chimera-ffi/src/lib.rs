use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::collections::hash_map::DefaultHasher;
use std::fs::{self, File, OpenOptions};
use std::hash::{Hash, Hasher};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::sync::{Mutex, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

static CORE_RUNNING: AtomicBool = AtomicBool::new(false);
static CORE_STATE: OnceLock<Mutex<Option<CoreState>>> = OnceLock::new();
static LAST_ERROR: OnceLock<Mutex<Option<String>>> = OnceLock::new();

struct CoreState {
    worker: JoinHandle<()>,
    shutdown_tx: Sender<()>,
    metadata: CoreMetadata,
}

#[derive(Clone)]
struct CoreMetadata {
    profile_name: String,
    profile_checksum: u64,
    work_dir: PathBuf,
    log_path: PathBuf,
    socket_path: PathBuf,
    started_at_epoch_secs: u64,
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

fn log_line(log_path: &Path, message: &str) {
    let file = OpenOptions::new().append(true).create(true).open(log_path);
    let Ok(mut file) = file else {
        return;
    };
    let _ = writeln!(file, "[{}] {}", now_epoch_secs(), message);
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
        let _ = state.shutdown_tx.send(());
        let _ = state.worker.join();
        let _ = fs::remove_file(state.metadata.socket_path);
        log_line(&state.metadata.log_path, "chimera core stopped");
    }

    CORE_RUNNING.store(false, Ordering::SeqCst);
    clear_last_error();
    Ok(())
}

fn start_core_internal(profile_path: String, cache_dir: String) -> Result<(), String> {
    if profile_path.trim().is_empty() {
        return Err("profile path is empty".to_string());
    }
    if cache_dir.trim().is_empty() {
        return Err("cache dir is empty".to_string());
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

    let work_dir = PathBuf::from(cache_dir).join("chimera-core");
    fs::create_dir_all(&work_dir)
        .map_err(|error| format!("failed to create work dir {}: {error}", work_dir.display()))?;

    let mirrored_profile_path = work_dir.join("active-profile.yaml");
    fs::write(&mirrored_profile_path, profile_content.as_bytes()).map_err(|error| {
        format!(
            "failed to mirror profile into {}: {error}",
            mirrored_profile_path.display()
        )
    })?;

    let socket_path = work_dir.join("chimera.sock");
    let _ = fs::remove_file(&socket_path);
    File::create(&socket_path).map_err(|error| {
        format!(
            "failed to initialize socket placeholder {}: {error}",
            socket_path.display()
        )
    })?;

    let log_path = work_dir.join("chimera-core.log");
    let profile_name = profile_path
        .file_name()
        .and_then(|it| it.to_str())
        .unwrap_or("profile.yaml")
        .to_string();
    let metadata = CoreMetadata {
        profile_name,
        profile_checksum: profile_checksum(&profile_content),
        work_dir: work_dir.clone(),
        log_path: log_path.clone(),
        socket_path: socket_path.clone(),
        started_at_epoch_secs: now_epoch_secs(),
    };

    stop_core_internal()?;

    let worker_metadata = metadata.clone();
    let (shutdown_tx, shutdown_rx) = mpsc::channel::<()>();
    let worker = thread::Builder::new()
        .name("chimera-core".to_string())
        .spawn(move || {
            log_line(
                &worker_metadata.log_path,
                &format!(
                    "starting core with profile={}, checksum={}, work_dir={}",
                    worker_metadata.profile_name,
                    worker_metadata.profile_checksum,
                    worker_metadata.work_dir.display()
                ),
            );
            loop {
                match shutdown_rx.recv_timeout(Duration::from_secs(15)) {
                    Ok(_) | Err(RecvTimeoutError::Disconnected) => break,
                    Err(RecvTimeoutError::Timeout) => {
                        log_line(
                            &worker_metadata.log_path,
                            &format!(
                                "core heartbeat profile={} started_at={}",
                                worker_metadata.profile_name, worker_metadata.started_at_epoch_secs
                            ),
                        );
                    }
                }
            }
            log_line(&worker_metadata.log_path, "shutdown signal received");
        })
        .map_err(|error| format!("failed to spawn core worker thread: {error}"))?;

    {
        let mut guard = core_state()
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        *guard = Some(CoreState {
            worker,
            shutdown_tx,
            metadata,
        });
    }
    CORE_RUNNING.store(true, Ordering::SeqCst);
    clear_last_error();
    Ok(())
}

fn build_hello_message() -> String {
    if CORE_RUNNING.load(Ordering::SeqCst) {
        let guard = core_state().lock();
        if let Ok(guard) = guard {
            if let Some(state) = guard.as_ref() {
                return format!(
                    "ffi: core running {} ({})",
                    state.metadata.profile_name,
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

    match start_core_internal(profile_path, cache_dir) {
        Ok(()) => JNI_TRUE,
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
