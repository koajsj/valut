# Offline Vault

A **fully offline** password manager for Android, built with Kotlin + Jetpack Compose. No internet
permission, no cloud, no servers — everything is encrypted and stored locally on the device.

Inspired by 1Password, Bitwarden and Apple Passwords, but designed to never leave your phone.

---

## Highlights

- 🔒 **AES-256-GCM** encryption for every stored password and note
- 🔑 **PBKDF2-HMAC-SHA256** (600k iterations, OWASP-aligned) key derivation from your master
  password — the iteration count is stored per-vault so it can be raised without locking old vaults
- 🧬 **Envelope encryption** — a random Data Encryption Key (DEK) wrapped by both your master
  password *and* a recovery answer, so forgetting your password does **not** mean losing data
- 👆 **Fingerprint unlock** via Android Keystore + BiometricPrompt (`CryptoObject`)
- 🗂 **Multiple vaults** with item counts and last-updated times
- 🧮 **Strong password generator** (length 8–32, configurable character sets)
- 📊 **Password strength checker** (0–100 score, weak-password & sequence detection)
- 📥 **Import** from this app's encrypted JSON backup or **Chrome password CSV**
- 📤 **Export** encrypted JSON (recommended) or plaintext CSV (with an explicit warning)
- 🧩 **Android Autofill** service that matches credentials by web domain / package name
- 🛡 Background **auto-lock**, **FLAG_SECURE** anti-screenshot, **clipboard auto-clear**, and a
  brute-force delay after repeated failed unlocks
- 🎨 Product-grade **light** Material 3 UI (white background with restrained blue accents)

There is **no `android.permission.INTERNET`** in the manifest. The app physically cannot make
network calls.

---

## Build & run

### Option A — Android Studio (recommended)
1. Open Android Studio (Hedgehog / Iguana or newer, JDK 17).
2. **File → Open** and select the `OfflineVault` folder.
3. Let Gradle sync (Android Studio will generate the Gradle wrapper automatically).
4. Run the `app` configuration on a device or emulator (minSdk 26 / Android 8.0+).

### Option B — command line
The binary `gradle/wrapper/gradle-wrapper.jar` is not shipped in this archive. Generate it once
with a local Gradle install, then use the wrapper:

```bash
cd OfflineVault
gradle wrapper --gradle-version 8.2     # one-time, requires Gradle installed
./gradlew assembleDebug                  # build debug APK
./gradlew installDebug                   # install on a connected device
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Enable autofill (optional)
After installing: **System Settings → Passwords, passkeys & autofill → Autofill service →
Offline Vault**. Unlock the app at least once so the encryption key is in memory; the service only
offers credentials while the vault is unlocked.

---

## Project structure

```
app/src/main/java/com/offlinevault/
├─ MainActivity.kt            # single activity, navigation, lock-on-background, FLAG_SECURE
├─ OfflineVaultApp.kt         # Application + manual DI container
├─ AppContainer.kt
├─ security/
│  ├─ CryptoManager.kt        # AES-256-GCM, PBKDF2, SecureRandom primitives
│  ├─ KeyManager.kt           # setup / unlock / recovery / change-password / biometric wrapping
│  ├─ KeystoreManager.kt      # biometric-bound AndroidKeyStore key
│  ├─ SessionManager.kt       # in-memory DEK + unlocked state
│  ├─ LockGuard.kt            # suppress lock during system pickers / biometric
│  ├─ PasswordStrengthChecker.kt
│  └─ PasswordGenerator.kt
├─ data/
│  ├─ db/AppDatabase.kt
│  ├─ dao/ (VaultDao, PasswordDao)
│  ├─ model/ (VaultEntity, PasswordEntity)
│  ├─ repository/ (VaultRepository, PasswordRepository)   # encrypt/decrypt lives here
│  ├─ preferences/SecurityPreferences.kt                  # DataStore (key material + settings)
│  └─ backup/ (BackupManager, CsvUtils, models)           # JSON/CSV import & export
├─ autofill/VaultAutofillService.kt
├─ viewmodel/ (Auth, VaultList, PasswordList, PasswordEdit, PasswordDetail, Settings, Generator)
├─ ui/
│  ├─ theme/ (Color, Type, Theme)
│  ├─ components/ (Components, PasswordGeneratorSheet, VaultIcons)
│  └─ screens/ (Setup, Unlock, Recover, VaultList, PasswordList, PasswordDetail, PasswordEdit, Settings)
└─ utils/ (BiometricHelper, ClipboardHelper, FileIo, Formatters)
```

ViewModels never touch the DAOs directly — they go through repositories, which own all encryption.

---

## Security model

```
                 PBKDF2(masterPassword, masterSalt)  ──► masterKey ──┐
random 256-bit DEK ──────────────────────────────────────────────────┼─► AES-GCM wrap ─► stored
                 PBKDF2(recoveryAnswer, recoverySalt) ─► recoveryKey ─┘
```

- The **DEK** encrypts all passwords/notes and only ever exists in memory (`SessionManager`) while
  unlocked. It is dropped on lock / background.
- The master password and recovery answer are **never stored**. Only salts and the two wrapped
  copies of the DEK are persisted (in DataStore).
- **Unlock** = derive `masterKey`, attempt to AES-GCM-unwrap the DEK. A wrong password fails the
  GCM authentication tag, so it can never produce a usable key.
- **Recovery** = unwrap the DEK with the recovery answer, then re-wrap it under a brand-new master
  password. Data is preserved; a wrong answer recovers nothing.
- **Biometric** = the DEK is additionally wrapped by an AndroidKeyStore AES key that requires
  biometric authentication (`setUserAuthenticationRequired(true)`), used via a BiometricPrompt
  `CryptoObject`.

---

## Notes / limitations

- The autofill service is a solid basic implementation: it parses the view structure, matches by
  web domain / package name, and fills username + password. Saving new logins from autofill is left
  to in-app entry. It only works while the vault is unlocked (by design — no key, no decryption).
- CSV export is plaintext by nature and is gated behind an explicit warning dialog. Prefer the
  encrypted JSON backup for storage/transfer.
