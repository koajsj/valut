package com.offlinevault.autofill

import java.net.IDN
import java.net.URI

/** Strict origin matching used by autofill. It intentionally never uses substring matching. */
object OriginMatcher {

    /** Scheme used to record the identity of a native Android app a credential belongs to. */
    const val APP_SCHEME = "androidapp://"

    fun matches(storedUrl: String, requestedDomain: String?): Boolean {
        val requestedHost = normalizeHost(requestedDomain) ?: return false
        val storedHost = hostFromUrl(storedUrl) ?: return false
        return storedHost == requestedHost || comparableHost(storedHost) == comparableHost(requestedHost)
    }

    /** Canonical identifier stored in a credential's url field for a native app. */
    fun appIdentifier(packageName: String): String = APP_SCHEME + packageName.trim().lowercase()

    /** Matches a stored credential against a native app's package name. Exact, never substring. */
    fun matchesApp(storedUrl: String, requestedPackage: String?): Boolean {
        val pkg = requestedPackage?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        val stored = storedUrl.trim().lowercase()
        return stored == APP_SCHEME + pkg || stored == pkg
    }

    /** True when two stored identifiers point at the same web origin or the same native app. */
    fun sameTarget(a: String, b: String): Boolean {
        val pkgA = a.trim().lowercase().removePrefix(APP_SCHEME)
        val pkgB = b.trim().lowercase().removePrefix(APP_SCHEME)
        if (a.trim().startsWith(APP_SCHEME) || b.trim().startsWith(APP_SCHEME)) return pkgA == pkgB
        val hostA = hostFromUrl(a) ?: return a.trim().equals(b.trim(), ignoreCase = true)
        val hostB = hostFromUrl(b) ?: return false
        return hostA == hostB || comparableHost(hostA) == comparableHost(hostB)
    }

    fun targetLabel(webDomain: String?, packageName: String?): String = when {
        !webDomain.isNullOrBlank() -> normalizeHost(webDomain) ?: webDomain
        !packageName.isNullOrBlank() -> packageName.trim()
        else -> "当前目标"
    }

    fun hostFromUrl(value: String): String? {
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

    private fun comparableHost(host: String): String = host.removePrefix("www.")
}
