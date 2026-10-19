# AChimera Agent 工作指南

本文件适用于整个仓库；更深目录中的 `AGENTS.md` 或 `AGENTS.override.md` 可为对应子树补充或覆盖规则。默认使用中文沟通，代码命名沿用现有风格。

## 工作方式与完成标准

- 开始修改前先检查工作区差异和相关实现，保留用户已有改动；发现重叠修改时先理解其意图，不回退或覆盖无关内容。
- 以当前源码、构建脚本和 CI 为事实来源；README 和参考项目只提供背景，不能证明本项目已支持某项能力。
- 围绕一个可验证的问题做最小改动，优先复用现有抽象和工具，避免夹带无关重构、全仓格式化或依赖升级。
- 信息足以安全推进时直接实施；只有当缺失信息会显著改变结果、涉及额外权限或扩大任务范围时才暂停并询问。
- 修改完成后检查最终 diff，并按影响范围运行最小且有意义的验证。验证通过后，不因习惯重复或扩大测试；失败时先判断是代码问题、环境问题还是既有失败。
- 未经用户明确要求，不创建提交、不推送远端、不修改发布状态。不要把本机 SDK 路径、签名密钥、密码或其他秘密写入版本控制。
- 交付时说明改动内容与原因、实际运行的验证及结果，以及仍未验证的限制；不要把编译成功表述为协议、VPN 连通性或真机行为已经验证。

## Codex AI 研发治理

本项目采用“计划 → 编码 → 验证 → 独立审查 → PR → 复盘”的可审计研发流程。
详细流程及风险审批见 [AI 研发治理规范](docs/ai-development-governance.md)，
独立 Reviewer 使用 [审查清单](docs/ai-review-checklist.md)，
实现之前查阅与本任务相关的 [历史工程教训](LESSONS.md)。
这些文件补充本指南，不得削弱本文件的 Android / Rust / UniFFI 要求，
也不能替代 [应用架构](ARCHITECTURE.md) 和 [AI 提交署名](LLM.md)。

- 在非平凡任务编码前明确**目标、非目标、影响范围、风险等级（L0–L3）、验证计划和回滚方式**。
  使用 `.github/ISSUE_TEMPLATE/ai-task.md` 记录需要跨会话传递的任务。
  先检查实际代码、当前工作区和更深层的 Agent 规则，不把提议的设计当作已实现能力。
- Codex 开发者负责最小实现及测试；独立 Reviewer 必须从新的上下文检查真实 diff、
  调用关系和验证证据。Coder 自我评价、一次编译成功或 AI 审查结论都不能替代必要测试。
- 并行任务须使用**独立 Git 分支和 Worktree**，不得让两个编码 Agent 共用可写工作目录、
  index 或竞争同一生成文件；Rust/Gradle 缓存并发有干扰时分离构建输出。
  保留其他用户与 Agent 尚未提交的修改，不重置、覆盖或纳入无关变更。
- 每个任务按实际影响选择 JVM/Rust/UniFFI 检查；涉及 VPN、权限、后台恢复、双界面
  或真机网络行为时，需要相应设备/模拟器与实测证据，不能将未运行的检查标为通过。
- 提交 PR 前填写 `.github/pull_request_template.md`，准确列出执行命令、结果、
  没跑的检查、风险等级、独立审查状态和人工审批要求；`LLM.md` 的
  `Assisted-by` 规则保持生效，不能臆造模型版本或工具。
- L0 普通解释性文档、L1 无业务行为的局部 UI 修饰，未来可在保护规则生效后允许
  自动合并；L2 涉及 VPN、配置/用户数据、异步状态、FFI、依赖/权限，L3 涉及治理、
  CI/发布、签名凭据或高权限脚本，均需人工批准。风险取最高级，详见治理规范。
- 未获授权不得直接提交、推送、合并或发布；不得修改分支保护、质量检查或签名设置
  来“通过”失败检查。本文件**没有启用**自动开发、AI Review 或自动合并工作流。
- 已确认缺陷的复盘必须记下复现、根因、修复与防复发验证；把重复教训升级为规范
  需要维护者批准，不能由 Agent 自行放宽门禁。
- 当前仓库存在定时 Nightly 预发布及版本标签/手动触发的稳定发布工作流；
  **不要把这两类发布等同**，也不要把 AI 任务完成当作授权触发 release。
  变更 GitHub 工作流前先阅读 `.github/WORKFLOWS.md`，核实真实触发器、签名及权限。

## 项目结构与修改边界

