use crate::core_state::instance;
use clash_lib::{SocketProtector, set_socket_protector};
use jni::jni_str;
use jni::objects::JValue;
use jni::signature::{JavaType, MethodSignature, Primitive};
use std::sync::{Arc, OnceLock};
use tracing::error;

static SOCKET_PROTECTOR_INSTALLED: OnceLock<()> = OnceLock::new();

struct AndroidSocketProtector;

impl SocketProtector for AndroidSocketProtector {
    fn protect_socket_handle(&self, handle: usize) -> std::io::Result<()> {
        let fd = i32::try_from(handle).map_err(|_| {
            std::io::Error::other(format!("socket handle out of i32 range: {handle}"))
        })?;
        let inst = instance();
        let protected = inst
            .jvm
            .attach_current_thread(|env| {
                let protect_socket_sig = unsafe {
                    MethodSignature::from_raw_parts(
                        jni_str!("(I)Z"),
                        &[JavaType::Primitive(Primitive::Int)],
                        JavaType::Primitive(Primitive::Boolean),
                    )
                };
                env.call_method(
                    inst.chimera_ffi.as_obj(),
                    jni_str!("protectSocket"),
                    protect_socket_sig,
                    &[JValue::Int(fd)],
                )
                .and_then(|value| value.z())
            })
            .map_err(|error| {
                std::io::Error::other(format!(
                    "failed to call ChimeraFfi.protectSocket({fd}): {error}"
                ))
            })?;

        if protected {
            Ok(())
        } else {
            Err(std::io::Error::other(format!(
                "VpnService.protect({fd}) returned false"
            )))
        }
    }
}

pub(crate) fn install_socket_protector() {
    if SOCKET_PROTECTOR_INSTALLED.get().is_some() {
        return;
    }

    set_socket_protector(Arc::new(AndroidSocketProtector));
    let _ = SOCKET_PROTECTOR_INSTALLED.set(());
}

pub(crate) fn notify_core_stopped(message: &str) {
    let inst = instance();
    let result = inst.jvm.attach_current_thread(|env| {
        let message = env.new_string(message)?;
        let callback_sig = unsafe {
            MethodSignature::from_raw_parts(
                jni_str!("(Ljava/lang/String;)V"),
                &[JavaType::Object],
                JavaType::Primitive(Primitive::Void),
            )
        };
        env.call_method(
            inst.chimera_ffi.as_obj(),
            jni_str!("onCoreStopped"),
            callback_sig,
            &[JValue::Object(&message)],
        )?;
        Ok::<(), jni::errors::Error>(())
    });

    if let Err(error) = result {
        error!("failed to notify Android about core exit: {error}");
    }
}
