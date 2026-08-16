use crate::ChimeraError;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tracing::debug;
use urlencoding::encode;

#[cfg(unix)]
use http_body_util::{BodyExt, Full};
#[cfg(unix)]
use hyper::Request;
#[cfg(unix)]
use hyper::body::Bytes;
#[cfg(unix)]
use hyper_util::client::legacy::Client;
#[cfg(unix)]
use hyper_util::rt::TokioExecutor;
#[cfg(unix)]
use hyperlocal::{UnixConnector, Uri as UnixUri};

#[cfg(unix)]
type UnixClient = Client<UnixConnector, Full<Bytes>>;

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
pub struct MemoryResponse {
    pub inuse: i64,
    pub oslimit: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Connection {
    pub id: String,
    pub metadata: Metadata,
    pub upload: i64,
    pub download: i64,
    pub start: String,
    #[serde(default)]
    pub chains: Vec<String>,
    #[serde(default)]
    pub rule: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Metadata {
    pub network: String,
    #[serde(rename = "type")]
    pub metadata_type: String,
    #[serde(rename = "sourceIP")]
    pub source_ip: String,
    #[serde(rename = "destinationIP")]
    pub destination_ip: Option<String>,
    #[serde(rename = "sourcePort")]
    pub source_port: Option<u16>,
    #[serde(rename = "destinationPort")]
    pub destination_port: u16,
    #[serde(default)]
    pub host: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ConnectionsResponse {
    #[serde(rename = "downloadTotal")]
    pub download_total: i64,
    #[serde(rename = "uploadTotal")]
    pub upload_total: i64,
    #[serde(default)]
    pub memory: Option<i64>,
    pub connections: Vec<Connection>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ConfigResponse {
    #[serde(rename = "external-controller")]
    pub external_controller: Option<String>,
    pub secret: Option<String>,
    pub mode: Option<Mode>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct RuleSnapshot {
    #[serde(rename = "type")]
    pub rule_type: String,
    pub proxy: String,
    pub payload: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RulesResponse {
    #[serde(default)]
    rules: Vec<RuleSnapshot>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ProxyProviderSnapshot {
    pub name: String,
    pub provider_type: String,
    pub vehicle_type: String,
    pub proxy_count: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct ProxiesResponse {
    pub proxies: HashMap<String, Proxy>,
}

#[derive(uniffi::Object)]
pub struct ClashController {
    socket_path: String,
    #[cfg(unix)]
    client: UnixClient,
}

#[uniffi::export(async_runtime = "tokio")]
impl ClashController {
    #[uniffi::constructor]
    pub fn new(socket_path: String) -> Arc<Self> {
        Arc::new(Self::for_socket_path(socket_path))
    }

    pub async fn get_proxies(&self) -> Result<Vec<Proxy>, ChimeraError> {
        debug!("controller get_proxies");
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
        debug!(
            "controller select_proxy group={} proxy={}",
            group_name, proxy_name
        );
        let body = serde_json::json!({ "name": proxy_name });
        let path = format!("/proxies/{}", encode(&group_name));
        self.request_no_response(
            "PUT",
            &path,
            Some(
                serde_json::to_vec(&body).map_err(|error| ChimeraError::Runtime {
                    details: format!("failed to serialize proxy selection: {error}"),
                })?,
            ),
        )
        .await
    }

    pub async fn get_proxy_delay(
        &self,
        name: String,
        url: Option<String>,
        timeout: Option<i32>,
    ) -> Result<DelayResponse, ChimeraError> {
        debug!("controller get_proxy_delay proxy={}", name);
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

    pub async fn get_memory(&self) -> Result<MemoryResponse, ChimeraError> {
        self.request("GET", "/memory", None).await
    }

    pub async fn get_connections(&self) -> Result<ConnectionsResponse, ChimeraError> {
        self.request("GET", "/connections", None).await
    }

    pub async fn close_connection(&self, id: String) -> Result<(), ChimeraError> {
        debug!("controller close_connection id={}", id);
        let path = format!("/connections/{}", encode(&id));
        self.request_no_response("DELETE", &path, None).await
    }

    pub async fn close_all_connections(&self) -> Result<(), ChimeraError> {
        debug!("controller close_all_connections");
        self.request_no_response("DELETE", "/connections", None)
            .await
    }

    pub async fn get_rules(&self) -> Result<Vec<RuleSnapshot>, ChimeraError> {
        debug!("controller get_rules");
        let response: RulesResponse = self.request("GET", "/rules", None).await?;
        Ok(response.rules)
    }

    pub async fn get_proxy_providers(&self) -> Result<Vec<ProxyProviderSnapshot>, ChimeraError> {
        debug!("controller get_proxy_providers");
        let response: serde_json::Value = self.request("GET", "/providers/proxies", None).await?;
        let providers = response
            .get("providers")
            .and_then(serde_json::Value::as_object)
            .ok_or_else(|| ChimeraError::Runtime {
                details: "provider response is missing the providers object".to_string(),
            })?;
        let mut snapshots = providers
            .iter()
            .map(|(fallback_name, provider)| ProxyProviderSnapshot {
                name: provider
                    .get("name")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or(fallback_name)
                    .to_string(),
                provider_type: provider
                    .get("type")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default()
                    .to_string(),
                vehicle_type: provider
                    .get("vehicleType")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default()
                    .to_string(),
                proxy_count: provider
                    .get("proxies")
                    .and_then(serde_json::Value::as_array)
                    .map_or(0, |proxies| proxies.len() as i32),
            })
            .collect::<Vec<_>>();
        snapshots.sort_by(|left, right| left.name.cmp(&right.name));
        Ok(snapshots)
    }

    pub async fn update_proxy_provider(&self, name: String) -> Result<(), ChimeraError> {
        debug!("controller update_proxy_provider name={}", name);
        let path = format!("/providers/proxies/{}", encode(&name));
        self.request_no_response("PUT", &path, None).await
    }

    pub async fn healthcheck_proxy_provider(&self, name: String) -> Result<(), ChimeraError> {
        debug!("controller healthcheck_proxy_provider name={}", name);
        let path = format!("/providers/proxies/{}/healthcheck", encode(&name));
        self.request_no_response("GET", &path, None).await
    }

    pub async fn query_dns(
        &self,
        name: String,
        record_type: String,
    ) -> Result<String, ChimeraError> {
        let name = name.trim();
        if name.is_empty() {
            return Err(ChimeraError::Runtime {
                details: "DNS query name must not be empty".to_string(),
            });
        }
        let record_type = record_type.trim().to_ascii_uppercase();
        if !matches!(
            record_type.as_str(),
            "A" | "AAAA" | "CAA" | "CNAME" | "MX" | "NS" | "PTR" | "SOA" | "SRV" | "TXT"
        ) {
            return Err(ChimeraError::Runtime {
                details: format!("unsupported DNS record type: {record_type}"),
            });
        }

        debug!("controller query_dns name={} type={}", name, record_type);
        let path = format!(
            "/dns/query?name={}&type={}",
            encode(name),
            encode(&record_type),
        );
        let response: serde_json::Value = self.request("GET", &path, None).await?;
        serde_json::to_string_pretty(&response).map_err(|error| ChimeraError::Runtime {
            details: format!("failed to serialize DNS response: {error}"),
        })
    }

    pub async fn get_configs(&self) -> Result<ConfigResponse, ChimeraError> {
        self.request("GET", "/configs", None).await
    }

    pub async fn update_config(&self, config: HashMap<String, String>) -> Result<(), ChimeraError> {
        self.request_no_response(
            "PATCH",
            "/configs",
            Some(
                serde_json::to_vec(&config).map_err(|error| ChimeraError::Runtime {
                    details: format!("failed to serialize config update: {error}"),
                })?,
            ),
        )
        .await
    }

    pub async fn set_mode(&self, mode: Mode) -> Result<(), ChimeraError> {
        debug!("controller set_mode {:?}", mode);
        let mode_str = match mode {
            Mode::Rule => "rule",
            Mode::Global => "global",
            Mode::Direct => "direct",
        };
        let mut config = HashMap::new();
        config.insert("mode".to_string(), mode_str.to_string());
        self.update_config(config).await
    }

    pub async fn reset_network(&self) -> Result<(), ChimeraError> {
        debug!("controller reset_network");
        self.request_no_response("POST", "/network/reset", None)
            .await
    }

    pub async fn get_mode(&self) -> Result<Option<Mode>, ChimeraError> {
        let config = self.get_configs().await?;
        Ok(config.mode)
    }
}

impl ClashController {
    fn for_socket_path(socket_path: String) -> Self {
        Self {
            socket_path,
            #[cfg(unix)]
            client: Client::builder(TokioExecutor::new()).build(UnixConnector),
        }
    }

    async fn do_request(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<hyper::body::Bytes, ChimeraError> {
        #[cfg(unix)]
        {
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

            let response = self
                .client
                .request(request)
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("controller request failed: {error}"),
                })
                .inspect_err(|error| tracing::error!("{error}"))?;

            if !response.status().is_success() {
                tracing::error!("controller http status error: {}", response.status());
                return Err(ChimeraError::Runtime {
                    details: format!("controller http status error: {}", response.status()),
                });
            }

            response
                .into_body()
                .collect()
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("failed to read controller response: {error}"),
                })
                .map(|body| body.to_bytes())
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

    async fn request_no_response(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<(), ChimeraError> {
        self.do_request(method, path, body).await.map(|_| ())
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
        let body_bytes = self.do_request(method, path, body).await?;
        serde_json::from_slice(&body_bytes).map_err(|error| ChimeraError::Runtime {
            details: format!("failed to decode controller response: {error}"),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::ClashController;
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::time::Duration;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    static SOCKET_SEQUENCE: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn rules_response_deserializes_structured_snapshots() {
        let response: super::RulesResponse = serde_json::from_str(
            r#"{"rules":[{"type":"DOMAIN-SUFFIX","proxy":"Proxy","payload":"example.com"},{"type":"MATCH","proxy":"DIRECT","payload":""}]}"#,
        )
        .unwrap();

        assert_eq!(response.rules.len(), 2);
        assert_eq!(response.rules[0].rule_type, "DOMAIN-SUFFIX");
        assert_eq!(response.rules[0].proxy, "Proxy");
        assert_eq!(response.rules[0].payload, "example.com");
        assert_eq!(response.rules[1].rule_type, "MATCH");
    }

    #[test]
    fn proxy_provider_snapshot_deserializes_expected_shape() {
        let response: serde_json::Value = serde_json::from_str(
            r#"{"providers":{"remote":{"name":"remote","type":"Proxy","vehicleType":"HTTP","proxies":[{},{}]}}}"#,
        )
        .unwrap();
        let provider = &response["providers"]["remote"];

        assert_eq!(provider["name"], "remote");
        assert_eq!(provider["type"], "Proxy");
        assert_eq!(provider["vehicleType"], "HTTP");
        assert_eq!(provider["proxies"].as_array().map(Vec::len), Some(2));
    }

    #[tokio::test]
    async fn controller_reuses_unix_http_connection() {
        let sequence = SOCKET_SEQUENCE.fetch_add(1, Ordering::Relaxed);
        let socket_path = std::env::temp_dir().join(format!(
            "chimera-controller-keep-alive-test-{}-{sequence}.sock",
            std::process::id(),
        ));
        let _ = std::fs::remove_file(&socket_path);
        let listener = tokio::net::UnixListener::bind(&socket_path).unwrap();
        let server_socket_path = socket_path.clone();
        let server = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request_lines = Vec::new();
            for _ in 0..2 {
                let mut request = Vec::new();
                let mut buffer = [0_u8; 512];
                while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                    let count = stream.read(&mut buffer).await.unwrap();
                    assert!(count > 0, "client closed the keep-alive connection early");
                    request.extend_from_slice(&buffer[..count]);
                }
                request_lines.push(
                    String::from_utf8_lossy(&request)
                        .lines()
                        .next()
                        .unwrap_or_default()
                        .to_string(),
                );
                let response_body = r#"{"inuse":1024,"oslimit":2048}"#;
                let response = format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: keep-alive\r\n\r\n{}",
                    response_body.len(),
                    response_body,
                );
                stream.write_all(response.as_bytes()).await.unwrap();
            }
            let _ = std::fs::remove_file(server_socket_path);
            request_lines
        });
        let controller = ClashController::for_socket_path(socket_path.to_string_lossy().to_string());

        let first = tokio::time::timeout(Duration::from_secs(2), controller.get_memory())
            .await
            .expect("first controller request timed out")
            .unwrap();
        let second = tokio::time::timeout(Duration::from_secs(2), controller.get_memory())
            .await
            .expect("second controller request did not reuse the accepted connection")
            .unwrap();
        let requests = tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .expect("keep-alive server did not finish")
            .unwrap();

        assert_eq!(first.inuse, 1024);
        assert_eq!(second.oslimit, 2048);
        assert_eq!(
            requests,
            vec![
                "GET /memory HTTP/1.1".to_string(),
                "GET /memory HTTP/1.1".to_string(),
            ],
        );
    }

    #[tokio::test]
    async fn query_dns_rejects_empty_name_before_request() {
        let controller = ClashController::for_socket_path(String::new());

        let error = controller
            .query_dns("   ".to_string(), "A".to_string())
            .await
            .unwrap_err();

        assert_eq!(error.to_string(), "DNS query name must not be empty");
    }

    #[tokio::test]
    async fn query_dns_rejects_unknown_record_type_before_request() {
        let controller = ClashController::for_socket_path(String::new());

        let error = controller
            .query_dns("example.com".to_string(), "invalid".to_string())
            .await
            .unwrap_err();

        assert_eq!(error.to_string(), "unsupported DNS record type: INVALID");
    }
}
