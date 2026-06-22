package com.offlinevault.autofill

import java.net.IDN
import java.net.URI

/** Strict origin matching used by autofill. It intentionally never uses substring matching. */
object OriginMatcher {

    fun matches(storedUrl: String, requestedDomain: String?): Boolean {
        val requestedHost = normalizeHost(requestedDomain) ?: return false
        val storedHost = hostFromUrl(storedUrl) ?: return false
        return storedHost == requestedHost
    }

    internal fun hostFromUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        return runCatching { normalizeHost(URI(withScheme).host) }.getOrNull()
    }

    internal fun normalizeHost(value: String?): String? {
        val host = value?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (host.isEmpty()) return null
        return runCatching { IDN.toASCII(host) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
    }
}
