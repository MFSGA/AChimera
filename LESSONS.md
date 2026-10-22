# AChimera Engineering Lessons

本文是人类维护者和 AI Agent 共同维护的**已验证工程经验库**。它补充 `AGENTS.md` 和 `docs/ai-development-governance.md`，不是尚未实现的功能清单，也不是自动改写质量门禁的依据。

## 收录规则

- 仅收录有明确记录的真实失败（CI、测试、Issue、PR、事故或本地可复现日志）；推测和待做工作记录在 Issue。
- 每次事故需写出可验证的现象、根因证据、修复方式、预防措施、复验命令/设备；“跑过构建”不能等同运行在设备成功。
- 不记录密钥、签名文件、完整用户配置、真实节点凭据、个人信息；敏感错误日志必须先脱敏。
- 新的 Agent 应在修复相关模块前阅读对应条目。独立 Reviewer 检查本次变更是否再次触发已有问题。
- 当问题再次出现，优先加回归测试/CI；三次独立事故后提议把教训升级到强制规范，须由维护者审查。高危数据丢失或凭据泄露可以首次就要求强制规则。
- 规范变更遵循 L3 人工审批；Agent 不得删除不利证据、伪造验证记录或修改 CI 以抹平失败。

## 已确认记录索引

| 编号 | 日期 | 范围 | 描述 | 证据 | 状态 |
| --- | --- | --- | --- | --- | --- |
| LESSON-20261017-001 | 2026-10-17 | Android instrumentation tests | CI 测试断言与测试上下文不匹配 | [失败 run 38995671505](https://github.com/MFSGA/AChimera/actions/runs/38995671505)、[修复验证 run 38996990027](https://github.com/MFSGA/AChimera/actions/runs/38996990027) | Verified |
| LESSON-20261022-001 | 2026-10-22 | Android instrumentation tests | 本机 instrumentation 后 debug 应用与配置不可用 | `connectedDebugAndroidTest` 后 `adb shell pm path rs.chimera.android.dev` 无输出；随后通过 `installDebug` 和应用内重新导入恢复 | Mitigated |
| LESSON-20261022-002 | 2026-10-22 | CI Android instrumentation | Headless AVD 测试期间窗口失焦 | [失败 run 39697174637](https://github.com/MFSGA/AChimera/actions/runs/39697174637)、[修复验证 run 39700416292](https://github.com/MFSGA/AChimera/actions/runs/39700416292) | Verified |
| LESSON-20261022-003 | 2026-10-22 | CI Android SDK setup | Emulator SDK 下载遇到 Premature EOF，后续检查未运行 | [失败 run 39699535693](https://github.com/MFSGA/AChimera/actions/runs/39699535693)、[修复验证 run 39700416292](https://github.com/MFSGA/AChimera/actions/runs/39700416292) | Verified |

## 条目模板

```markdown
### LESSON-YYYYMMDD-001: 问题标题

- 发生/确认时间（含时区）：
- 模块：Compose / Meta UI / backend / profile / VPN / Rust-FFI / CI / Release / 其他
- 严重度：Critical / High / Medium / Low
- 状态：Open / Mitigated / Verified
- Issue、PR、commit、CI 运行或复现证据：
- 环境：Android 版本、ABI、设备/模拟器、clash-lib release+SHA（如适用）
- 预期行为：
- 实际失败与可复现步骤：
- 根本原因及证据（仍存疑点需注明）：
- 修复改动及其归属：
- 预防措施：回归测试、审查项、静态检查或流程约束
- 复验命令、场景和真实结果：
- 关联独立事件（用于累计出现次数）：
- 是否提议升级治理规范及审批链接：
```

## 已确认经验

### LESSON-20261017-001: Android instrumentation 断言与测试上下文不匹配

- 发生/确认时间（含时区）：2026-10-17，Asia/Shanghai
- 模块：Android instrumentation tests
- 严重度：Medium
- 状态：Verified
- Issue、PR、commit、CI 运行或复现证据：[失败 CI run 38995671505](https://github.com/MFSGA/AChimera/actions/runs/38995671505)，修复提交 `2b9293f2`，完整通过的 [CI run 38996990027](https://github.com/MFSGA/AChimera/actions/runs/38996990027)
- 环境：GitHub Actions `ubuntu-24.04`，Android API 36 x86_64 模拟器
- 预期行为：导航测试验证设置页内容；Material XML 布局在应用主题下 inflate；无来源指纹的旧自动更新状态由存储层保持 fail-closed。
- 实际失败与可复现步骤：CI run 38995671505 中，设置页测试用通用 `settings_title` 匹配到多个节点；Material 布局测试用未应用 Material 主题的 context inflate；存储层测试则错误地期待它为缺少来源指纹的旧状态自动绑定来源。
- 根本原因及证据：失败测试日志确认前两项分别是非唯一 Compose 文本匹配和缺少主题属性；第三项断言与存储层职责不符，存储层无法验证当前 profile 来源。来源核验及兼容绑定应由 `ProfileCatalogReader` 在比较当前快照后完成，存储层必须拒绝推断来源。
- 修复改动及其归属：测试改为断言唯一的 `settings_general` 内容、使用 `Theme_Chimera_MetaCubeX` 包装布局 context，并验证 store 层不会绑定缺指纹的旧状态；未改动应用运行逻辑。
- 预防措施：UI 导航测试选择唯一页面内容；inflate Material 布局的测试显式使用应用主题；旧数据绑定只在具备来源快照校验的 catalog 层测试，存储层维持 fail-closed。
- 复验命令、场景和真实结果：本地 `./gradlew :app:ktlintCheck :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests 'rs.chimera.android.backend.ProfileAutoUpdateStateWritePolicyTest'` 和 `git diff --check` 通过；CI run 38996990027 中 Rust、UniFFI、Android quality、Debug APK、KVM 加速、模拟器启动及完整 instrumentation tests 全部通过。未在本地设备运行 instrumentation tests。
- 关联独立事件（用于累计出现次数）：无
- 是否提议升级治理规范及审批链接：否

### LESSON-20261022-001: 本机 instrumentation 后 debug 应用与配置不可用

- 发生/确认时间（含时区）：2026-10-22，Asia/Shanghai
- 模块：Android instrumentation tests / 本机模拟器配置数据
- 严重度：Medium（仅本机 debug 配置受影响，已重新导入恢复；未影响仓库文件或远端数据）
- 状态：Mitigated
- Issue、PR、commit、CI 运行或复现证据：运行 `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=rs.chimera.android.AppUiNavigationInstrumentedTest#proxyPagerPagesFillTheViewport` 后，`adb shell pm path rs.chimera.android.dev` 没有返回已安装 APK 路径；之后 `./gradlew :app:installDebug` 重新安装应用。
- 环境：本机 Android API 36 x86_64 模拟器 `emulator-5554`，debug application id `rs.chimera.android.dev`。
- 预期行为：instrumentation 验证不应被假定为保留该模拟器中 debug 应用的安装和私有数据；若测试期间应用被卸载，必须先确认测试设备是可丢弃环境或有可恢复备份。
- 实际失败与可复现步骤：模拟器 debug 应用原先保存了用于 UI 验证的 profile。执行上述 instrumentation 命令后，debug 包不再可由 Package Manager 定位，应用私有数据随之不可用；profile 需要在重新安装后通过应用 UI 重新导入。完整配置、订阅 URL 和节点凭据不在此记录。
- 根本原因及证据（仍存疑点需注明）：直接证据表明 instrumentation 运行结束后目标 debug package 已卸载，且随后 `installDebug` 可重新安装。未隔离 Gradle/Android Test 哪个内部清理步骤触发卸载，因此不推断更具体的实现原因；已确认的流程缺口是把有本地配置的模拟器误当作运行 instrumentation 后仍保留 app 数据的环境。
- 修复改动及其归属：重新安装 debug APK，并在应用内恢复本机 profile；重新导入时关闭自动更新。没有停止参考应用 VPN，也没有启动 Chimera VPN。
- 预防措施：今后优先用独立、可丢弃的 AVD 跑 instrumentation；若只能使用保存了本机配置的模拟器，执行前先准备并验证可恢复备份，或明确接受重置；测试后检查 package 与数据状态，再继续截图/手动验收。不得把用户配置写入仓库；CMFA 对比截图继续保存在仓库外。
- 复验命令、场景和真实结果：`./gradlew :app:testDebugUnitTest --tests 'rs.chimera.android.ui.metacubex.activity.ProxyRequestLoopTest' --tests 'rs.chimera.android.ui.metacubex.activity.ProxySelectionActionTest' --tests 'rs.chimera.android.ui.metacubex.activity.ProxyModeSwitchActionTest' --tests 'rs.chimera.android.ui.metacubex.design.ProxySelectionAnimationTrackerTest' --tests 'rs.chimera.android.ui.metacubex.design.ProxySelectionColorTransitionTest' --tests 'rs.chimera.android.ui.metacubex.design.ProxyDisplayPolicyTest'` 通过；重新安装/导入后确认主页显示 `CMFA_UI_Parity Activated`、服务为 `Stopped`，`vpn_management` 仍仅显示参考应用持有 VPN。没有为避免再次清除本机配置而重跑 instrumentation；截图视觉对比使用之前保存在仓库外的同设备参考/实现截图。
- 关联独立事件（用于累计出现次数）：无
- 是否提议升级治理规范及审批链接：否

### LESSON-20261022-002: Headless CI AVD 在 instrumentation 期间窗口失焦

- 发生/确认时间（含时区）：2026-10-22，Asia/Shanghai
- 模块：CI / Android instrumentation tests
- 严重度：Medium
- 状态：Verified
- Issue、PR、commit、CI 运行或复现证据：[失败 CI run 39697174637](https://github.com/MFSGA/AChimera/actions/runs/39697174637)，完整通过的[修复验证 CI run 39700416292](https://github.com/MFSGA/AChimera/actions/runs/39700416292)
- 环境：GitHub Actions `ubuntu-24.04`，Android API 36 x86_64 headless AVD
- 预期行为：模拟器运行 instrumentation tests 时，应用窗口保持唤醒并获得输入焦点。
- 实际失败与可复现步骤：run 39697174637 的 Android 构建和前置检查通过；instrumentation 中四项测试报告 `RootViewWithoutFocusException`（`has-window-focus=false`），代理页横向滑动测试仍停留在第 0 页。
- 根本原因及证据（仍存疑点需注明）：CI 的 AVD 启动步骤此前未设置保持唤醒或显式唤醒屏幕；失焦与滑动失败与显示器进入非交互状态相符。但失败日志没有记录当时的电源/屏幕状态，不能断言设备确实已休眠。
- 修复改动及其归属：在 CI AVD 完成启动后、运行 instrumentation 前执行 `svc power stayon true` 和 `input keyevent KEYCODE_WAKEUP`；未更改应用行为、测试断言、检查项或权限。
- 预防措施：headless UI 测试显式保持设备唤醒并唤醒屏幕；若复验仍失败，应从设备电源与前台窗口状态继续诊断，而不是移除失效测试。
- 复验命令、场景和真实结果：`ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=rs.chimera.android.AppUiNavigationInstrumentedTest` 在 API 36 x86_64 `CMFA_ProxyParityAPI36` 临时 AVD 上通过，26/26 项通过；`svc power stayon true` 后确认 `stay_on_while_plugged_in=15` 且 `mWakefulness=Awake`。GitHub CI run 39700416292 中 API 36 x86_64 模拟器上的全部 88 项 instrumentation tests 及其余 CI 检查通过。
- 关联独立事件（用于累计出现次数）：无
- 是否提议升级治理规范及审批链接：否

### LESSON-20261022-003: CI Android SDK 下载中断且无重试

- 发生/确认时间（含时区）：2026-10-22，Asia/Shanghai
- 模块：CI / Android SDK
- 严重度：Low（CI 预备步骤失败，未运行代码检查；没有应用数据影响）
- 状态：Verified
- Issue、PR、commit、CI 运行或复现证据：[失败 CI run 39699535693](https://github.com/MFSGA/AChimera/actions/runs/39699535693)，完整通过的[修复验证 CI run 39700416292](https://github.com/MFSGA/AChimera/actions/runs/39700416292)
- 环境：GitHub Actions `ubuntu-24.04`，Android SDK command-line tools
- 预期行为：Android SDK 包暂时安装失败时可有限重试；持续失败时仍让 CI 明确失败。
- 实际失败与可复现步骤：在 `Configure Android SDK` 中安装 SDK 包时，Android Emulator 包下载至约 21% 后报告 `Premature EOF` 并以退出码 1 结束；Rust/Android 质量检查和测试步骤均未运行。
- 根本原因及证据（仍存疑点需注明）：下载流意外提前结束是日志中直接确认的现象。缺少失败后重试是工作流可恢复性缺口；底层网络为何中断尚不能由该日志确认。
- 修复改动及其归属：在 CI SDK 安装脚本中为整组固定 SDK 包安装增加最多 3 次尝试，失败间隔为 15 秒、30 秒；最终仍失败则返回错误，不跳过后续步骤。
- 预防措施：对 SDK 包安装失败有限重试并保留失败门禁；完整 GitHub CI 验证重试步骤及其后的全部质量检查和 instrumentation tests。
- 复验命令、场景和真实结果：`python3 .github/check_action_pins.py`、`git diff --check`、抽取后的 Bash `bash -n` 语法检查及重试成功/最终失败的本地故障注入均通过。GitHub CI run 39700416292 中 SDK 配置在首次尝试成功，Rust 格式、Clippy、测试、UniFFI 绑定、Android quality、Debug APK、KVM、API 36 x86_64 模拟器和全部 88 项 instrumentation tests 均通过；实际重试路径由本地故障注入覆盖。
- 关联独立事件（用于累计出现次数）：无
- 是否提议升级治理规范及审批链接：否
