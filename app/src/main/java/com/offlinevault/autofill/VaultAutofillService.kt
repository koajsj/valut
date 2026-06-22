package com.offlinevault.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.offlinevault.OfflineVaultApp
import com.offlinevault.R
import com.offlinevault.data.repository.DecryptedPassword
import com.offlinevault.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Offline autofill service. It NEVER touches the network — it only reads the locally decrypted
 * vault while the app is unlocked, matches credentials against the requesting package / web domain,
 * and offers them as datasets.
 *
 * When the vault is locked there is no key in memory, so no credentials can be offered; the user
 * must open Offline Vault and unlock first.
 */
@RequiresApi(Build.VERSION_CODES.O)
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ParsedFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val webDomain: String?
    )

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
        if (parsed.passwordId == null && parsed.usernameId == null) {
            callback.onSuccess(null)
            return
        }

        // Only offer credentials if the vault is currently unlocked.
        if (!SessionManager.isUnlocked) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            val matches = findMatches(parsed)
            if (cancellationSignal.isCanceled) return@launch
            if (matches.isEmpty()) {
                callback.onSuccess(null)
                return@launch
            }

            val responseBuilder = FillResponse.Builder()
            for (item in matches) responseBuilder.addDataset(buildDataset(item, parsed))
            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Saving new credentials from autofill is intentionally not handled in this basic version;
        // credentials are added inside the app. We acknowledge so the platform does not warn.
        callback.onSuccess()
    }

    // ---- Matching ----------------------------------------------------------

    private suspend fun findMatches(parsed: ParsedFields): List<DecryptedPassword> {
        val domain = parsed.webDomain ?: return emptyList()
        val repo = (application as OfflineVaultApp).container.passwordRepository
        return runCatching {
            repo.decryptedMatching { OriginMatcher.matches(it, domain) }
        }.getOrDefault(emptyList()).take(8)
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
        var webDomain: String? = null

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
                } else if (isUsername && usernameId == null) {
                    usernameId = autofillId
                }
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }

        return ParsedFields(usernameId, passwordId, webDomain)
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