- `app/`：Android 应用，包含 Compose UI、`ui/metacubex` 界面及 XML 资源、ViewModel、backend 和 VPN 服务。包名沿用 `rs.chimera.android`。
- `app/src/main/java/rs/chimera/android/backend/`：应用后端接口、运行状态、配置管理和更新逻辑。新增业务操作优先沿用 `ChimeraBackend` 边界，避免在界面中重复实现核心控制或持久化逻辑。
- `core/`：Android 库模块，负责 Rust 原生库构建集成和 Kotlin 绑定；命名空间为 `rs.chimera.android.ffi`。
- `uniffi/chimera-ffi/`：Rust FFI 适配层，连接 Android 与 `clash-lib`；`uniffi/uniffi-bindgen/` 为绑定生成工具。
- `uniffi/Cargo.toml`、`uniffi/Cargo.lock`：Rust workspace 配置和已提交的依赖锁文件。
- `justfile`、`.github/workflows/ci.yml`：绑定生成、检查和持续集成命令的依据。

修改共享 backend 或状态模型时，检查两套界面的调用方；修改 VPN 生命周期、配置持久化或异步操作时，检查取消、失败恢复和并发状态是否仍然正确。

## clash-lib / Chimera_Client 依赖锁定

所有 `clash-lib` 依赖必须锁定到明确、可复现的发布版本对应修订。

- 采用 release-first 流程：先选择明确的 `Chimera_Client` release，再向远端核实 tag、解析完整提交 SHA，并检查该 SHA 的源码。
- 对 `MFSGA/Chimera_Client` Git 依赖必须使用 `rev = "<完整提交 SHA>"`。禁止使用 `master`、其他浮动分支、`HEAD` 或未锁定的 Git 依赖；依赖声明使用完整 SHA，而不是仅使用 `tag = "vX.Y.Z"`。
- release tag 用于选择和审计版本，完整 SHA 是构建使用的依赖身份。不能假定本地 tag 与远端一致；附注 tag 应解析到其指向的 commit，而非 tag 对象 SHA。
- 如改用包注册表，使用精确版本要求（例如 `=X.Y.Z`），不能使用浮动 semver 范围。
- `uniffi/Cargo.lock` 必须提交，并与所选 revision/version 保持同步。同一锁文件中的 `Chimera_Client` Git 来源应解析到同一个目标 SHA。
- 保留 `clash-lib` 的显式 feature 列表和 `default-features = false`；调整 feature 时检查受影响协议、监听器及 Android 构建能力。
- 不得为了一个修复直接切换到更新分支。若所选 release 缺少必要修复，应先在 `Chimera_Client` 落地或回移并验证，再产出或选择明确的修复 release，最后更新本仓库的 SHA。
- 不得静默混用不同 release 线的代码。比较或回移时明确来源 release/commit 与目标 release/commit。

### 标准升级步骤

每次 `clash-lib` 升级单独作为可审查的迁移步骤：

1. 从当前 manifest 和 lockfile 记录旧 SHA，核实对应旧 release；无法确认时明确说明，不能猜测版本号。
2. 选择目标 release，向远端查询 tag，解析并记录完整 commit SHA。
3. 检查目标 SHA 的 `clash-lib` 源码及与旧版本相关的 API、feature 和行为差异，确认本项目所需能力确实存在。
4. 将 `uniffi/chimera-ffi/Cargo.toml` 中的 `clash-lib.rev` 更新为该 SHA。
5. 定向更新 `uniffi/Cargo.lock`，检查所有 `Chimera_Client` 来源与目标 SHA 一致，避免无关依赖更新。
6. 先运行相关 Cargo 检查和测试，再检查绑定并做最小相关 Gradle/Android 验证。
7. 在变更说明中记录旧、新 release/SHA、兼容性变化和验证结果。验证通过后，依赖升级应作为独立提交；实际创建提交时遵循任务要求。

## 参考项目迁移

按任务指定的参考项目和修订比较源码，先核实其位置及版本，不假设外部目录必然存在。保留 Chimera 本地包名和品牌命名，不因参考项目命名不同而批量替换。

如果 `clash-android` 需要当前锁定的 `Chimera_Client` release 不具备的 API 或行为，停止该迁移步骤，报告参考实现需求、当前依赖差异及受影响功能；不得猜测兼容性或切换浮动依赖。需要核心修复时，遵循上述 release-first 流程。

### CMFA UI 对齐规范

`app/src/main/java/rs/chimera/android/ui/metacubex/` 及其 XML、主题和 drawable 资源中的用户可见界面，必须尽可能像素级贴近本地 `ref/ClashMetaForAndroid` 对应界面，而不只是借鉴大致风格。实施或审查这套 UI 前，先阅读 [应用架构中的 CMFA UI 对齐规范](ARCHITECTURE.md)，核实并记录参考仓库修订；不得静默更换视觉基准。

保留 Chimera 的应用名称、品牌标识和后端边界；UI 对齐不能复制或绕过业务后端。无法一比一呈现的功能或平台差异必须尽量缩小，并在变更说明中列出原因和影响。验收需提供相同或尽可能一致的设备条件下对应页面/状态的参考与实现截图比对；仅通过编译或确认页面可以打开，不算完成视觉对齐。

## UniFFI 绑定

