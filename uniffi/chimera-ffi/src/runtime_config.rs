use crate::ProfileOverride;
use clash_lib::{
    app::outbound::manager::OutboundManager,
    config::{
        def::{Config as ConfigDef, Port},
        internal::proxy::{OutboundProxyProtocol, XhttpDownloadSettings, XhttpOpt},
    },
};
use std::path::Path;

fn validate_port(name: &str, port: u16) -> Result<(), String> {
    if port == 0 {
        return Err(format!("{name} port must be between 1 and 65535"));
    }
    Ok(())
}

pub(crate) fn apply_listener_defaults(
    config: &mut ConfigDef,
    over: &ProfileOverride,
) -> Result<u16, String> {
    config.allow_lan = Some(over.allow_lan);

    let mixed_port = if let Some(Port(port)) = config.mixed_port {
        port
    } else {
        validate_port("mixed", over.mixed_port)?;
        config.mixed_port = Some(Port(over.mixed_port));
        over.mixed_port
    };

    if config.port.is_none()
        && let Some(port) = over.http_port
    {
        validate_port("http", port)?;
        config.port = Some(Port(port));
    }
    if config.socks_port.is_none()
        && let Some(port) = over.socks_port
    {
        validate_port("socks", port)?;
        config.socks_port = Some(Port(port));
    }

    Ok(mixed_port)
}

fn validate_xhttp_endpoint(settings: &XhttpDownloadSettings, label: &str) -> Result<(), String> {
    if settings.address.is_empty() {
        return Err(format!("xhttp {label} address must not be empty"));
    }
    if settings.port == 0 {
        return Err(format!("xhttp {label} port must be greater than zero"));
    }
    if settings.network != "xhttp" {
        return Err(format!(
            "xhttp {label} network must be xhttp, got {}",
            settings.network
        ));
    }
    if let Some(security) = settings.security.as_deref()
        && !matches!(security, "none" | "tls" | "reality")
    {
        return Err(format!("unsupported xhttp {label} security: {security}"));
    }
    if matches!(
        settings
            .xhttp_settings
            .as_ref()
            .and_then(|settings| settings.path.as_deref()),
        Some("")
    ) {
        return Err(format!("xhttp {label} path must not be empty"));
    }
    Ok(())
}

fn validate_xhttp_options(options: &XhttpOpt) -> Result<(), String> {
    if matches!(options.path.as_deref(), Some("")) {
        return Err("xhttp path must not be empty".to_string());
    }
    if let Some(mode) = options.mode.as_deref()
        && !matches!(
            mode,
            "stream-one" | "stream-up" | "packet-up" | "split" | "auto"
        )
    {
        return Err(format!("unsupported xhttp mode: {mode}"));
    }
    for (name, value) in [
        ("max_each_post_bytes", options.max_each_post_bytes),
        ("max_buffered_posts", options.max_buffered_posts),
    ] {
        if matches!(value, Some(0)) {
            return Err(format!("xhttp {name} must be greater than zero"));
        }
    }
    if matches!(options.session_ttl, Some(0)) {
        return Err("xhttp session_ttl must be greater than zero".to_string());
    }
    if matches!(
        options
            .extra
            .as_ref()
            .and_then(|extra| extra.sc_max_each_post_bytes),
        Some(0)
    ) {
        return Err("xhttp extra sc_max_each_post_bytes must be greater than zero".to_string());
    }
    if matches!(
        options
            .extra
            .as_ref()
            .and_then(|extra| extra.sc_min_posts_interval_ms),
        Some(0)
    ) {
        return Err("xhttp extra sc_min_posts_interval_ms must be greater than zero".to_string());
    }
    if let Some(settings) = options.upload_settings.as_ref() {
        validate_xhttp_endpoint(settings, "upload_settings")?;
    }
    if let Some(settings) = options
        .extra
        .as_ref()
        .and_then(|extra| extra.download_settings.as_ref())
        .or(options.download_settings.as_ref())
    {
        validate_xhttp_endpoint(settings, "download_settings")?;
    }
    Ok(())
}

