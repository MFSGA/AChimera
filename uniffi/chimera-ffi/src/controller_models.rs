use serde::{Deserialize, Serialize};
use std::collections::HashMap;

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
pub struct ConnectionSummary {
    pub download_total: i64,
    pub upload_total: i64,
    pub connection_count: i32,
}

#[derive(Debug, Deserialize)]
pub(crate) struct ConnectionSummaryResponse {
    #[serde(rename = "downloadTotal")]
    pub(crate) download_total: i64,
    #[serde(rename = "uploadTotal")]
    pub(crate) upload_total: i64,
    #[serde(rename = "connectionCount")]
    pub(crate) connection_count: i32,
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
pub(crate) struct RulesResponse {
    #[serde(default)]
    pub(crate) rules: Vec<RuleSnapshot>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ProxyProviderSnapshot {
    pub name: String,
    pub provider_type: String,
    pub vehicle_type: String,
    pub proxy_count: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct ProxiesResponse {
    pub(crate) proxies: HashMap<String, Proxy>,
}
