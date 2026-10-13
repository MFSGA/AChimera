# AChimera — Codex 独立 Code Review 检查清单

> 使用**与开发者不同的 Codex 会话/上下文**审查真实 PR diff、相关代码、测试输出和实际依赖版本。开发者声明与 AI 自评只是线索；对未运行的设备/网络测试不得表示通过。

## 重要风险（存在则阻塞合并）

- [ ] 任务范围、风险 L0–L3、验收标准与代码修改一致，无无关重构、依赖升级、提交其他用户的工作。
- [ ] 两套界面（Compose / Watfaq、MetaCubeX）共享 `ChimeraBackend`、设置与 VPN 状态，未产生业务分叉、重复持久化源或失效调用。
- [ ] 配置/设置：导入、导出、删除、下载、备份恢复和兼容解析不会无声丢失旧数据；保存状态与运行应用状态不混淆。
- [ ] VPN 生命周期：重复启动/停止/重启、Service 销毁、用户取消、权限拒绝/撤销、进程死亡及后台恢复的竞态与 TUN 资源清理可解释且有测试。
- [ ] 异步：共享初始化、协程取消、回调和作用域所有权不会产生重复任务、过期状态、阻塞主线程或内存泄漏。
- [ ] 安全：外部 Intent/URI、权限、配置凭据、日志、KeyStore/签名、备份不会泄漏秘密或绕过用户授权。
- [ ] Rust / UniFFI：导出接口、Kotlin 绑定、调用方、错误/线程模型同步；生成文件不能手改充当修复；必要时 `just check-bindings` 实测。
- [ ] 核心依赖：`Chimera_Client` release、完整固定 SHA、`uniffi/Cargo.lock`、features、`default-features = false` 均一致；不存在浮动分支引用或跨 release 混用。
- [ ] 验证真实有效：针对性 JVM / Rust / Gradle / NDK、设备/模拟器测试按变更范围执行，结果可复核；未做设备 VPN 测试时明确局限。
- [ ] CI / 治理：风险 L2/L3 有人工批准，`.github/**`、`AGENTS.md`、`LLM.md`、`docs/ai-*`、签名/版本/发布相关修改未绕过受控流程；PR/Issue 中不可信内容未获得写权限。
- [ ] 提交遵循 `LLM.md` 的真实 `Assisted-by` 记录要求；未伪造 Agent/模型或额外工具。

## 严重度和输出格式

- **BLOCKER/HIGH**：会导致数据损失、权限/敏感信息问题、VPN 生命周期错误、绑定不一致、依赖不可复现或质量门禁绕过；应阻止合并。
- **MEDIUM**：可复现的功能回归、测试覆盖关键空缺；修复或提供明确审核结论。
- **LOW**：非阻断性改进建议，不把主观偏好当成缺陷。

每一项发现包含：`[SEVERITY] 标题`、文件与行、触发场景、影响、可复核的测试/证据、最小修复建议。无发现时只能说明“审查范围内未发现阻断项”，不能保证无缺陷。

```text
Risk: L0/L1/L2/L3
Decision: REQUEST_CHANGES / COMMENT / APPROVE_WITH_EVIDENCE
Reviewed paths and behavioral flows:
Findings: [severity, file:line, reproduction/evidence, impact, fix]
Actual commands/devices checked + results:
Skipped / blocked checks:
Open risks and required human approval:
AI attribution verification:
```
