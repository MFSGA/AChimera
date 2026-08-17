use crate::android_bridge::notify_core_stopped;
use crate::core_lifecycle::{
    CORE_START_TIMEOUT, CORE_STOP_TIMEOUT, should_notify_core_stopped, wait_for_core_ready,
    wait_for_worker_shutdown,
};
use crate::core_state::{
    CoreMetadata, CoreState, clear_last_error, instance, runtime, set_last_error,
};
use crate::runtime_config::load_runtime_config;
use crate::{FinalProfile, ProfileOverride, log_line};
use clash_lib::{config::def::DNSMode, shutdown as clash_shutdown, start};
use ipnet::{IpNet, Ipv4Net, Ipv6Net};
use std::fs::{self, File};
use std::net::{Ipv4Addr, Ipv6Addr, SocketAddr};
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::broadcast;

pub(crate) fn stop_core_internal() -> Result<(), String> {
    let running = {
        let mut guard = instance()
            .core_state
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        guard.take()
    };

    let result = if let Some(state) = running {
        let CoreState {
            worker,
            notify_exit,
            metadata,
        } = state;
        notify_exit.store(false, Ordering::SeqCst);
        let shutdown_sent = clash_shutdown();
        log_line(
            &metadata.log_path,
            if shutdown_sent {
                "chimera core graceful stop requested"
            } else {
                "chimera core stop requested without active shutdown token"
            },
        );
        let result = runtime().block_on(wait_for_worker_shutdown(worker, CORE_STOP_TIMEOUT));
        let _ = fs::remove_file(metadata.socket_path);
        result
    } else {
        Ok(())
    };

    instance().core_running.store(false, Ordering::SeqCst);
    if result.is_ok() {
        clear_last_error();
    }
    result
}

