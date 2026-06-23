package com.offlinevault.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.offlinevault.MainActivity
import com.offlinevault.OfflineVaultApp
import com.offlinevault.R
import com.offlinevault.data.repository.DecryptedPassword
import com.offlinevault.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Offline autofill service. It NEVER touches the network — it only reads the locally decrypted
 * vault while the app is unlocked, matches credentials against the requesting package / web domain,
 * and offers them as datasets. It also offers to SAVE newly typed credentials via the platform's
 * native "save" prompt.
 *
 * When the vault is locked there is no key in memory, so no credentials can be offered for fill;
 * saving is deferred until the next unlock.
 */
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ParsedFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val webDomain: String?,
        val packageName: String?,
        val usernameValue: String?,
        val passwordValue: String?
    ) {
        /** Stable identifier for this target: the web origin if present, else the native app. */
        val identifier: String?
            get() = when {
                !webDomain.isNullOrBlank() -> webDomain
                !packageName.isNullOrBlank() -> OriginMatcher.appIdentifier(packageName)
                else -> null
            }
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = parseStructure(structure)
        // Never operate on our own UI, and require at least one fillable field.
        if (parsed.packageName == applicationContext.packageName ||
            (parsed.passwordId == null && parsed.usernameId == null)
        ) {
            callback.onSuccess(null)
            return
        }

        val saveInfo = buildSaveInfo(parsed)

        // While locked there is no key, so no datasets can be built — but we still register the
        // SaveInfo so the user can save credentials they type now (persisted after the next unlock).
        if (!SessionManager.isUnlocked) {
            callback.onSuccess(saveInfo?.let { FillResponse.Builder().setSaveInfo(it).build() })
            return
        }

        serviceScope.launch {
            try {
                val matches = findMatches(parsed)
                if (cancellationSignal.isCanceled) return@launch

                val builder = FillResponse.Builder()
                var hasContent = false
                for (item in matches) {
                    builder.addDataset(buildDataset(item, parsed))
                    hasContent = true
                }
                if (saveInfo != null) {
                    builder.setSaveInfo(saveInfo)
                    hasContent = true
                }
                callback.onSuccess(if (hasContent) builder.build() else null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never let an unexpected failure escape the launch — that would crash the autofill
                // service process. Fail gracefully by offering nothing.
                runCatching { callback.onSuccess(null) }
            }
        }
    }

    /** A save offer needs at least a password field; the username is included when present. */
    private fun buildSaveInfo(parsed: ParsedFields): SaveInfo? {
        val passwordId = parsed.passwordId ?: return null
        if (parsed.identifier == null) return null
        var type = SaveInfo.SAVE_DATA_TYPE_PASSWORD
        val ids = mutableListOf(passwordId)
        parsed.usernameId?.let {
            type = type or SaveInfo.SAVE_DATA_TYPE_USERNAME
            ids.add(it)
        }
        return SaveInfo.Builder(type, ids.toTypedArray()).build()
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }
        val parsed = parseStructure(structure)
        val identifier = parsed.identifier
        val password = parsed.passwordValue
        if (parsed.packageName == applicationContext.packageName ||
            identifier == null || password.isNullOrEmpty()
        ) {
            callback.onSuccess()
            return
        }
        val capture = PendingAutofillSave.Capture(
            identifier = identifier,
            username = parsed.usernameValue.orEmpty(),
            password = password
        )

        if (SessionManager.isUnlocked) {
            val app = application as OfflineVaultApp
            serviceScope.launch {
                try {
                    val vaultId = app.container.vaultRepository.ensureDefault().id
                    app.container.passwordRepository.upsertFromAutofill(
                        vaultId, capture.identifier, capture.username, capture.password
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Fall back to the deferred path so the capture is not lost.
                    PendingAutofillSave.set(capture)
                }
            }
        } else {
            // Locked: stash the capture and bring the app forward so the user can unlock; the save
            // completes right after unlock. Stashing first means the capture survives even if the
            // activity start is suppressed by the OS background-launch limits.
            PendingAutofillSave.set(capture)
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        callback.onSuccess()
    }

    // ---- Matching ----------------------------------------------------------

    private suspend fun findMatches(parsed: ParsedFields): List<DecryptedPassword> {
        val repo = (application as OfflineVaultApp).container.passwordRepository
        return try {
            when {
                !parsed.webDomain.isNullOrBlank() ->
                    repo.decryptedMatching { OriginMatcher.matches(it, parsed.webDomain) }
                !parsed.packageName.isNullOrBlank() ->
                    repo.decryptedMatching { OriginMatcher.matchesApp(it, parsed.packageName) }
                else -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }.take(8)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- Dataset building --------------------------------------------------

    private fun buildDataset(item: DecryptedPassword, parsed: ParsedFields): Dataset {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, item.title.ifEmpty { "离线密码库" })
            setTextViewText(R.id.autofill_subtitle, item.username.ifEmpty { item.url })
        }

        val builder = Dataset.Builder(presentation)
        parsed.usernameId?.let {
            builder.setValue(it, AutofillValue.forText(item.username))
        }
        parsed.passwordId?.let {
            builder.setValue(it, AutofillValue.forText(item.password))
        }
        return builder.build()
    }

    // ---- Structure parsing -------------------------------------------------

    private fun parseStructure(structure: AssistStructure): ParsedFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue: String? = null
        var passwordValue: String? = null
        var webDomain: String? = null
        val packageName = structure.activityComponent?.packageName

        fun traverse(node: AssistStructure.ViewNode) {
            if (node.webDomain?.isNotEmpty() == true && webDomain == null) {
                webDomain = node.webDomain
            }
            val autofillId = node.autofillId
            if (autofillId != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
                val inputType = node.inputType
                val isPassword = hints.any { it.contains("password") } || isPasswordInput(inputType)
                val isUsername = hints.any {
                    it.contains("username") || it.contains("email") || it == View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase()
                } || looksLikeUsername(node)

                if (isPassword && passwordId == null) {
                    passwordId = autofillId
                    passwordValue = textValueOf(node)
                } else if (isUsername && usernameId == null) {
                    usernameId = autofillId
                    usernameValue = textValueOf(node)
                }
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }

        return ParsedFields(usernameId, passwordId, webDomain, packageName, usernameValue, passwordValue)
    }

    /** Reads the text the user has currently entered into a node, if any. */
    private fun textValueOf(node: AssistStructure.ViewNode): String? {
        val value = node.autofillValue
        val fromAutofill = if (value != null && value.isText) value.textValue.toString() else null
        return (fromAutofill ?: node.text?.toString())?.takeIf { it.isNotEmpty() }
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val klass = inputType and InputType.TYPE_MASK_CLASS
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            (klass == InputType.TYPE_CLASS_NUMBER &&
                (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0)
    }

    private fun looksLikeUsername(node: AssistStructure.ViewNode): Boolean {
        val hint = (node.hint ?: "").lowercase()
        val idEntry = (node.idEntry ?: "").lowercase()
        val keywords = listOf("user", "email", "login", "account", "phone")
        return keywords.any { hint.contains(it) || idEntry.contains(it) }
    }
}