fn validate_proxy_options(proxy: &OutboundProxyProtocol) -> Result<(), String> {
    match proxy {
        OutboundProxyProtocol::Vless(proxy) => match proxy.network.as_deref().unwrap_or("tcp") {
            "tcp" => Ok(()),
            "ws" if proxy.ws_opts.is_none() => Err("ws_opts is required for vless ws".to_string()),
            "ws" => Ok(()),
            "xhttp" => proxy
                .xhttp_opts
                .as_ref()
                .ok_or_else(|| "xhttp_opts is required for vless xhttp".to_string())
                .and_then(validate_xhttp_options),
            other => Err(format!("unsupported vless network: {other}")),
        },
        OutboundProxyProtocol::Trojan(proxy) => match proxy.network.as_deref() {
            None => Ok(()),
            Some("ws") if proxy.ws_opts.is_none() => {
                Err("ws_opts is required for trojan ws".to_string())
            }
            Some("ws") => Ok(()),
            Some(other) => Err(format!("unsupported trojan network: {other}")),
        },
        OutboundProxyProtocol::Hysteria2(proxy)
            if proxy.obfs.is_some() && proxy.obfs_password.is_none() =>
        {
            Err("hysteria2 `obfs-password` is required when `obfs` is set".to_string())
        }
        _ => Ok(()),
    }
}

fn proxy_identity(proxy: &OutboundProxyProtocol) -> (&str, &'static str) {
    match proxy {
        OutboundProxyProtocol::Direct(proxy) => (&proxy.name, "direct"),
        OutboundProxyProtocol::Reject(proxy) => (&proxy.name, "reject"),
        OutboundProxyProtocol::Socks5(proxy) => (&proxy.common_opts.name, "socks5"),
        OutboundProxyProtocol::Vless(proxy) => (&proxy.common_opts.name, "vless"),
        OutboundProxyProtocol::Trojan(proxy) => (&proxy.common_opts.name, "trojan"),
        OutboundProxyProtocol::Hysteria2(proxy) => (&proxy.name, "hysteria2"),
        #[allow(unreachable_patterns)]
        _ => ("<unknown>", "unknown"),
    }
}

pub(crate) fn validate_runtime_proxy_handlers(
    proxies: Vec<OutboundProxyProtocol>,
) -> Result<(), String> {
    clash_lib::setup_default_crypto_provider();
    for proxy in proxies {
        let (name, protocol) = proxy_identity(&proxy);
        let name = name.to_owned();
        validate_proxy_options(&proxy)
            .map_err(|error| format!("proxy `{name}` ({protocol}): {error}"))?;
        if OutboundManager::load_plain_outbounds(vec![proxy]).is_empty() {
            return Err(format!(
                "proxy `{name}` ({protocol}) parsed successfully, but its runtime handler could not be constructed; verify protocol options and enabled clash-lib features"
            ));
        }
    }
    Ok(())
}

pub(crate) fn validate_profile_runtime_handlers(profile_path: &Path) -> Result<(), String> {
    let mut config = ConfigDef::try_from(profile_path.to_path_buf()).map_err(|error| {
        format!(
            "failed to parse profile {} for runtime validation: {error}",
            profile_path.display()
        )
    })?;
    validate_runtime_proxy_handlers(config.proxy.take().unwrap_or_default()).map_err(|error| {
        format!(
            "failed to validate runtime proxies {}: {error}",
            profile_path.display()
        )
    })
}

pub(crate) fn load_runtime_config(
    profile_path: &Path,
    over: &ProfileOverride,
) -> Result<(clash_lib::config::RuntimeConfig, u16), String> {
    let mut config_def = ConfigDef::try_from(profile_path.to_path_buf()).map_err(|error| {
        format!(
            "failed to parse profile {}: {error}",
            profile_path.display()
        )
    })?;
    let mixed_port = apply_listener_defaults(&mut config_def, over)?;
    validate_profile_runtime_handlers(profile_path)?;
    let config = config_def.try_into().map_err(|error| {
        format!(
            "failed to build runtime config {}: {error}",
            profile_path.display()
        )
    })?;
    Ok((config, mixed_port))
}
