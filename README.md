# Offline Vault（离线密码库）

一款**完全离线**的 Android 密码管理器，使用 Kotlin + Jetpack Compose 构建。没有网络权限、没有云端、没有服务器——所有数据都加密后保存在本机。

设计灵感来自 1Password、Bitwarden 和 Apple Passwords，但坚持永不离开你的手机。

---

## 亮点功能

- 🔒 密码、备注**以及标题/用户名/网址/标签等元数据**均采用 **AES-256-GCM** 加密，数据库里不存任何明文
- 🔑 解锁凭据通过 **PBKDF2-HMAC-SHA256**（30 万次迭代，兼顾安全与老设备解锁速度）派生密钥——迭代次数按密码库分别存储，可以随时上调而不会锁死旧密码库
- 🔢 解锁凭据**类型可选**：4 位 PIN、6 位 PIN 或普通密码
- 🧬 **信封加密** —— 随机生成的数据加密密钥（DEK）分别被主密码、密保答案、12 个助记词各包裹一份，任一方式都能恢复数据
- 🧾 **12 个助记词恢复**：仅展示一次、不明文存储，作为忘记主密码且忘记密保答案时的最终恢复手段（设置时需随机抽词校验）
- 👆 通过 Android Keystore + BiometricPrompt(`CryptoObject`) 实现**指纹解锁**（可选）
- 🧮 **强密码生成器**（长度 8–32，可配置字符集）
- 📊 **密码强度检测**（0–100 分，弱密码与连续字符检测）
- 📥 支持**导入**本应用的加密 JSON 备份或 **Chrome 密码 CSV**
- 📤 支持**导出**加密 JSON（推荐）或明文 CSV（带明确风险提示）
- 🧩 **Android 自动填充**服务：支持**原生应用**（按包名）与**网页**（按域名，含主流浏览器兼容模式）严格匹配；并能在你输入新账号时**主动询问是否保存**到密码库
- 🛡 后台**自动锁定**、可配置的 **FLAG_SECURE 防截屏**、**剪贴板自动清空**，以及多次解锁失败后的指数退避暴力破解延迟
- 🎨 精致的**浅色** Material 3 界面（白色背景配克制的蓝色点缀）

清单中**没有 `android.permission.INTERNET`**。本应用在物理上无法发起任何网络请求。

---

## 构建与运行

### 方式 A —— Android Studio（推荐）
1. 打开 Android Studio（Hedgehog / Iguana 或更新版本，JDK 17）。
2. **File → Open**，选择 `OfflineVault` 文件夹。
3. 等待 Gradle 同步。
4. 在设备或模拟器（minSdk 26 / Android 8.0+）上运行 `app` 配置。

### 方式 B —— 命令行
```bash
cd OfflineVault
./gradlew assembleDebug                  # 构建调试版 APK
./gradlew installDebug                   # 安装到已连接的设备
```

APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 通过 GitHub Releases 更新

应用的“设置 → 检查更新”会使用系统浏览器打开本仓库的最新 Release 页面。应用本身仍不申请联网权限，也不会在后台检查或下载更新。

发布新版时：

1. 在 `app/build.gradle.kts` 中递增 `versionCode`，并更新 `versionName`。
2. 使用与上一版完全相同的发布密钥生成已签名 APK。
3. 创建 GitHub Release，将已签名 APK 和对应的 SHA-256 校验值作为附件上传。
4. 用户从更新页面下载 APK 后，由 Android 系统确认并覆盖安装。

Android 只允许签名证书相同且 `versionCode` 更高的 APK 覆盖安装。发布密钥和密码不得提交到仓库；首次正式发布后必须妥善离线备份该密钥。调试版 APK 使用调试证书，不能被正式发布证书签名的 APK 直接覆盖，迁移前应先导出加密备份。

### 启用自动填充（可选）
应用内**设置 → 自动填充**会显示当前是否已启用，并提供「去系统设置开启」按钮，一键跳转到：**系统设置 → 密码、密钥与自动填充 → 自动填充服务 → Offline Vault**。

- 请先至少解锁一次应用，让加密密钥进入内存；该服务只在密码库已解锁时才会**提供**凭据。
- **保存**新账号时即使处于锁定状态也不会丢失：会先在内存暂存，待你下次解锁后自动写入并提示。
- **原生应用**开箱即用；**浏览器**需要浏览器本身支持并允许第三方自动填充服务（如 Chrome 需在其设置中开启），本应用已为主流浏览器声明兼容模式。

---

## 项目结构

