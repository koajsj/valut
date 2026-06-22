# Offline Vault（离线密码库）

一款**完全离线**的 Android 密码管理器，使用 Kotlin + Jetpack Compose 构建。没有网络权限、没有云端、没有服务器——所有数据都加密后保存在本机。

设计灵感来自 1Password、Bitwarden 和 Apple Passwords，但坚持永不离开你的手机。

---

## 亮点功能

- 🔒 所有保存的密码和备注均采用 **AES-256-GCM** 加密
- 🔑 主密码通过 **PBKDF2-HMAC-SHA256**（60 万次迭代，符合 OWASP 建议）派生密钥——迭代次数按密码库分别存储，可以随时上调而不会锁死旧密码库
- 🧬 **信封加密** —— 随机生成的数据加密密钥（DEK）同时被主密码和恢复答案分别包裹一份，忘记密码也不会导致数据丢失
- 👆 通过 Android Keystore + BiometricPrompt(`CryptoObject`) 实现**指纹解锁**
- 🗂 支持**多个密码库**，显示条目数和最后更新时间
- 🧮 **强密码生成器**（长度 8–32，可配置字符集）
- 📊 **密码强度检测**（0–100 分，弱密码与连续字符检测）
- 📥 支持**导入**本应用的加密 JSON 备份或 **Chrome 密码 CSV**
- 📤 支持**导出**加密 JSON（推荐）或明文 CSV（带明确风险提示）
- 🧩 **Android 自动填充**服务，按网页域名 / 应用包名匹配凭据
- 🛡 后台**自动锁定**、**FLAG_SECURE 防截屏**、**剪贴板自动清空**，以及多次解锁失败后的暴力破解延迟
- 🎨 精致的**浅色** Material 3 界面（白色背景配克制的蓝色点缀）

清单中**没有 `android.permission.INTERNET`**。本应用在物理上无法发起任何网络请求。

---

## 构建与运行

### 方式 A —— Android Studio（推荐）
1. 打开 Android Studio（Hedgehog / Iguana 或更新版本，JDK 17）。
2. **File → Open**，选择 `OfflineVault` 文件夹。
3. 等待 Gradle 同步（Android Studio 会自动生成 Gradle wrapper）。
4. 在设备或模拟器（minSdk 26 / Android 8.0+）上运行 `app` 配置。

### 方式 B —— 命令行
本压缩包未附带 `gradle/wrapper/gradle-wrapper.jar` 二进制文件。请先用本机安装的 Gradle 生成一次，再使用 wrapper：

```bash
cd OfflineVault
gradle wrapper --gradle-version 8.2     # 仅需一次，需要本机已安装 Gradle
./gradlew assembleDebug                  # 构建调试版 APK
./gradlew installDebug                   # 安装到已连接的设备
```

APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。

### 启用自动填充（可选）
安装完成后：**系统设置 → 密码、密钥与自动填充 → 自动填充服务 → Offline Vault**。请先至少解锁一次应用，让加密密钥进入内存；该服务只在密码库已解锁时才会提供凭据。

---

## 项目结构

```
app/src/main/java/com/offlinevault/
├─ MainActivity.kt            # 单 Activity，导航、后台自动锁定、FLAG_SECURE
├─ OfflineVaultApp.kt         # Application + 手动依赖注入容器
├─ AppContainer.kt
├─ security/
│  ├─ CryptoManager.kt        # AES-256-GCM、PBKDF2、SecureRandom 原语
│  ├─ KeyManager.kt           # 初始化 / 解锁 / 找回 / 修改密码 / 生物识别包裹
│  ├─ KeystoreManager.kt      # 与生物识别绑定的 AndroidKeyStore 密钥
│  ├─ SessionManager.kt       # 内存中的 DEK 及解锁状态
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
├─ autofill/VaultAutofillService.kt
├─ viewmodel/ (Auth, VaultList, PasswordList, PasswordEdit, PasswordDetail, Settings, Generator)
├─ ui/
│  ├─ theme/ (Color, Type, Theme)
│  ├─ components/ (Components, PasswordGeneratorSheet, VaultIcons)
│  └─ screens/ (Setup, Unlock, Recover, VaultList, PasswordList, PasswordDetail, PasswordEdit, Settings)
└─ utils/ (BiometricHelper, ClipboardHelper, FileIo, Formatters)
```

ViewModel 从不直接访问 DAO —— 一律通过 Repository，所有加解密逻辑都封装在 Repository 内。

---

## 安全模型

```
                 PBKDF2(主密码, masterSalt)  ──► masterKey ──┐
随机生成的 256 位 DEK ─────────────────────────────────────────┼─► AES-GCM 包裹 ─► 持久化存储
                 PBKDF2(恢复答案, recoverySalt) ─► recoveryKey ─┘
```

- **DEK** 用于加密所有密码和备注，只在解锁状态下存在于内存中（`SessionManager`），锁定 / 切到后台时立即清除。
- 主密码和恢复答案**永不存储**。只持久化盐值和两份被包裹的 DEK 副本（保存在 DataStore 中）。
- **解锁** = 派生 `masterKey`，尝试用 AES-GCM 解包 DEK。密码错误会导致 GCM 认证标签校验失败，因此永远无法得到可用的密钥。
- **找回** = 用恢复答案解包 DEK，再用新设置的主密码重新包裹一份。数据完整保留；答案错误则什么也找不回。
- **生物识别** = DEK 额外用一个要求生物识别认证的 AndroidKeyStore AES 密钥包裹一份（`setUserAuthenticationRequired(true)`），通过 BiometricPrompt 的 `CryptoObject` 使用。

---

## 说明 / 局限性

- 自动填充服务是一个扎实的基础实现：解析视图结构，按网页域名 / 应用包名匹配，并填充用户名 + 密码。从自动填充直接保存新登录信息暂未实现，需要在应用内手动添加。该服务只在密码库已解锁时才生效（设计如此——没有密钥就无法解密）。
- CSV 导出本质上是明文，导出前会有明确的风险提示弹窗。建议优先使用加密的 JSON 备份用于存储/传输。
