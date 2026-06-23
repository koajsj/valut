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

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copySensitive(context: Context, label: String, value: String, clearAfterSeconds: Int) {
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        if (clearAfterSeconds > 0) {
            val runnable = Runnable { clear(appContext, value) }
            pendingClear = runnable
            handler.postDelayed(runnable, clearAfterSeconds * 1000L)
        }
    }

    fun copyPlain(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    /**
     * Clears the copied secret when the timer fires. Reading the clipboard to confirm it still holds
     * our value is blocked in the background on Android 10+, returning null — so we clear on a null
     * read too (fail safe: wipe the secret). We only skip clearing when we can positively see that
     * the user has since copied something different, so we don't stomp their newer clipboard content.
     */
    private fun clear(context: Context, expected: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