```
app/src/main/java/com/offlinevault/
├─ MainActivity.kt            # 单 Activity，导航、后台自动锁定、FLAG_SECURE
├─ OfflineVaultApp.kt         # Application + 手动依赖注入容器
├─ AppContainer.kt
├─ security/
│  ├─ CryptoManager.kt        # AES-256-GCM、PBKDF2、SecureRandom 原语
│  ├─ KeyManager.kt           # 初始化 / 解锁 / 找回 / 修改密码 / 助记词 / 生物识别包裹
│  ├─ KeystoreManager.kt      # 与生物识别绑定的 AndroidKeyStore 密钥
│  ├─ SessionManager.kt       # 内存中的 DEK 及解锁状态
│  ├─ MnemonicManager.kt      # 12 个助记词生成 / 校验 / 派生恢复密钥
│  ├─ UnlockThrottle.kt       # 解锁失败的指数退避延迟
│  ├─ EncryptedField.kt       # 可搜索元数据的版本化字段加密
│  ├─ CredentialType.kt       # PIN4 / PIN6 / 密码 凭据类型
│  ├─ LockGuard.kt            # 在系统选择器 / 生物识别期间抑制自动锁定
│  ├─ PasswordStrengthChecker.kt
│  └─ PasswordGenerator.kt
├─ data/
│  ├─ db/AppDatabase.kt
│  ├─ dao/ (VaultDao, PasswordDao)
│  ├─ model/ (VaultEntity, PasswordEntity)
│  ├─ repository/ (VaultRepository, PasswordRepository)   # 加解密逻辑均在此处
│  ├─ preferences/SecurityPreferences.kt                  # DataStore（密钥材料与设置）
│  └─ backup/ (BackupManager, CsvUtils, models)           # JSON/CSV 导入导出
├─ autofill/ (VaultAutofillService, OriginMatcher, PendingAutofillSave)  # 严格匹配 + 保存流程
├─ viewmodel/ (Auth, PasswordList, PasswordEdit, PasswordDetail, Settings)
├─ ui/
│  ├─ theme/ (Color, Type, Theme)
│  ├─ components/ (Components, SecureWindowEffect, PasswordGeneratorSheet)
│  └─ screens/ (Setup, Unlock, Recover, MnemonicUi, PasswordList, PasswordDetail, PasswordEdit, Settings)
└─ utils/ (BiometricHelper, ClipboardHelper, FileIo, Formatters)
```

ViewModel 从不直接访问 DAO —— 一律通过 Repository，所有加解密逻辑都封装在 Repository 内。

---

## 安全模型

```
                 PBKDF2(主密码,   masterSalt)   ──► masterKey   ──┐
随机生成的 256 位 DEK ──┬──────────────────────────────────────────┼─► AES-GCM 包裹 ─► 持久化存储
                 PBKDF2(密保答案, recoverySalt) ──► recoveryKey ──┤
                 PBKDF2(助记词,   mnemonicSalt) ──► mnemonicKey ──┘
```

- **DEK** 用于加密所有密码、备注和元数据，只在解锁状态下存在于内存中（`SessionManager`），达到用户设置的后台锁定时间后清除。
- 主密码、密保答案和助记词**永不存储**。只持久化盐值和被各自包裹的 DEK 副本（保存在 DataStore 中）。
- **解锁** = 派生 `masterKey`，尝试用 AES-GCM 解包 DEK。凭据错误会导致 GCM 认证标签校验失败，因此永远无法得到可用的密钥。
- **找回** = 用密保答案或 12 个助记词解包 DEK，再用新设置的解锁凭据重新包裹一份；找回只重置凭据、不直接进入密码库。数据完整保留；答案/助记词错误则什么也找不回。
- **生物识别** = DEK 额外用一个要求生物识别认证的 AndroidKeyStore AES 密钥包裹一份（`setUserAuthenticationRequired(true)`），通过 BiometricPrompt 的 `CryptoObject` 使用。
- **暴力破解防护** = 连续失败达阈值后按指数退避延迟下一次尝试（`UnlockThrottle`）。

---

## 说明 / 局限性

- 自动填充服务解析视图结构后**严格匹配**：网页按域名、原生应用按包名（均为精确匹配，绝不做子串匹配，避免钓鱼）。原生应用的凭据以 `androidapp://<包名>` 标识保存。该服务只在密码库已解锁时才提供凭据。
- 网页自动填充依赖浏览器把填充交给第三方服务；不支持第三方填充的浏览器或纯 WebView 无法触发，这是 Android 框架层的限制。
- 从自动填充保存新登录信息会通过系统原生的「保存」弹窗征求用户同意；同一目标且同用户名的条目会更新密码而非新建重复项。
- CSV 导出本质上是明文，导出前会有明确的风险提示弹窗。建议优先使用加密的 JSON 备份用于存储/传输。
