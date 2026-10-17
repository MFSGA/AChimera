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
