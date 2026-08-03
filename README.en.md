# AChimera

AChimera is an experimental proxy client project based on Android + Kotlin/Jetpack Compose + Rust/UniFFI. The current repository already includes core capabilities such as an Android UI, VPN service entry, config import and validation, Rust core bridging, log viewing, and a node panel. Its goal is to separate the mobile experience and the underlying network core into a clear multi-module structure.
The current app name is `Chimera Lite`, and the package includes both the Android UI layer and the Rust core exposed to Kotlin through UniFFI.

## Supported Protocols

- `hysteria2`
- `reality`
- `xhttp`
- `trojan`
- `vless`
- `tls+ws`

## Tech Stack

- Android SDK 36
- Kotlin + Jetpack Compose
- Java 25 Toolchain
- Rust 2024
- UniFFI
- `cargo-ndk`
- `clash-lib` as the Rust core dependency

## Requirements

Before building locally, prepare at least:

- JDK 25
- Android SDK 36 and Build Tools 36.0.0
- Android NDK `29.0.14206865`
- Rust toolchain
- `cargo-ndk`

It is recommended to configure one of the following so Gradle can locate the SDK/NDK:

- `sdk.dir` in `local.properties`, and optionally `ndk.dir`
- or `ANDROID_SDK_ROOT`
- or `ANDROID_HOME`
- optionally `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`

Install `cargo-ndk`:

```powershell
cargo install cargo-ndk --locked
```

## Local Build

Build the Debug APK:

```powershell
.\gradlew assembleDebug
```

Run quality checks:

```powershell
.\gradlew testDebugUnitTest lintDebug
```

Build the Release APK:

```powershell
.\gradlew assembleRelease
```

The `core` module automatically completes two steps before Android compilation:

1. Use `cargo ndk` to build `chimera-ffi` for Android ABIs
2. Run `uniffi-bindgen` against the generated dynamic library to output Kotlin bindings

## Current Status and Limitations

This project is still evolving, and the README only describes what has already been implemented in the current repository. Based on the existing strings and implementation, some known limitations still remain:

- after stopping the VPN, the current implementation may restart the app to avoid an unsafe runtime state
- some settings only fully take effect after restarting the VPN when switching configurations
- `clash-rs` / `clash-lib` still has limited compatibility with some mihomo configurations

## License

The project uses the [LICENSE](LICENSE) file in the repository root.

## Contributing

- If you run into usage issues or implementation problems, feel free to open an Issue or PR.
- Even if you are a complete beginner, after reading the [wiki](https://mfsga.github.io/Proxy_WIKI/), you can continue asking more specific questions.
- The project also hopes to attract more developers to help improve it.

---

If this project helps you, consider giving it a star 🧡
