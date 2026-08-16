use crate::ChimeraError;
use crate::controller_models::*;
use std::collections::HashMap;
use std::sync::Arc;
use tracing::debug;
use urlencoding::encode;

#[derive(uniffi::Object)]
pub struct ClashController {
    pub(crate) socket_path: String,
    #[cfg(unix)]
    pub(crate) client: crate::controller_transport::UnixClient,
}

#[uniffi::export(async_runtime = "tokio")]
impl ClashController {
    #[uniffi::constructor]
    pub fn new(socket_path: String) -> Arc<Self> {
        Arc::new(Self::for_socket_path(socket_path))
    }

    pub async fn get_proxies(&self) -> Result<Vec<Proxy>, ChimeraError> {
        debug!("controller get_proxies");
        let response: ProxiesResponse = self.request("GET", "/proxies", None).await?;
        let mut proxies = response.proxies.into_values().collect::<Vec<_>>();
        proxies.sort_by(|left, right| left.name.cmp(&right.name));
        Ok(proxies)
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

    pub async fn get_connection_summary(&self) -> Result<ConnectionSummary, ChimeraError> {
        let response: ConnectionSummaryResponse =
            self.request("GET", "/connections/summary", None).await?;
        Ok(ConnectionSummary {
            download_total: response.download_total,
            upload_total: response.upload_total,
            connection_count: response.connection_count,
        })
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
        let controller =
            ClashController::for_socket_path(socket_path.to_string_lossy().to_string());

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
    async fn get_proxies_uses_single_collection_request_and_sorts_results() {
        let sequence = SOCKET_SEQUENCE.fetch_add(1, Ordering::Relaxed);
        let socket_path = std::env::temp_dir().join(format!(
            "chimera-controller-proxies-test-{}-{sequence}.sock",
            std::process::id(),
        ));
        let _ = std::fs::remove_file(&socket_path);
        let listener = tokio::net::UnixListener::bind(&socket_path).unwrap();
        let server_socket_path = socket_path.clone();
        let server = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request = Vec::new();
            let mut buffer = [0_u8; 512];
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let count = stream.read(&mut buffer).await.unwrap();
                assert!(count > 0, "client closed before sending the proxy request");
                request.extend_from_slice(&buffer[..count]);
            }
            let request_line = String::from_utf8_lossy(&request)
                .lines()
                .next()
                .unwrap_or_default()
                .to_string();
            let response_body = r#"{"proxies":{"zeta":{"name":"zeta","type":"Selector","all":[],"now":null,"history":[]},"alpha":{"name":"alpha","type":"Direct","all":[],"now":null,"history":[]}}}"#;
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                response_body.len(),
                response_body,
            );
            stream.write_all(response.as_bytes()).await.unwrap();
            let _ = std::fs::remove_file(server_socket_path);
            request_line
        });
        let controller =
            ClashController::for_socket_path(socket_path.to_string_lossy().to_string());

        let proxies = tokio::time::timeout(Duration::from_secs(2), controller.get_proxies())
            .await
            .expect("proxy request timed out")
            .unwrap();
        let request = tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .expect("proxy server did not finish")
            .unwrap();

        assert_eq!(request, "GET /proxies HTTP/1.1");
        assert_eq!(
            proxies
                .iter()
                .map(|proxy| proxy.name.as_str())
                .collect::<Vec<_>>(),
            vec!["alpha", "zeta"],
        );
    }

    #[tokio::test]
    async fn get_connection_summary_uses_lightweight_endpoint() {
        let sequence = SOCKET_SEQUENCE.fetch_add(1, Ordering::Relaxed);
        let socket_path = std::env::temp_dir().join(format!(
            "chimera-controller-summary-test-{}-{sequence}.sock",
            std::process::id(),
        ));
        let _ = std::fs::remove_file(&socket_path);
        let listener = tokio::net::UnixListener::bind(&socket_path).unwrap();
        let server_socket_path = socket_path.clone();
        let server = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request = Vec::new();
            let mut buffer = [0_u8; 512];
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let count = stream.read(&mut buffer).await.unwrap();
                assert!(
                    count > 0,
                    "client closed before sending the summary request"
                );
                request.extend_from_slice(&buffer[..count]);
            }
            let request_line = String::from_utf8_lossy(&request)
                .lines()
                .next()
                .unwrap_or_default()
                .to_string();
            let response_body = r#"{"downloadTotal":2048,"uploadTotal":1024,"connectionCount":2}"#;
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                response_body.len(),
                response_body,
            );
            stream.write_all(response.as_bytes()).await.unwrap();
            let _ = std::fs::remove_file(server_socket_path);
            request_line
        });
        let controller =
            ClashController::for_socket_path(socket_path.to_string_lossy().to_string());

        let summary =
            tokio::time::timeout(Duration::from_secs(2), controller.get_connection_summary())
                .await
                .expect("connection summary request timed out")
                .unwrap();
        let request = tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .expect("connection summary server did not finish")
            .unwrap();

        assert_eq!(request, "GET /connections/summary HTTP/1.1");
        assert_eq!(summary.download_total, 2048);
        assert_eq!(summary.upload_total, 1024);
        assert_eq!(summary.connection_count, 2);
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
