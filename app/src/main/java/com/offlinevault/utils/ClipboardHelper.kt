package com.offlinevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies sensitive values to the clipboard and automatically wipes them after a delay so a copied
 * password does not linger. On Android 13+ the clip is also flagged as sensitive so it is hidden
 * from the clipboard preview.
 */
object ClipboardHelper {

    private const val DEFAULT_SENSITIVE_CLEAR_SECONDS = 10

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copySensitive(context: Context, label: String, value: String, clearAfterSeconds: Int) {
        val effectiveClearSeconds =
            if (clearAfterSeconds > 0) clearAfterSeconds else DEFAULT_SENSITIVE_CLEAR_SECONDS
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }.getOrElse { return }

        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        val runnable = Runnable { clear(appContext, value) }
        pendingClear = runnable
        handler.postDelayed(runnable, effectiveClearSeconds * 1000L)
    }

    fun copyPlain(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, value)) }
    }

    /**
     * Clears the copied secret when the timer fires. Reading the clipboard to confirm it still holds
     * our value is blocked in the background on Android 10+, returning null — so we clear on a null
     * read too (fail safe: wipe the secret). We only skip clearing when we can positively see that
     * the user has since copied something different, so we don't stomp their newer clipboard content.
     */
    private fun clear(context: Context, expected: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current == null || current == expected) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
        pendingClear = null
    }
}
