use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::sync::atomic::{AtomicBool, Ordering};

static CORE_RUNNING: AtomicBool = AtomicBool::new(false);

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeHello(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env.new_string("ffi: hello from rust") {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStart(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    profile_path: JString<'_>,
    cache_dir: JString<'_>,
) -> jboolean {
    let profile_path = match env.get_string(&profile_path) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(_) => return JNI_FALSE,
    };

    let cache_dir = match env.get_string(&cache_dir) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(_) => return JNI_FALSE,
    };

    if profile_path.trim().is_empty() || cache_dir.trim().is_empty() {
        return JNI_FALSE;
    }

    CORE_RUNNING.store(true, Ordering::SeqCst);
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_rs_chimera_android_ffi_ChimeraFfi_nativeStop(
    _env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jboolean {
    CORE_RUNNING.store(false, Ordering::SeqCst);
    JNI_TRUE
}
