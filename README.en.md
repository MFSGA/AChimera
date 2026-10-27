# AChimera

This README: English · Also available in: [中文](README.md) · [فارسی](README.fa.md) · [Русский](README.ru.md)

> Chimera Lite is an experimental Android proxy client. The Android app layer uses Kotlin: the Watfaq interface is built with Jetpack Compose, while the MetaCubeX-style interface uses native Android views. The proxy engine is written in Rust and connected to Kotlin through UniFFI.

AChimera offers two switchable interfaces: Watfaq (Compose) and a MetaCubeX-style UI. Both share the same profiles, settings, VPN service, and Rust core. The Android layer handles the app UI, system VPN integration, and application-level coordination; proxy traffic is handled by the native Rust core.

## Rust Core and Protocol Support

The proxy core is written in Rust and runs as an Android native library. It is designed for efficient network processing with low resource overhead. Actual CPU and memory use vary with the device, configuration, protocols, rules, and number of connections; this project does not claim fixed resource figures without benchmark data.

The core inherits broad proxy protocol and transport capabilities from the upstream clash-chimera core. **The protocols and features available in AChimera are determined by the pinned upstream [`clash-lib` revision and feature definitions in Chimera_Client](https://github.com/MFSGA/Chimera_Client/blob/daf67cb09a8f37bbca59b9758801155a2989962a/clash-lib/Cargo.toml) and this project's explicitly enabled [core features](uniffi/chimera-ffi/Cargo.toml).** The currently pinned revision is [Chimera_Client v0.28.1](https://github.com/MFSGA/Chimera_Client/releases/tag/v0.28.1). An upstream capability is not necessarily exposed by AChimera's configuration parser, Android integration, or UI. For that reason, this README does not maintain a separate protocol list that could become stale, and does not promise compatibility with every mihomo configuration.

## App Capabilities

- Switch between the Watfaq (Compose) and MetaCubeX-style interfaces while sharing the same runtime state and profile data.
- Route system traffic through Android's VPN service and manage profiles, proxy groups, and node selection.
- Import and validate profiles, and view runtime logs and node status.
- Connect the Kotlin app layer to the Rust proxy core through UniFFI.

## Chimera Ecosystem

- [Chimera](https://github.com/MFSGA/Chimera) — cross-platform desktop application
- [Chimera_Client](https://github.com/MFSGA/Chimera_Client) — Rust client core and `clash-lib`
- [Chimera Server](https://github.com/MFSGA/Chimera_Server) — a Rust proxy server aiming for Xray-core-compatible server configuration and runtime behavior; see its compatibility matrix for the current scope
- [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) — usage and deployment documentation

## Tech Stack

- Android SDK 36 and Android NDK `29.0.14206865`
- Kotlin, Jetpack Compose, and Java 25 toolchain
- Rust `1.97.1` (Rust 2024 edition) and `cargo-ndk`
- UniFFI; the upstream Rust `clash-lib` dependency is pinned to a full Git revision

## Build Locally

### Requirements

- JDK 25
- Android SDK 36 and Build Tools 36.0.0
- Android NDK `29.0.14206865`
- Rust `1.97.1`, with the Android targets and components declared in `rust-toolchain.toml`
- `cargo-ndk`; `just` is needed only to generate or check UniFFI bindings

To let Gradle locate the Android SDK/NDK, configure `sdk.dir` (and optionally `ndk.dir`) in a local `local.properties`, or set `ANDROID_SDK_ROOT` / `ANDROID_HOME` (and optionally `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`). `local.properties` contains machine-specific configuration; do not commit local paths.

Install the command-line build tools:

```sh
cargo install cargo-ndk --locked
```

Install `just` as well only if you need to generate or check UniFFI bindings:

```sh
cargo install just --locked
```

### Build and Checks

Build a Debug APK (the default build includes only the `x86_64` ABI):

```sh
./gradlew :app:assembleDebug
```

Build a Debug APK for all Android ABIs:

```sh
./gradlew :app:assembleDebug -Pchimera.fullAbi=true
```

Run Kotlin / Android quality checks:

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug
```

After changing Rust-exported interfaces, generate the Kotlin UniFFI bindings with the repository script. To check that the committed bindings match the Rust interface:

```sh
just generate-bindings
just check-bindings
```

Gradle builds the Rust native library for Android, but **does not automatically regenerate Kotlin UniFFI bindings**. Binding generation and checks require the Android NDK, Rust Android targets, and `cargo-ndk`.

On Windows, replace `./gradlew` with `.\gradlew.bat`. The `scripts/start-emulator.sh` helper requires a Bash environment. Create an AVD in Android Studio Device Manager first:

```sh
./scripts/start-emulator.sh
# Or specify an AVD name
./scripts/start-emulator.sh YOUR_AVD_NAME
```

## Builds and Signing

GitHub Actions runs a daily Nightly build, which can also be triggered manually from the Actions page. The full-ABI Debug APK is uploaded as an Actions Artifact retained for 14 days; the workflow separately uploads the APKs and checksum file to the rolling GitHub `nightly` prerelease. Nightly Debug APKs use Android's default debug key, not the stable release signing key. Download prerelease APKs from the [project Releases page](https://github.com/MFSGA/AChimera/releases), or download the time-limited Artifact from its Actions run.

Stable Release APKs require release-signing configuration. See [Release APK signing](RELEASE_SIGNING.md) for setup. The repository's [`SIGNING_CERT_SHA256.txt`](SIGNING_CERT_SHA256.txt) lets users check whether APKs from different versions were signed by the same certificate. Certificate continuity verifies a consistent signing identity, but does not by itself prove a real-world person's or company's identity.

## Status and Limitations

AChimera is under active development. Available behavior is determined by the current source, pinned Rust core revision, and enabled features. Upstream protocol support does not mean every related capability is already exposed in the Android app; some core or mihomo configuration options may not be compatible. A successful build also does not prove that a specific protocol connection, VPN lifecycle, or behavior on every Android device has been verified.

## License and Contributions

See the repository-root [LICENSE](LICENSE). Issues and pull requests are welcome. Before contributing, you may also find the [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) and [application architecture guide](ARCHITECTURE.md) useful.
