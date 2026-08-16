use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
#[cfg(unix)]
use tokio::net::UnixStream;
use tokio::task::JoinHandle;
use tokio::time::{Instant, sleep};

pub(crate) const CORE_START_TIMEOUT: Duration = Duration::from_secs(10);
pub(crate) const CORE_STOP_TIMEOUT: Duration = Duration::from_secs(5);
const CORE_READY_POLL_INTERVAL: Duration = Duration::from_millis(50);

pub(crate) fn should_notify_core_stopped(notify_exit: &AtomicBool) -> bool {
    notify_exit.load(Ordering::SeqCst)
}

#[cfg(unix)]
async fn controller_is_ready(socket_path: &Path) -> std::io::Result<()> {
    UnixStream::connect(socket_path).await.map(|_| ())
}

#[cfg(not(unix))]
async fn controller_is_ready(socket_path: &Path) -> std::io::Result<()> {
    if socket_path.exists() {
        Ok(())
    } else {
        Err(std::io::Error::new(
            std::io::ErrorKind::NotFound,
            "controller socket is not available",
        ))
    }
}

fn describe_worker_exit(result: Result<Result<(), String>, tokio::task::JoinError>) -> String {
    match result {
        Ok(Ok(())) => "clash core exited before becoming ready".to_string(),
        Ok(Err(error)) => error,
        Err(error) if error.is_cancelled() => {
            "clash core startup task was cancelled before becoming ready".to_string()
        }
        Err(error) => format!("clash core startup task failed: {error}"),
    }
}

pub(crate) async fn wait_for_core_ready(
    socket_path: &Path,
    worker: &mut JoinHandle<Result<(), String>>,
    ready: &AtomicBool,
    timeout: Duration,
) -> Result<(), String> {
    let deadline = Instant::now() + timeout;

    loop {
        let connect_error = match controller_is_ready(socket_path).await {
            Ok(()) => {
                ready.store(true, Ordering::SeqCst);
                return Ok(());
            }
            Err(error) => error,
        };

        let now = Instant::now();
        if now >= deadline {
            return Err(format!(
                "timed out waiting for clash controller {}: {connect_error}",
                socket_path.display(),
            ));
        }

        let delay = CORE_READY_POLL_INTERVAL.min(deadline.saturating_duration_since(now));
        tokio::select! {
            result = &mut *worker => return Err(describe_worker_exit(result)),
            _ = sleep(delay) => {}
        }
    }
}

pub(crate) async fn wait_for_worker_shutdown(
    mut worker: JoinHandle<Result<(), String>>,
    timeout: Duration,
) -> Result<(), String> {
    match tokio::time::timeout(timeout, &mut worker).await {
        Ok(Ok(Ok(()))) | Ok(Ok(Err(_))) => Ok(()),
        Ok(Err(error)) => Err(format!("clash core worker join failed: {error}")),
        Err(_) => {
            worker.abort();
            let _ = worker.await;
            Err(format!(
                "timed out waiting for clash core shutdown after {} ms",
                timeout.as_millis(),
            ))
        }
    }
}
