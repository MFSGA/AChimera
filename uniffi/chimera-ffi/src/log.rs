use tracing_error::ErrorLayer;
use tracing_subscriber::EnvFilter;
#[allow(unused_imports)]
use tracing_subscriber::{filter::LevelFilter, fmt::format::FmtSpan, prelude::*};

pub(crate) fn init_logger(level: LevelFilter) {
    let filter = EnvFilter::from_default_env()
        .add_directive(format!("clash={}", level).parse().unwrap())
        .add_directive(format!("clash_lib={}", level).parse().unwrap())
        .add_directive(format!("clash_android_ffi={}", level).parse().unwrap())
        .add_directive("warn".parse().unwrap());

    #[cfg(target_os = "android")]
    {
        let android_layer = paranoid_android::layer("clash-rs")
            .with_ansi(false)
            .with_span_events(FmtSpan::CLOSE)
            .with_thread_names(true)
            .without_time()
            .with_filter(LevelFilter::TRACE)
            .boxed();

        tracing_subscriber::registry()
            .with(filter)
            .with(android_layer)
            .with(ErrorLayer::default())
            .init();
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing_subscriber::registry()
            .with(filter)
            .with(ErrorLayer::default())
            .init();
    }
}
