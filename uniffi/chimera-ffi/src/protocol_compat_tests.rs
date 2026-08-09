use super::{ConfigDef, validate_profile_runtime_handlers, validate_runtime_proxy_handlers};
use clash_lib::config::{
    RuntimeConfig,
    internal::proxy::{
        CommonConfigOptions, Hysteria2Obfs, OutboundHysteria2, OutboundProxyProtocol,
        OutboundTrojan, OutboundTrojanRealityOpts, OutboundVless, XhttpOpt,
    },
};
use std::path::PathBuf;

fn fixture_path(file_name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("tests/data/protocols")
        .join(file_name)
}

fn parse_fixture(file_name: &str) -> ConfigDef {
    let path = fixture_path(file_name);
    ConfigDef::try_from(path.clone())
        .unwrap_or_else(|error| panic!("failed to parse {}: {error}", path.display()))
}

fn assert_definition_parses(file_name: &str) {
    let config = parse_fixture(file_name);
    assert_eq!(
        1,
        config.proxy.as_ref().map_or(0, Vec::len),
        "fixture must contain exactly one protocol under test",
    );
}

fn assert_runtime_config_builds(file_name: &str) {
    let config = parse_fixture(file_name);
    let _: RuntimeConfig = config.try_into().unwrap_or_else(|error| {
        panic!("runtime config conversion failed for {file_name}: {error}")
    });
}

fn assert_runtime_handler_builds(file_name: &str) {
    let path = fixture_path(file_name);
    validate_profile_runtime_handlers(&path).unwrap_or_else(|error| {
        panic!("runtime handler validation failed for {file_name}: {error}")
    });
}

macro_rules! protocol_compatibility_tests {
    (
        $parse_test:ident,
        $runtime_test:ident,
        $handler_test:ident,
        $fixture:literal
    ) => {
        #[test]
        fn $parse_test() {
            assert_definition_parses($fixture);
        }

        #[test]
        fn $runtime_test() {
            assert_runtime_config_builds($fixture);
        }

        #[test]
        fn $handler_test() {
            assert_runtime_handler_builds($fixture);
        }
    };
}

protocol_compatibility_tests!(
    hysteria2_definition_parses,
    hysteria2_runtime_config_builds,
    hysteria2_runtime_handler_builds,
    "hysteria2.yaml"
);
protocol_compatibility_tests!(
    vless_definition_parses,
    vless_runtime_config_builds,
    vless_runtime_handler_builds,
    "vless.yaml"
);
protocol_compatibility_tests!(
    reality_definition_parses,
    reality_runtime_config_builds,
    reality_runtime_handler_builds,
    "reality.yaml"
);
protocol_compatibility_tests!(
    xhttp_definition_parses,
    xhttp_runtime_config_builds,
    xhttp_runtime_handler_builds,
    "xhttp.yaml"
);
protocol_compatibility_tests!(
    trojan_definition_parses,
    trojan_runtime_config_builds,
    trojan_runtime_handler_builds,
    "trojan.yaml"
);
fn common_options(name: &str) -> CommonConfigOptions {
    CommonConfigOptions {
        name: name.to_owned(),
        server: "example.com".to_owned(),
        port: 443,
        connect_via: None,
    }
}

fn validation_error(proxy: OutboundProxyProtocol) -> String {
    validate_runtime_proxy_handlers(vec![proxy])
        .expect_err("invalid proxy configuration should fail validation")
}

#[test]
fn unsupported_vless_network_reports_explicit_error() {
    let proxy = OutboundVless {
        common_opts: common_options("unsupported-vless"),
        uuid: "b831381d-6324-4d53-ad4f-8cda48b30811".to_owned(),
        network: Some("grpc".to_owned()),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Vless(proxy));

    assert!(error.contains("proxy `unsupported-vless` (vless)"));
    assert!(error.contains("unsupported vless network: grpc"));
}

#[test]
fn vless_xhttp_missing_options_reports_explicit_error() {
    let proxy = OutboundVless {
        common_opts: common_options("invalid-xhttp"),
        uuid: "b831381d-6324-4d53-ad4f-8cda48b30811".to_owned(),
        network: Some("xhttp".to_owned()),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Vless(proxy));

    assert!(error.contains("xhttp_opts is required for vless xhttp"));
}

#[test]
fn vless_xhttp_zero_limit_reports_explicit_error() {
    let proxy = OutboundVless {
        common_opts: common_options("invalid-xhttp-limit"),
        uuid: "b831381d-6324-4d53-ad4f-8cda48b30811".to_owned(),
        network: Some("xhttp".to_owned()),
        xhttp_opts: Some(XhttpOpt {
            path: Some("/xhttp/".to_owned()),
            max_buffered_posts: Some(0),
            ..Default::default()
        }),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Vless(proxy));

    assert!(error.contains("xhttp max_buffered_posts must be greater than zero"));
}

#[test]
fn trojan_websocket_missing_options_reports_explicit_error() {
    let proxy = OutboundTrojan {
        common_opts: common_options("invalid-trojan-ws"),
        password: "test-password".to_owned(),
        network: Some("ws".to_owned()),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Trojan(proxy));

    assert!(error.contains("ws_opts is required for trojan ws"));
}

#[test]
fn hysteria2_obfs_missing_password_reports_explicit_error() {
    let proxy = OutboundHysteria2 {
        name: "invalid-hysteria2-obfs".to_owned(),
        server: "example.com".to_owned(),
        port: 443,
        password: "test-password".to_owned(),
        obfs: Some(Hysteria2Obfs::Salamander),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Hysteria2(proxy));

    assert!(error.contains("hysteria2 `obfs-password` is required when `obfs` is set"));
}

#[test]
fn invalid_reality_key_reports_runtime_handler_failure() {
    let proxy = OutboundVless {
        common_opts: common_options("invalid-reality-key"),
        uuid: "b831381d-6324-4d53-ad4f-8cda48b30811".to_owned(),
        tls: Some(true),
        network: Some("tcp".to_owned()),
        reality_opts: Some(OutboundTrojanRealityOpts {
            public_key: "not-a-valid-reality-key".to_owned(),
            short_id: Some("85144f63".to_owned()),
        }),
        ..Default::default()
    };

    let error = validation_error(OutboundProxyProtocol::Vless(proxy));

    assert!(error.contains("proxy `invalid-reality-key` (vless)"));
    assert!(error.contains("runtime handler could not be constructed"));
}