- 已跟踪的 Kotlin 绑定为 `core/src/main/java/uniffi/chimera_ffi/chimera_ffi.kt`。修改 Rust 导出接口后，通过 `just generate-bindings` 重新生成，不手改生成文件来掩盖接口差异。
- 使用 `just check-bindings` 验证已提交绑定与 Rust 导出接口一致；它会生成临时绑定并调用仓库中的比较脚本。
- 两个命令都会先构建 arm64 Android 原生库，需要 Android NDK、对应 Rust target 和 `cargo-ndk`。
- Gradle 会集成 Rust 原生库构建，但当前 `core/build.gradle.kts` 未配置自动重新生成 Kotlin 绑定；不能用 APK 编译通过代替绑定一致性检查。

## 构建与验证

工具链版本以两个 `rust-toolchain.toml`、Gradle 配置和 CI 为准；修改 Rust 工具链时保持根目录与 `uniffi/` 配置一致。Android 环境要求见 README，并与构建脚本核对。

以下命令从仓库根目录执行，按修改范围选择最小相关检查：

| 修改范围 | 验证命令 |
| --- | --- |
| Rust 格式 | `cargo fmt --manifest-path uniffi/Cargo.toml --all -- --check` |
| Rust 编译 | `RUSTC_BOOTSTRAP=1 cargo check --manifest-path uniffi/Cargo.toml -p chimera-ffi --locked` |
| Rust 测试 | `RUSTC_BOOTSTRAP=1 cargo test --manifest-path uniffi/Cargo.toml --workspace --locked` |
| Rust Clippy（CI 检查） | `RUSTC_BOOTSTRAP=1 cargo clippy --manifest-path uniffi/Cargo.toml --workspace --all-targets --locked -- -D warnings` |
| FFI 接口一致性 | `just check-bindings` |
| Kotlin 编译 | `./gradlew :app:compileDebugKotlin` |
| Android 单元测试 | `./gradlew :app:testDebugUnitTest` |
| Android 格式与静态检查 | `./gradlew ktlintCheck lintDebug` |
| Debug APK | `./gradlew :app:assembleDebug` |
| 设备或模拟器测试 | `./gradlew :app:connectedDebugAndroidTest`（需匹配 ABI 的设备） |

- 普通 Debug 默认仅构建 `x86_64`。需要 ARM 真机或完整 ABI 验证时使用 `./gradlew :app:assembleDebug -Pchimera.fullAbi=true`；该选项同时使 Rust 使用 release profile。Release 任务默认启用完整 ABI，实际行为以 `app/` 和 `core/` 的构建脚本为准。
- 常规 Cargo 编译和测试使用 `--locked`；仅在有意升级依赖时更新 lockfile，不为绕过失败而删除锁文件。
- 对行为修复补充能复现问题的针对性测试，优先使用现有 JVM、instrumentation 或 Rust 测试位置。纯文档修改检查内容、路径和 `git diff --check`，不要求运行完整 Android 构建。
- 缺少工具链、设备或依赖下载失败时，明确报告未完成的验证及原因。编译成功不能代替协议运行、VPN 连通性或真机行为验证。

## Code Review Rules

- 将正确性、生命周期、并发、持久化和兼容性问题作为主要审查目标；纯格式问题交给现有 formatter、lint 和 CI。
- backend 或共享状态变更：确认 Compose 与 `ui/metacubex` 两套界面没有出现行为分叉、重复状态源或过期调用方。
- VPN 或异步流程变更：确认重复命令、取消、异常退出和失败恢复不会留下错误运行状态，也不会发生竞态或资源泄漏。
- 配置与持久化变更：确认旧数据的读取和迁移路径；任何用户数据丢失、静默重置或不兼容默认值都应作为问题指出。
- Rust FFI 变更：确认导出接口、UniFFI 绑定、Kotlin 调用方和线程/错误语义同步；生成文件的手工修改不能作为修复方案。
- `clash-lib` 依赖变更：确认 release、完整 SHA、feature 列表和 lockfile 一致；发现浮动引用、跨 release 混用或未经验证的 API 假设时必须指出并停止该迁移步骤。
- 每条审查意见说明触发场景、实际影响和安全修复方向；没有可复现影响的偏好不作为缺陷报告。

## AI 任务交接与复盘

- 交接报告必须写清：完成了什么、具体修改文件、验证命令与结果、失败/未验证范围、
  Android 真机或模拟器验证状态，以及需人工判断的安全与兼容性风险。
- 独立 Code Review 必须优先检查用户数据保全、双 UI 一致性、VPN 生命周期、
  核心依赖 SHA/feature 与 UniFFI 绑定是否同步，使用
  `docs/ai-review-checklist.md` 记录可复现发现；重大问题不得用“CI 绿灯”掩盖。
- 已确认事故、重复返工、CI 漏检的证据写入 `LESSONS.md`；
  原因未知或仅为建议的事项放在 Issue，不能记为已证实的失败经验。

## 交付与提交

若改变用户可见行为或构建流程，同步更新相关文档。

创建提交时遵守 `LLM.md` 的 `Assisted-by` 要求，包括实质性 AI 生成的文档改动。按实际使用的代理和模型填写，不编造工具或模型信息。
