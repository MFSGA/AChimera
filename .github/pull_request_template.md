## 目标与验收
<!-- Issue 关联、可观察结果、非目标 -->

## 改动与架构边界
<!-- 两套 UI、共享 backend、设置/配置、VPN 服务、Rust/UniFFI 等涉及哪些调用链？ -->

## 最高风险等级（L0–L3）
- [ ] L0：非行为性文档
- [ ] L1：低风险局部改动
- [ ] L2：VPN / 用户数据 / backend / Rust-FFI / 依赖
- [ ] L3：治理、CI/发布、凭据/签名、权限、构建策略

原因及需要的人工审批：

## 实际验证记录
| 检查 / 命令 | 环境与版本 | 结果（通过 / 失败 / 未运行） | 证据或未运行原因 |
| --- | --- | --- | --- |
| 相关 JVM / Gradle 测试 | | | |
| Rust fmt / Clippy / tests（如适用） | | | |
| `just check-bindings`（FFI 如适用） | | | |
| Android 真机/模拟器、ABI、VPN 行为（如适用） | | | |
| CI 与其他检查 | | | |

## 风险及回滚
<!-- 用户数据兼容、权限撤销/进程恢复、失败恢复；重要风险和未验证项 -->

## 独立 Code Review
<!-- 按 docs/ai-review-checklist.md；谁独立审查、阻断项及其解决/未解决状态 -->

## AI 贡献透明度
<!-- 如此 PR 中的提交涉及 AI，按 LLM.md 在提交消息末尾添加真实 Assisted-by 标签。 -->

## 发布授权
<!-- 本 PR 不授权修改 tag、Nightly、稳定发布、签名或 Secrets。 -->
