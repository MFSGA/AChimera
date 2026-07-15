use std::path::Path;

use reqwest::redirect::Policy;
use tokio_stream::StreamExt;

use crate::ChimeraError;

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

#[uniffi::export(async_runtime = "tokio")]
pub async fn download_file(
    url: String,
    output_path: String,
    user_agent: Option<String>,
    proxy_url: Option<String>,
) -> Result<DownloadResult, ChimeraError> {
    download_file_with_progress(url, output_path, user_agent, proxy_url, None).await
}

#[uniffi::export(async_runtime = "tokio")]
pub async fn download_file_with_progress(
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
            .map_err(|error| request_error("invalid proxy url", error))?;
        client_builder = client_builder.proxy(proxy);
    }

    let client = client_builder
        .build()
        .map_err(|error| request_error("failed to build http client", error))?;

    let response = client
        .get(&url)
        .send()
        .await
        .map_err(|error| request_error("failed to send request", error))?;

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
        let chunk = chunk.map_err(|error| request_error("failed to read response chunk", error))?;
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
            .map_err(|error| request_error("failed to create output dir", error))?;
    }

    tokio::fs::write(&output_path, &buffer)
        .await
        .map_err(|error| request_error("failed to write downloaded file", error))?;

    Ok(DownloadResult {
        success: true,
        file_size: buffer.len() as u64,
        error_message: None,
    })
}

fn request_error(prefix: &str, error: impl std::fmt::Display) -> ChimeraError {
    ChimeraError::Runtime {
        details: format!("{prefix}: {error}"),
    }
}
