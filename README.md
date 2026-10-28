# AChimera

本文语言：中文 · 其他语言：[English](README.en.md) · [فارسی](README.fa.md) · [Русский](README.ru.md)

> Chimera Lite 是一款面向 Android 的实验性代理客户端。Android 应用层使用 Kotlin：Watfaq 界面基于 Jetpack Compose，MetaCubeX 风格界面采用 Android 原生视图；代理核心由 Rust 编写，并通过 UniFFI 接入 Kotlin。

AChimera 提供 Watfaq（Compose）和 MetaCubeX 风格两套可切换界面；两套界面共用同一配置、设置、VPN 服务和 Rust 核心。Android 侧负责应用界面、系统 VPN 接入及业务协调，代理运行由原生 Rust 核心承担。

## Rust 核心与协议支持

代理核心由 Rust 编写，并以 Android 原生库运行，设计目标是在提供高效网络处理能力的同时保持较低的资源开销。实际 CPU 和内存占用会随设备、配置、协议、规则及连接数量变化；本项目不以未经测量的固定数值作为资源占用承诺。

我们承诺将 **Mihomo 配置兼容**作为持续对齐目标：逐步对齐配置解析与运行行为，并针对支持范围补充验证。这里承诺的是持续缩小与 Mihomo 的兼容差距，不代表当前版本已能不加修改地运行所有 Mihomo 配置。实际支持范围由本项目锁定的上游版本、显式启用的核心 feature、本地[运行时校验](uniffi/chimera-ffi/src/runtime_config.rs)及 Android 集成共同决定。

当前核心锁定于 [Chimera_Client v0.28.1](https://github.com/MFSGA/Chimera_Client/releases/tag/v0.28.1)，对应完整修订 [`daf67cb`](https://github.com/MFSGA/Chimera_Client/tree/daf67cb09a8f37bbca59b9758801155a2989962a)。下表依据该修订的 [`clash-lib` 配置类型](https://github.com/MFSGA/Chimera_Client/blob/daf67cb09a8f37bbca59b9758801155a2989962a/clash-lib/src/config/internal/proxy.rs)、[协议转换实现](https://github.com/MFSGA/Chimera_Client/blob/daf67cb09a8f37bbca59b9758801155a2989962a/clash-lib/src/proxy/converters/mod.rs)及本项目[显式启用的 features](uniffi/chimera-ffi/Cargo.toml)整理；它描述的是当前配置与运行实现范围，不是上游能力全集。

| Mihomo 配置类型 | 当前状态 | 支持范围与已知限制 |
| --- | --- | --- |
| `ss`（Shadowsocks） | 已启用 | 支持 Shadowsocks 出站代理；配置包含加密方式、密码、UDP 及插件选项。当前实现的插件范围包括 `obfs`、`v2ray-plugin` 和 `shadow-tls`，不代表任意 SIP003 插件都可用。 |
| `socks5` | 已启用 | 支持 SOCKS5 出站代理；配置模型包含认证、TLS 与 UDP 选项。 |
| `anytls`（AnyTLS） | 已启用 | 支持 AnyTLS 出站代理及 TLS 客户端证书配置；客户端指纹和 idle-session 相关字段可读取，但当前不会应用。 |
| `vless`（VLESS） | 部分支持 | 本项目运行时校验允许 TCP、WebSocket 和 XHTTP；TLS、REALITY 与 VLESS 原生加密 feature 已启用。XUDP 尚未实现（对应节点会关闭 UDP），SMUX 设置虽可读取但不会生效；目前原生加密仅支持 1-RTT。 |
| `wireguard`（WireGuard） | 已启用 | 支持 WireGuard 出站配置，包含密钥、地址及路由相关选项。 |
| `trojan`（Trojan） | 部分支持 | 支持 TLS 及默认 TCP、WebSocket 网络；虽然上游转换器包含 gRPC 实现，本项目的运行时校验目前仍拒绝 gRPC。`client-fingerprint` 字段可被读取，但客户端指纹模拟尚未实现。 |
| `hysteria2`（Hysteria 2） | 已启用 | 上游 Hysteria 2（QUIC/TLS）实现已启用，支持项以该版本配置模型为准；`client-fingerprint` 不代表已实现客户端指纹模拟。 |
| `direct` / `reject` | 内置动作 | 分别表示直连和拒绝流量，不属于代理协议。 |

传输与安全能力方面，本项目启用了 TLS、REALITY、WebSocket、XHTTP-H3 和 VLESS 原生加密相关 feature。需要注意，feature 已启用不等于 Mihomo 的每个字段组合都已适配：例如当前运行时校验不接受 VLESS/Trojan 的 gRPC 网络。`http-port`、`mixed-port` 是本地入站监听端口，不代表支持 HTTP 出站代理；当前出站类型定义也不包含 `vmess`。

## 应用能力

- 在 Watfaq（Compose）与 MetaCubeX 风格界面之间切换，共用同一运行状态和配置数据。
- 通过 Android VPN 服务接入系统流量，并管理配置、代理组与节点选择。
- 导入和校验配置，查看运行日志及节点状态。
- 通过 UniFFI 将 Kotlin 应用层连接到 Rust 代理核心。

## 项目生态

- [Chimera](https://github.com/MFSGA/Chimera) — 跨平台桌面应用
- [Chimera_Client](https://github.com/MFSGA/Chimera_Client) — Rust 客户端核心及 `clash-lib`
- [Chimera Server](https://github.com/MFSGA/Chimera_Server) — Rust 编写的代理服务端，以兼容 Xray-core 的服务端配置与运行行为为目标；实际支持范围和验证状态以其兼容性矩阵及验证记录为准
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
- `cargo-ndk`；只有使用仓库 `justfile` 中的命令时才需要安装 `just`

让 Gradle 找到 Android SDK/NDK，可在本机 `local.properties` 中配置 `sdk.dir`（可选 `ndk.dir`），或设置 `ANDROID_SDK_ROOT` / `ANDROID_HOME`（可选 `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`）。`local.properties` 是本机配置，不要提交其中的本机路径。

安装命令行构建依赖：

```sh
cargo install cargo-ndk --locked
```

如需使用下方的 `just` 命令，还需安装 `just`：

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

AChimera 仍在持续开发，并以逐步对齐 Mihomo 配置为兼容目标。各版本的实际能力以当前源码、锁定的 Rust 核心修订、启用的 feature 及 Android 集成为准；上游支持不代表所有配置字段或组合均已适配。构建成功也不等同于已验证特定协议连接、VPN 生命周期或所有 Android 设备上的运行行为。

## 许可证与贡献

项目许可证见仓库根目录的 [LICENSE](LICENSE)。欢迎通过 Issue 反馈问题或提交 PR；开始前可先阅读 [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) 和[应用架构说明](ARCHITECTURE.md)。
