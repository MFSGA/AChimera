use jni::objects::JObject;
use jni::sys::jstring;
use jni::JNIEnv;

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
