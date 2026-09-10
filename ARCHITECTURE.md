# AChimera 应用架构

AChimera 在同一应用中提供 Compose / Watfaq 和 MetaCubeX 风格的两套界面。两套界面共享配置、设置、VPN 服务与 Rust 核心；切换界面不创建另一份代理运行时。导航、布局、权限弹窗和提示方式可以各自实现，业务规则通过共享 backend 执行。

## 调用边界

```text
Compose 页面 → ViewModel ─┐
                         ├→ ChimeraBackend → 配置管理 / 控制器适配 / VPN 命令入口
Meta Activity / Design ──┘                         ↓
                                              TunService
                                                  ↓
                                           Rust / UniFFI 核心
```

`ChimeraBackend` 是应用业务入口。`BackendProvider` 负责应用级组装，向实现传入 application context 和共享设置仓库。业务模型使用 `ProxyMode`、`ProxyGroupSnapshot`、`MemoryInfo`、`ProfileDownloadProgress` 等应用类型；UniFFI 数据在控制器和下载适配边界转换，界面不依赖生成类型。

## 设置的数据源

`SettingsRepository` 是运行设置的统一读写入口，`RuntimeSettingsReader` 负责默认值、旧端口格式兼容和应用集合复制。`PreferenceSettingsRepository` 保留既有 SharedPreferences 文件与键，通过 `StateFlow<RuntimeSettings>` 发布保存结果，并监听旧入口或外部偏好修改。

Compose 设置 ViewModel 和 Meta 设置页面观察同一个 backend 设置流。VPN 启动时从同一仓库获取快照。应用选择页面允许保留未保存的编辑草稿，草稿不等同于已保存设置。

backend 串行执行“保存设置、按需重启”。设置流表示**已保存的用户设置**，并不承诺运行核心已经应用成功：重启失败时保留已保存值并报告操作错误。运行核心仍在服务启动时应用快照。外观、语言和界面选择继续由 `AppPreferences` 管理。

## 初始化与线程

`BackendInitialization` 将配置导入、备份、删除及下载恢复放在 backend 的 IO 作用域。依赖配置的业务入口和服务启动等待同一个恢复任务；取消单个等待者不会取消共享恢复。初始化后的自动更新调度在恢复完成后执行。

配置业务入口、启停和设置更新负责切换到 IO dispatcher，因此界面可以从主线程调用 suspend 接口。这里不表示构造阶段完全没有 Android 系统访问：获取偏好对象等轻量组装仍在创建 backend 时进行。

## VPN 命令、状态和资源

- `VpnCommandGate`：统一两个界面的启动、停止和重启并发规则。重复启动不重复分发，停止期间不允许启动，冲突的重启明确失败。
- `BackendRuntimeState`：通过一个 `VpnRuntimeStatus` 快照同时发布服务状态和错误。旧状态与错误流是只读投影，不再各自保存一份可变状态；需要关联读取时使用完整快照。
- `VpnDesiredStateStore`：持久化用户期望，用于进程或系统恢复；不能将它当作核心正在运行的证据。
- `VpnRuntimeRegistry`：连接当前服务控制实例与 FFI 回调，保存进程内启动请求。
- `TunService`、`VpnLifecycleGate`：负责 Android 生命周期、启动任务取消、TUN 资源释放和 Rust 启停。命令互斥不替代服务自身的资源锁。

服务回调和 FFI 异常仍然向共享状态存储发布结果。本实现尚未把 Android 与 Rust 生命周期合并为一个跨语言状态机；任何进一步调整都需要验证停止、重启、权限撤销、系统恢复和核心异常退出的交错情况。

## 验证

JVM 测试覆盖初始化等待与取消、VPN 命令并发、状态投影、设置兼容解析和既有配置事务策略。Android instrumentation 测试用于两套界面、偏好存储、VPN 前置条件及原生桥接；测试代码编译通过与设备上执行通过必须分别报告。

涉及界面或 VPN 生命周期的改动，设备回归应覆盖：两套界面切换后设置一致、应用过滤草稿保存、重复启停、运行中修改设置、权限拒绝或撤销，以及后台恢复。保留既有核心 SHA、feature 和锁文件，核心升级遵循 AGENTS.md 的独立发布迁移流程。
