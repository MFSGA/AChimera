# AChimera GitHub Actions 和 AI 研发门禁

本文件描述**目前实际存在**的工作流和后续 AI 自动化接入原则。工作规程见 [AI 研发治理规范](../docs/ai-development-governance.md)，AI Reviewer 检查项见 [审查清单](../docs/ai-review-checklist.md)。行为以实际 `.github/workflows/*.yml` 为准；仅凭本文不表示已设置 GitHub Rulesets。

## 当前工作流

| 工作流 | 真实触发方式 | 作用与权限 |
| --- | --- | --- |
| `ci.yml` | `main` push / PR | 只读 Token；核验 Actions 引用、Cargo dependency policy、Rust fmt/Clippy/tests、UniFFI 绑定、Gradle ktlint/JVM/lint、Debug APK |
| `nightly.yml` | 每日 19:17 UTC / 手工 dispatch | 构建全 ABI Debug APK，上传 Artifact，使用 `contents: write` 更新滚动 `nightly` GitHub 预发布 |
| `release.yml` | `v*` 标签 push / 手工 dispatch | 手工 dispatch 可更改版本并推 tag；发布任务使用签名材料（若配置）构建 release APK，检查 Gradle 测试/lint，生成依赖清单、校验和并发布 GitHub Release |

### 不可误判

- CI 通过 **不等于** Android 设备端 VPN、权限撤销、后台恢复、双 UI 一致性、ARM ABI 运行成功。
- Nightly 是**已有的自动预发布流水线**，不是夜间 AI 复盘；Nightly 具有写入 GitHub Release 的权限。
- Release 接收 `v*` 标签 push；Codex 不得未经授权执行 tagging 或 workflow dispatch，也不得把普通合并当作稳定发布授权。
- `release.yml` 的签名材料缺失时可以打包 unsigned APK；不能简单假定每一个稳定发布资产都已签名。修改此流程须 L3 审查。
- CI 使用 `contents: read`；新建有写权限的 AI 工作流必须隔离不可信 PR 内容、限制 GitHub Token/Secrets。
- 现有工作流不含独立的 Codex Reviewer / Issue coding / AI retrospective，不能在文档中宣称它们已自动执行。

## 接入 AI 的实施原则

1. **先守住当前 CI**：不得为让 Agent 更快通过而删除 Rust/UniFFI/Android 检查、降低 Actions Pin 或依赖审计。
2. **再做独立 Reviewer**：只读或低权限，单独上下文核对实际 diff 与结果；不能以 AI 自述代替通过证明。
3. **保护 `main`**：由维护者检查/配置 GitHub Rulesets、required checks、审批与权限边界；L2/L3 须人工批准。
4. **受控自动合并**：仅在测试过风险分级/权限模型后，让满足独立审批条件的 L0/L1 变更获得自动合并候选资格；不得靠可伪造的 Issue/PR 标签自行授予权限。
5. **AI 复盘与 Nightly 隔离**：若以后新增夜间复盘，应使用只读独立工作流，把结果作为待审查建议，不写签名材料、不修改发布状态，也不自动批准自己提出的治理变更。

## 修改工作流的验证

- 检查改动前后触发条件、所用 secrets、job 级 permissions、第三方 Action 的固定引用、并发和错误传播。
- 运行 `python3 .github/check_action_pins.py` 和改动覆盖的最小测试；需要的 Android 设备/Release 签名验证另行实施并记录。
- 保留现有流程的明确用途；任何新增自动打 tag、发布或修改 `main` 的权限都必须是 L3 人工批准的独立更改。
