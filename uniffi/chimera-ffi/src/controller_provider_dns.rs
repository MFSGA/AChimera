use crate::ChimeraError;
use crate::controller::ClashController;
use crate::controller_models::{ProviderSnapshot, ProxyProviderSnapshot};
use tracing::debug;
use urlencoding::encode;

#[uniffi::export(async_runtime = "tokio")]
impl ClashController {
    pub async fn get_providers(&self) -> Result<Vec<ProviderSnapshot>, ChimeraError> {
        debug!("controller get_providers");
        let proxy_response: serde_json::Value =
            self.request("GET", "/providers/proxies", None).await?;
        let rule_response: serde_json::Value =
            self.request("GET", "/providers/rules", None).await?;
        let mut snapshots = parse_providers(proxy_response, "Proxy")?;
        snapshots.extend(parse_providers(rule_response, "Rule")?);
        snapshots.sort_by(|left, right| {
            provider_type_order(&left.provider_type)
                .cmp(&provider_type_order(&right.provider_type))
                .then_with(|| left.name.cmp(&right.name))
        });
        Ok(snapshots)
    }

    pub async fn update_provider(
        &self,
        provider_type: String,
        name: String,
    ) -> Result<(), ChimeraError> {
        debug!("controller update_provider type={}", provider_type);
        let path = provider_update_path(&provider_type, &name)?;
        self.request_no_response("PUT", &path, None).await
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
}

fn parse_providers(
    response: serde_json::Value,
    fallback_type: &str,
) -> Result<Vec<ProviderSnapshot>, ChimeraError> {
    let providers = response
        .get("providers")
        .and_then(serde_json::Value::as_object)
        .ok_or_else(|| ChimeraError::Runtime {
            details: "provider response is missing the providers object".to_string(),
        })?;
    Ok(providers
        .iter()
        // CMFA's QueryProviders omits built-in Compatible groups from external resources.
        .filter(|(_, provider)| {
            provider
                .get("vehicleType")
                .and_then(serde_json::Value::as_str)
                != Some("Compatible")
        })
        .map(|(fallback_name, provider)| ProviderSnapshot {
            name: provider
                .get("name")
                .and_then(serde_json::Value::as_str)
                .unwrap_or(fallback_name)
                .to_string(),
            provider_type: provider
                .get("type")
                .and_then(serde_json::Value::as_str)
                .unwrap_or(fallback_type)
                .to_string(),
            vehicle_type: provider
                .get("vehicleType")
                .and_then(serde_json::Value::as_str)
                .unwrap_or_default()
                .to_string(),
            updated_at: parse_updated_at(provider.get("updatedAt")),
        })
        .collect())
}

fn parse_updated_at(value: Option<&serde_json::Value>) -> Option<i64> {
    value.and_then(|value| {
        value.as_i64().or_else(|| {
            let timestamp = value.as_str()?;
            let parsed = time::OffsetDateTime::parse(
                timestamp,
                &time::format_description::well_known::Rfc3339,
            )
            .ok()?;
            i64::try_from(parsed.unix_timestamp_nanos() / 1_000_000).ok()
        })
    })
}

fn provider_type_order(provider_type: &str) -> u8 {
    match provider_type {
        "Proxy" => 0,
        "Rule" => 1,
        _ => 2,
    }
}

fn provider_update_path(provider_type: &str, name: &str) -> Result<String, ChimeraError> {
    let endpoint = match provider_type {
        "Proxy" => "/providers/proxies",
        "Rule" => "/providers/rules",
        _ => {
            return Err(ChimeraError::Runtime {
                details: format!("unsupported provider type: {provider_type}"),
            });
        }
    };
    Ok(format!("{endpoint}/{}", encode(name)))
}

#[cfg(test)]
mod tests {
    use super::{parse_providers, provider_update_path};

    #[test]
    fn parses_proxy_and_rule_provider_metadata() {
        let proxies = parse_providers(
            serde_json::json!({
                "providers": {
                    "remote": {
                        "name": "remote",
                        "type": "Proxy",
                        "vehicleType": "HTTP",
                        "updatedAt": "2023-11-14T22:13:20Z",
                        "proxies": [{}, {}]
                    }
                }
            }),
            "Proxy",
        )
        .unwrap();
        let rules = parse_providers(
            serde_json::json!({
                "providers": {
                    "geosite": {
                        "name": "geosite",
                        "vehicleType": "HTTP"
                    }
                }
            }),
            "Rule",
        )
        .unwrap();

        assert_eq!(proxies[0].provider_type, "Proxy");
        assert_eq!(proxies[0].updated_at, Some(1_700_000_000_000));
        assert_eq!(rules[0].provider_type, "Rule");
        assert_eq!(rules[0].updated_at, None);
    }

    #[test]
    fn rejects_provider_responses_without_provider_map() {
        let error = parse_providers(serde_json::json!({}), "Proxy").unwrap_err();

        assert!(error.to_string().contains("providers object"));
    }

    #[test]
    fn excludes_compatible_providers_like_cmfa_external_resources() {
        let proxies = parse_providers(
            serde_json::json!({
                "providers": {
                    "builtin-group": { "vehicleType": "Compatible" },
                    "remote-proxies": { "vehicleType": "HTTP" }
                }
            }),
            "Proxy",
        )
        .unwrap();
        let rules = parse_providers(
            serde_json::json!({
                "providers": {
                    "builtin-rules": { "vehicleType": "Compatible" },
                    "remote-rules": { "vehicleType": "File" }
                }
            }),
            "Rule",
        )
        .unwrap();

        assert_eq!(proxies.len(), 1);
        assert_eq!(proxies[0].name, "remote-proxies");
        assert_eq!(rules.len(), 1);
        assert_eq!(rules[0].name, "remote-rules");
    }

    #[test]
    fn update_path_uses_the_provider_type_and_encodes_its_name() {
        assert_eq!(
            provider_update_path("Rule", "geo set").unwrap(),
            "/providers/rules/geo%20set",
        );
        assert_eq!(
            provider_update_path("Proxy", "remote").unwrap(),
            "/providers/proxies/remote",
        );
        assert!(provider_update_path("Unknown", "remote").is_err());
    }
}
