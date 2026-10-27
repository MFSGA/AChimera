# AChimera

> Chimera Lite 是一款面向 Android 的实验性代理客户端：界面由 Kotlin / Jetpack Compose 构建，网络代理核心由 Rust 编写，并通过 UniFFI 与 Android 应用连接。

AChimera 提供 Watfaq（Compose）和 MetaCubeX 风格两套可切换界面；两套界面共用同一配置、设置、VPN 服务和 Rust 核心。Android 侧负责应用界面、系统 VPN 接入及业务协调，代理运行由原生 Rust 核心承担。

## Rust 核心与协议支持

Rust 核心以原生代码运行，目标是在提供高效网络处理能力的同时保持较低的运行开销。实际 CPU 和内存占用会随设备、配置、协议、规则及连接数量变化；本项目不以未经测量的固定数值作为资源占用承诺。

核心继承上游 clash-chimera 的广泛代理协议与传输能力。**AChimera 具体支持哪些协议和特性，以本仓库锁定的上游 [Chimera_Client `clash-lib` 修订及 feature 定义](https://github.com/MFSGA/Chimera_Client/blob/1fe744218ebd0df7e9101e0191fedef22239302d/clash-lib/Cargo.toml) 和本项目显式启用的[核心 feature 配置](uniffi/chimera-ffi/Cargo.toml)为准。** 上游核心具备的能力不一定都已由 AChimera 的配置解析、Android 集成或界面开放；因此 README 不维护可能过期的独立协议清单，也不承诺兼容所有 mihomo 配置。

## 应用能力

- 在 Watfaq（Compose）与 MetaCubeX 风格界面之间切换，共用同一运行状态和配置数据。
- 通过 Android VPN 服务接入系统流量，并管理配置、代理组与节点选择。
- 导入和校验配置，查看运行日志及节点状态。
- 通过 UniFFI 将 Kotlin 应用层连接到 Rust 代理核心。

## 项目生态

- [Chimera](https://github.com/MFSGA/Chimera) — 跨平台桌面应用
- [Chimera_Client](https://github.com/MFSGA/Chimera_Client) — Rust 客户端核心及 `clash-lib`
- [Chimera_Service](https://github.com/MFSGA/Chimera_Service) — Rust 服务端项目
- [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) — 使用与部署文档

## 技术栈

- Android SDK 36、Android NDK `29.0.14206865`
- Kotlin、Jetpack Compose、Java 25 toolchain
- Rust `1.97.1`（Rust 2024 edition）、`cargo-ndk`
- UniFFI；上游 Rust `clash-lib` 依赖以完整 Git revision 锁定

## 本地构建

### 环境要求

- JDK 25
- Android SDK 36 和 Build Tools 36.0.0
- Android NDK `29.0.14206865`
- Rust `1.97.1`，以及仓库 `rust-toolchain.toml` 中声明的 Android targets 和组件
- `cargo-ndk`；`just` 仅在生成或检查 UniFFI 绑定时需要

让 Gradle 找到 Android SDK/NDK，可在本机 `local.properties` 中配置 `sdk.dir`（可选 `ndk.dir`），或设置 `ANDROID_SDK_ROOT` / `ANDROID_HOME`（可选 `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`）。`local.properties` 是本机配置，不要提交其中的本机路径。

安装命令行构建依赖：

```sh
cargo install cargo-ndk --locked
```

仅在需要生成或检查 UniFFI 绑定时，还需安装 `just`：

```sh
cargo install just --locked
```

### 构建与检查

构建 Debug APK（默认仅包含 `x86_64` ABI）：

```sh
./gradlew :app:assembleDebug
```

构建包含全部 Android ABI 的 Debug APK：

```sh
./gradlew :app:assembleDebug -Pchimera.fullAbi=true
```

运行 Kotlin / Android 质量检查：

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug
```

Rust 导出接口变化后，使用仓库脚本生成 Kotlin UniFFI 绑定；检查已提交绑定与 Rust 接口是否一致：

```sh
just generate-bindings
just check-bindings
```

Gradle 构建会为 Android ABI 编译 Rust 原生库，但**不会自动重新生成 Kotlin UniFFI 绑定**。绑定检查和生成需要 Android NDK、Rust Android target 与 `cargo-ndk`。

Windows 可将上述 `./gradlew` 替换为 `.\gradlew.bat`。`scripts/start-emulator.sh` 需要 Bash 环境；运行前请在 Android Studio Device Manager 中创建 AVD：

```sh
./scripts/start-emulator.sh
# 或指定 AVD 名称
./scripts/start-emulator.sh YOUR_AVD_NAME
```

## 构建产物与签名

GitHub Actions 每日运行 Nightly 构建，也支持在 Actions 页面手动触发。构建的全 ABI Debug APK 会作为 Actions Artifact 保留 14 天；工作流还会把 APK 和校验文件上传到滚动的 GitHub `nightly` 预发布。Debug APK 使用 Android 默认 debug key，不是正式发布签名。可从[项目 Releases 页面](https://github.com/MFSGA/AChimera/releases)下载预发布 APK，也可从对应的 Actions 运行记录下载限期保留的 Artifact。

正式 Release APK 需要配置发布签名。密钥配置方式见[Release APK 签名说明](RELEASE_SIGNING.md)；仓库中的 [`SIGNING_CERT_SHA256.txt`](SIGNING_CERT_SHA256.txt) 用于核对不同版本 APK 是否由同一证书签名。证书连续性可以验证签名身份是否一致，但本身不证明现实中的个人或公司身份。

## 当前状态与限制

AChimera 仍在持续开发。实际可用能力以当前源码、锁定的 Rust 核心修订及启用的 feature 为准。上游协议支持不等于 Android 应用已经暴露全部相应功能；部分核心配置或 mihomo 配置可能尚未兼容。构建成功也不等同于已验证特定协议连接、VPN 生命周期或所有 Android 设备上的运行行为。

## 许可证与贡献

项目许可证见仓库根目录的 [LICENSE](LICENSE)。欢迎通过 Issue 反馈问题或提交 PR；开始前可先阅读 [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) 和[应用架构说明](ARCHITECTURE.md)。

English version: [README.en.md](README.en.md).
