use jni::JavaVM;
use jni::objects::{Global, JObject};
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, OnceLock};
use tokio::runtime::Runtime;
use tokio::task::JoinHandle;
use tracing::error;

pub(crate) static INSTANCE: OnceLock<ClashInstance> = OnceLock::new();

pub(crate) struct ClashInstance {
    pub(crate) jvm: JavaVM,
    pub(crate) chimera_ffi: Global<JObject<'static>>,
    pub(crate) rt: OnceLock<Runtime>,
    pub(crate) core_state: Mutex<Option<CoreState>>,
    pub(crate) last_error: Mutex<Option<String>>,
    pub(crate) core_running: AtomicBool,
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

pub(crate) fn instance() -> &'static ClashInstance {
    INSTANCE.get().expect("ClashInstance not initialized")
}

pub(crate) fn runtime() -> &'static Runtime {
    instance().runtime()
}

pub(crate) fn set_last_error(message: impl Into<String>) {
    let message = message.into();
    error!("{message}");
    if let Some(inst) = INSTANCE.get()
        && let Ok(mut guard) = inst.last_error.lock()
    {
        *guard = Some(message);
    }
}

pub(crate) fn clear_last_error() {
    if let Some(inst) = INSTANCE.get()
        && let Ok(mut guard) = inst.last_error.lock()
    {
        *guard = None;
    }
}

pub(crate) struct CoreState {
    pub(crate) worker: JoinHandle<Result<(), String>>,
    pub(crate) notify_exit: Arc<AtomicBool>,
    pub(crate) metadata: CoreMetadata,
}

#[derive(Clone)]
pub(crate) struct CoreMetadata {
    pub(crate) profile_name: String,
    pub(crate) tun_fd: i32,
    pub(crate) work_dir: PathBuf,
    pub(crate) log_path: PathBuf,
    pub(crate) socket_path: PathBuf,
}
