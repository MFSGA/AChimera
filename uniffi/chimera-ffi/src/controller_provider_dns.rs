use crate::ChimeraError;
use crate::controller::ClashController;
use crate::controller_models::ProxyProviderSnapshot;
use tracing::debug;
use urlencoding::encode;

#[uniffi::export(async_runtime = "tokio")]
impl ClashController {
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
}
