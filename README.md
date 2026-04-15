# AChimera

AChimera 是一个基于 Android + Kotlin/Jetpack Compose + Rust/UniFFI 的实验性代理客户端工程。当前仓库已经具备 Android 界面、VPN 服务入口、配置导入与校验、Rust 核心桥接、日志查看和节点面板等基础能力，目标是把移动端体验与底层网络核心拆分为清晰的多模块结构。
当前应用名称为 `Chimera Lite`，包内同时包含 Android UI 层和通过 UniFFI 暴露给 Kotlin 的 Rust 核心。

## 支持协议

- `hysteria2`
- `reality`
- `xhttp`
- `trojan`
- `vless`
- `tls+ws`

## 技术栈

- Android SDK 36
- Kotlin + Jetpack Compose
- Java 21 Toolchain
- Rust 2021
- UniFFI
- `cargo-ndk`
- `clash-lib` Rust 核心依赖

## 环境要求

在本地构建前，至少需要准备：

- JDK 21
- Android SDK
- Android NDK `29.0.14206865`
- Rust toolchain
- `cargo-ndk`

推荐同时配置以下环境之一来让 Gradle 找到 SDK/NDK：

- `local.properties` 中的 `sdk.dir` 和可选的 `ndk.dir`
- 或 `ANDROID_SDK_ROOT`
- 或 `ANDROID_HOME`
- 可选 `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`

安装 `cargo-ndk`：

```powershell
cargo install cargo-ndk --locked
```

## 本地构建

构建 Debug APK：

```powershell
.\gradlew assembleDebug
```

运行质量检查：

```powershell
.\gradlew testDebugUnitTest lintDebug
```

构建 Release APK：

```powershell
.\gradlew assembleRelease
```

`core` 模块会在 Android 编译前自动完成两件事：

1. 使用 `cargo ndk` 为 Android ABI 构建 `chimera-ffi`
2. 基于生成的动态库运行 `uniffi-bindgen`，输出 Kotlin 绑定代码

## 当前状态与限制

这是一个仍在演进中的工程，README 只描述当前仓库内已实现的内容。根据现有字符串和实现，已知仍存在一些限制：

- 停止 VPN 后当前实现可能会通过重启应用来回避不安全的运行时状态
- 部分设置在切换配置后需要重启 VPN 才会完全生效
- `clash-rs` / `clash-lib` 对部分 mihomo 配置的兼容性仍有限

## 许可证

项目使用仓库根目录中的 [LICENSE](LICENSE)。

## 贡献

- 有任何使用上的问题，或者代码实现上的问题，欢迎提 Issue 或 PR。
- 即使你是完全的新手，在查阅完 [wiki](https://mfsga.github.io/Proxy_WIKI/) 后，也可以继续有针对性地提问。
- 本项目也希望吸引更多开发者一起参与完善。

---

如果觉得有帮助，欢迎点个 star 🧡
