use crate::ChimeraError;
use http_body_util::{BodyExt, Full};
use hyper::Request;
use hyper::body::Bytes;
use hyper_util::client::legacy::Client;
use hyper_util::rt::TokioExecutor;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use urlencoding::encode;

#[cfg(unix)]
use hyperlocal::{UnixConnector, Uri as UnixUri};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "lowercase")]
pub enum Mode {
    Rule,
    Global,
    Direct,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Proxy {
    pub name: String,
    #[serde(rename = "type")]
    pub proxy_type: String,
    #[serde(default)]
    pub all: Vec<String>,
    pub now: Option<String>,
    #[serde(default)]
    pub history: Vec<DelayHistory>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct DelayHistory {
    pub time: String,
    pub delay: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct DelayResponse {
    pub delay: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ConfigResponse {
    #[serde(rename = "external-controller")]
    pub external_controller: Option<String>,
    pub secret: Option<String>,
    pub mode: Option<Mode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct ProxiesResponse {
    pub proxies: HashMap<String, Proxy>,
}

#[derive(uniffi::Object)]
pub struct ClashController {
    socket_path: String,
}

#[uniffi::export(async_runtime = "tokio")]
impl ClashController {
    #[uniffi::constructor]
    pub fn new(socket_path: String) -> Arc<Self> {
        Arc::new(Self { socket_path })
    }

    pub async fn get_proxies(&self) -> Result<Vec<Proxy>, ChimeraError> {
        let mode = self.get_mode().await?.unwrap_or(Mode::Rule);

        if matches!(mode, Mode::Direct) {
            return Ok(vec![Proxy {
                name: "DIRECT".to_string(),
                proxy_type: "Direct".to_string(),
                all: Vec::new(),
                now: None,
                history: Vec::new(),
            }]);
        }

        let mut response: ProxiesResponse = self.request("GET", "/proxies", None).await?;

        if let Some(global_group) = response.proxies.remove("GLOBAL") {
            let mut sorted_proxies = Vec::new();

            for name in &global_group.all {
                if let Some(proxy) = response.proxies.get(name) {
                    sorted_proxies.push(proxy.clone());
                }
            }

            for (name, proxy) in &response.proxies {
                if !global_group.all.contains(name) {
                    sorted_proxies.push(proxy.clone());
                }
            }

            if matches!(mode, Mode::Global) {
                sorted_proxies.insert(0, global_group);
            }

            Ok(sorted_proxies)
        } else {
            Ok(response.proxies.values().cloned().collect())
        }
    }

    pub async fn select_proxy(
        &self,
        group_name: String,
        proxy_name: String,
    ) -> Result<(), ChimeraError> {
        let body = serde_json::json!({ "name": proxy_name });
        let path = format!("/proxies/{}", encode(&group_name));
        self.request_no_response("PUT", &path, Some(serde_json::to_vec(&body).map_err(|error| {
            ChimeraError::Runtime {
                details: format!("failed to serialize proxy selection: {error}"),
            }
        })?))
        .await
    }

    pub async fn get_proxy_delay(
        &self,
        name: String,
        url: Option<String>,
        timeout: Option<i32>,
    ) -> Result<DelayResponse, ChimeraError> {
        let test_url = url.unwrap_or_else(|| "http://www.gstatic.com/generate_204".to_string());
        let timeout_ms = timeout.unwrap_or(5000);
        let path = format!(
            "/proxies/{}/delay?url={}&timeout={}",
            encode(&name),
            encode(&test_url),
            timeout_ms
        );
        self.request("GET", &path, None).await
    }

    pub async fn get_configs(&self) -> Result<ConfigResponse, ChimeraError> {
        self.request("GET", "/configs", None).await
    }

    pub async fn set_mode(&self, mode: Mode) -> Result<(), ChimeraError> {
        let mode_str = match mode {
            Mode::Rule => "rule",
            Mode::Global => "global",
            Mode::Direct => "direct",
        };
        let body = serde_json::json!({ "mode": mode_str });
        self.request_no_response("PATCH", "/configs", Some(serde_json::to_vec(&body).map_err(|error| {
            ChimeraError::Runtime {
                details: format!("failed to serialize mode update: {error}"),
            }
        })?))
        .await
    }

    pub async fn get_mode(&self) -> Result<Option<Mode>, ChimeraError> {
        let config = self.get_configs().await?;
        Ok(config.mode)
    }
}

impl ClashController {
    async fn request_no_response(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<(), ChimeraError> {
        #[cfg(unix)]
        {
            let client = Client::builder(TokioExecutor::new()).build(UnixConnector);
            let uri: hyper::Uri = UnixUri::new(&self.socket_path, path).into();

            let request_builder = Request::builder()
                .uri(uri)
                .method(method)
                .header("Content-Type", "application/json");

            let request = if let Some(body_data) = body {
                request_builder
                    .body(Full::new(Bytes::from(body_data)))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request with body: {error}"),
                    })?
            } else {
                request_builder
                    .body(Full::new(Bytes::new()))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request: {error}"),
                    })?
            };

            let response = client
                .request(request)
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("controller request failed: {error}"),
                })?;

            if !response.status().is_success() {
                return Err(ChimeraError::Runtime {
                    details: format!("controller http status error: {}", response.status()),
                });
            }

            Ok(())
        }
        #[cfg(not(unix))]
        {
            let _ = (method, path, body);
            Err(ChimeraError::Runtime {
                details: "unix domain socket controller is unavailable on this platform"
                    .to_string(),
            })
        }
    }

    async fn request<T>(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<T, ChimeraError>
    where
        T: serde::de::DeserializeOwned,
    {
        #[cfg(unix)]
        {
            let uri: hyper::Uri = UnixUri::new(&self.socket_path, path).into();
            let client = Client::builder(TokioExecutor::new()).build(UnixConnector);

            let request_builder = Request::builder()
                .uri(uri)
                .method(method)
                .header("Content-Type", "application/json");

            let request = if let Some(body_data) = body {
                request_builder
                    .body(Full::new(Bytes::from(body_data)))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request with body: {error}"),
                    })?
            } else {
                request_builder
                    .body(Full::new(Bytes::new()))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request: {error}"),
                    })?
            };

            let response = client
                .request(request)
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("controller request failed: {error}"),
                })?;

            if !response.status().is_success() {
                return Err(ChimeraError::Runtime {
                    details: format!("controller http status error: {}", response.status()),
                });
            }

            let body_bytes = response
                .into_body()
                .collect()
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("failed to read controller response: {error}"),
                })?
                .to_bytes();

            serde_json::from_slice(&body_bytes).map_err(|error| ChimeraError::Runtime {
                details: format!("failed to decode controller response: {error}"),
            })
        }
        #[cfg(not(unix))]
        {
            let _ = (method, path, body);
            Err(ChimeraError::Runtime {
                details: "unix domain socket controller is unavailable on this platform"
                    .to_string(),
            })
        }
    }
}
