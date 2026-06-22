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
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        if (clearAfterSeconds > 0) {
            pendingClear?.let { handler.removeCallbacks(it) }
            val runnable = Runnable { clear(context, value) }
            pendingClear = runnable
            handler.postDelayed(runnable, clearAfterSeconds * 1000L)
        }
    }

    fun copyPlain(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    /** Only clears if the clipboard still holds the value we put there. */
    private fun clear(context: Context, expected: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (current == expected) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        pendingClear = null
    }
}
