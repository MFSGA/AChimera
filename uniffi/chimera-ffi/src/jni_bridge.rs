use super::{
    INIT, ProfileOverride, build_hello_message, extract_jstring, install_socket_protector,
    start_core_internal, stop_core_internal,
};
use crate::core_state::{
    ClashInstance, INSTANCE, clear_last_error, instance, runtime, set_last_error,
};
use crate::log::init_logger;
use jni::objects::{JObject, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jstring};
use jni::{EnvUnowned, Outcome};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};
use tracing::{error, info};
use tracing_subscriber::filter::LevelFilter;

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeSetup(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jboolean {
    let vm = match env.with_env(|env| env.get_java_vm()).into_outcome() {
        Outcome::Ok(vm) => vm,
        Outcome::Err(error) => {
            set_last_error(format!("failed to get JavaVM: {error}"));
            return JNI_FALSE;
        }
        Outcome::Panic(_) => {
            set_last_error("failed to get JavaVM: JNI panic");
            return JNI_FALSE;
        }
    };

    let chimera_ffi = match env
        .with_env(|env| env.new_global_ref(&_this))
        .into_outcome()
    {
        Outcome::Ok(reference) => reference,
        Outcome::Err(error) => {
            set_last_error(format!("failed to create ChimeraFfi global ref: {error}"));
            return JNI_FALSE;
        }
        Outcome::Panic(_) => {
            set_last_error("failed to create ChimeraFfi global ref: JNI panic");
            return JNI_FALSE;
        }
    };

    let instance = ClashInstance {
        jvm: vm,
        chimera_ffi,
        rt: OnceLock::new(),
        core_state: Mutex::new(None),
        last_error: Mutex::new(None),
        core_running: AtomicBool::new(false),
    };

    if !(INSTANCE.set(instance).is_ok() || INSTANCE.get().is_some()) {
        set_last_error("failed to initialize ClashInstance");
        return JNI_FALSE;
    }

    INIT.call_once(|| unsafe {
        let level = if cfg!(debug_assertions) {
            LevelFilter::DEBUG
        } else {
            LevelFilter::INFO
        };
        std::env::set_var("RUST_BACKTRACE", "1");
        init_logger(level);
        let _ = color_eyre::install();

        // Route panic backtraces through tracing so Android logcat keeps them.
        let previous_hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |info| {
            let backtrace = std::backtrace::Backtrace::force_capture();
            error!(target: "panic", "thread panicked: {info}\n{backtrace}");
            previous_hook(info);
        }));

        // Install aws-lc-rs as the default crypto provider
        let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();
        info!("native logger initialized");
    });

    install_socket_protector();
    let _ = runtime();
    clear_last_error();
    info!("native setup complete");
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeHello(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env
        .with_env(|env| env.new_string(build_hello_message()))
        .into_outcome()
    {
        Outcome::Ok(value) => value.into_raw(),
        _ => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStart(
    mut env: EnvUnowned<'_>,
    _this: JObject<'_>,
    profile_path: JString<'_>,
    cache_dir: JString<'_>,
    tun_fd: jint,
    log_file_path: JString<'_>,
) -> jboolean {
    let profile_path = match extract_jstring(&mut env, profile_path, "profile_path") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };
    let cache_dir = match extract_jstring(&mut env, cache_dir, "cache_dir") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };
    let log_file_path = match extract_jstring(&mut env, log_file_path, "log_file_path") {
        Ok(value) => value,
        Err(error) => {
            set_last_error(error);
            return JNI_FALSE;
        }
    };

    let over = ProfileOverride {
        tun_fd,
        log_file_path,
        allow_lan: false,
        mixed_port: 7890,
        http_port: None,
        socks_port: None,
        fake_ip: false,
        fake_ip_range: "198.18.0.2/16".to_string(),
        ipv6: false,
    };

    match start_core_internal(profile_path, cache_dir, tun_fd, over) {
        Ok(_) => JNI_TRUE,
        Err(error) => {
            set_last_error(error);
            instance().core_running.store(false, Ordering::SeqCst);
            JNI_FALSE
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStop(
    _env: EnvUnowned<'_>,
    _this: JObject<'_>,
) -> jboolean {
    match stop_core_internal() {
        Ok(()) => JNI_TRUE,
        Err(error) => {
            set_last_error(error);
            JNI_FALSE
        }
    }
}