pub(crate) fn start_core_internal(
    profile_path: String,
    cache_dir: String,
    tun_fd: i32,
    over: ProfileOverride,
) -> Result<FinalProfile, String> {
    if profile_path.trim().is_empty() {
        return Err("profile path is empty".to_string());
    }
    if cache_dir.trim().is_empty() {
        return Err("cache dir is empty".to_string());
    }
    if tun_fd <= 0 {
        return Err(format!("invalid tun fd: {tun_fd}"));
    }
    if over.log_file_path.trim().is_empty() {
        return Err("log file path is empty".to_string());
    }
    let profile_path = PathBuf::from(profile_path);
    if !profile_path.exists() {
        return Err(format!(
            "profile file not found: {}",
            profile_path.display()
        ));
    }
    if !profile_path.is_file() {
        return Err(format!(
            "profile path is not a file: {}",
            profile_path.display()
        ));
    }

    let work_dir = PathBuf::from(cache_dir);
    fs::create_dir_all(&work_dir)
        .map_err(|error| format!("failed to create work dir {}: {error}", work_dir.display()))?;

    let mut log_path = PathBuf::from(&over.log_file_path);
    if !log_path.is_absolute() {
        log_path = work_dir.join(log_path);
    }
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)
            .map_err(|error| format!("failed to create log dir {}: {error}", parent.display()))?;
    }
    File::create(&log_path)
        .map_err(|error| format!("failed to create log file {}: {error}", log_path.display()))?;

    let socket_path = work_dir.join("clash.sock");
    let _ = fs::remove_file(&socket_path);

    stop_core_internal()?;
    clear_last_error();

    let work_dir_string = work_dir
        .to_str()
        .ok_or_else(|| "work dir contains invalid UTF-8".to_string())?
        .to_string();

    std::env::set_current_dir(&work_dir).map_err(|error| {
        format!(
            "failed to switch process cwd to {}: {error}",
            work_dir.display()
        )
    })?;

    let profile_path_string = profile_path
        .to_str()
        .ok_or_else(|| "profile path contains invalid UTF-8".to_string())?
        .to_string();

    let (mut config, mixed_port) = load_runtime_config(&profile_path, &over)?;

    config.tun.enable = true;
    config.tun.device_id = format!("fd://{tun_fd}");
    config.tun.route_all = false;
    config.tun.routes = Vec::new();
    config.tun.gateway = Ipv4Net::new(Ipv4Addr::new(10, 0, 0, 1), 30)
        .map_err(|error| format!("failed to build tun gateway: {error}"))?;
    config.tun.gateway_v6 = if over.ipv6 {
        Some(
            Ipv6Net::new(
                "fdfe:dcba:9876::1"
                    .parse::<Ipv6Addr>()
                    .map_err(|error| format!("failed to parse tun IPv6 gateway: {error}"))?,
                126,
            )
            .map_err(|error| format!("failed to build tun IPv6 gateway: {error}"))?,
        )
    } else {
        None
    };
    config.tun.mtu = None;
    config.tun.so_mark = None;
    config.tun.route_table = 0;
    config.tun.dns_hijack = true;

    config.general.ipv6 = over.ipv6;
    config.general.mmdb = Some("Country.mmdb".to_string());
    config.general.controller.external_controller_ipc =
        Some(socket_path.to_string_lossy().to_string());

    config.dns.enable = true;
    config.dns.ipv6 = over.ipv6;
    config.dns.listen.udp = Some(SocketAddr::from(([127, 0, 0, 1], 53_553)));
    if over.fake_ip {
        config.dns.enhance_mode = DNSMode::FakeIp;
        config.dns.fake_ip_range = over
            .fake_ip_range
            .parse::<IpNet>()
            .map_err(|error| format!("invalid fake-ip-range: {error}"))?;
    } else {
        config.dns.enhance_mode = DNSMode::Normal;
    }

    let profile_name = profile_path
        .file_name()
        .and_then(|it| it.to_str())
        .unwrap_or("profile.yaml")
        .to_string();
    let metadata = CoreMetadata {
        profile_name,
        tun_fd,
        work_dir: work_dir.clone(),
        log_path: log_path.clone(),
        socket_path: socket_path.clone(),
    };

    let final_profile = FinalProfile { mixed_port };

    let runtime_log_path = log_path.clone();
    let profile_label = PathBuf::from(&profile_path_string)
        .file_name()
        .and_then(|name| name.to_str())
        .filter(|name| !name.is_empty())
        .unwrap_or("unknown-profile")
        .to_string();
    let ready = Arc::new(AtomicBool::new(false));
    let worker_ready = ready.clone();
    instance().core_running.store(true, Ordering::SeqCst);
    let mut worker = runtime().spawn(async move {
        let (log_tx, _) = broadcast::channel(100);
        log_line(
            &runtime_log_path,
            &format!("starting clash core: profile={profile_label} tun_fd={tun_fd}"),
        );
        let result = start(
            config,
            work_dir_string,
            Some(profile_path_string.clone()),
            log_tx,
        )
        .await
        .map_err(|error| format!("clash core exited with error: {error}"));

        let exit_message = match &result {
            Ok(()) => {
                let message = "clash core exited unexpectedly".to_string();
                log_line(&runtime_log_path, &message);
                message
            }
            Err(message) => {
                set_last_error(message.clone());
                log_line(&runtime_log_path, message);
                message.clone()
            }
        };
        instance().core_running.store(false, Ordering::SeqCst);
        if should_notify_core_stopped(worker_ready.as_ref()) {
            notify_core_stopped(&exit_message);
        }
        result
    });

    if let Err(error) = runtime().block_on(wait_for_core_ready(
        &socket_path,
        &mut worker,
        ready.as_ref(),
        CORE_START_TIMEOUT,
    )) {
        let _ = clash_shutdown();
        worker.abort();
        let _ = fs::remove_file(&socket_path);
        instance().core_running.store(false, Ordering::SeqCst);
        set_last_error(error.clone());
        log_line(&log_path, &error);
        return Err(error);
    }

    {
        let mut guard = instance()
            .core_state
            .lock()
            .map_err(|error| format!("core state lock poisoned: {error}"))?;
        *guard = Some(CoreState {
            worker,
            notify_exit: ready,
            metadata,
        });
    }
    log_line(&log_path, "clash core controller is ready");
    Ok(final_profile)
}
