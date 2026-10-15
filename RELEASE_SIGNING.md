# Release APK 签名

Release APK 不能在缺少发布密钥时打包。本地 `packageRelease`/`assembleRelease` 和 Release Bundle 任务会先检查签名环境变量及 keystore 文件；缺失时直接失败。GitHub Release 会逐个验签，并要求 APK 的 SHA-256 证书指纹匹配仓库中的 [`SIGNING_CERT_SHA256.txt`](SIGNING_CERT_SHA256.txt)，随后把该指纹一并发布，方便用户跨版本核对。手动触发版本更新前，工作流会检查 Secrets、解码 keystore、校验密码和别名、比对 keystore 证书指纹，并用临时 JAR 验证签名密钥可用；任一预检失败时都不会创建版本提交或 tag。正式 Release 构建使用临时 runner 上的 keystore，验签后清理。

该指纹证明 APK 使用同一签名证书，代表签名密钥的连续性；它本身不认证现实中的个人或公司身份。用户应从官方仓库/发布页取得可信指纹，再用 Android SDK 的 `apksigner verify --print-certs app.apk` 检查下载的 APK。正式更换签名证书需要维护者审查并更新固定指纹。

## 配置 GitHub Actions

在仓库的 **Settings → Secrets and variables → Actions** 配置以下 Repository Secrets：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 发布 keystore 文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | keystore 中发布密钥的别名 |
| `KEY_PASSWORD` | 发布密钥密码 |

不要把 keystore、私钥、密码或 Base64 内容提交到 Git，也不要通过聊天发送私钥。请在可信的本机环境中对已有 keystore 做 Base64 编码，并只把编码结果保存为 `KEYSTORE_BASE64` Secret。Linux 示例：

```sh
base64 -w 0 release.keystore
```

macOS 示例：

```sh
base64 < release.keystore | tr -d '\n'
```

## Nightly 与本地构建

Nightly 构建的是 Debug APK，使用 Android Gradle Plugin 的默认 debug key，不是稳定版发布密钥。它仅用于测试，不代表正式 Release 的签名身份。

本地 Release 构建需通过 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS` 和 `KEY_PASSWORD` 环境变量配置签名。请通过本机安全的密钥管理器仅向 Release 构建注入这些值，不要把 keystore 或密码写进版本控制、shell 历史或聊天。检测到任一签名变量时，该次 Gradle 调用会禁用配置缓存，避免签名配置进入缓存；变量未设置时，Debug 构建继续使用 Android 默认 debug key。GitHub Actions 使用同名 Secrets；密钥缺失、无效或最终 APK 验签失败都会阻止稳定版产物上传。
