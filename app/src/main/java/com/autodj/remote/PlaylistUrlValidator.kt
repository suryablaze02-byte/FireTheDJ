package com.autodj.remote

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

sealed interface UrlCheck {
    data class Valid(val ref: PlaylistRef, val provider: StreamProvider) : UrlCheck
    data class Invalid(val reason: String) : UrlCheck
}

/**
 * Checks a pasted playlist link before anything goes over the network: it must be an https
 * URL on a host some provider handles, carrying a well-formed playlist id where that provider
 * expects it (a query parameter such as `list`, or the path). Path ids stay percent-encoded.
 */
class PlaylistUrlValidator(private val providers: List<StreamProvider>) {
    fun check(raw: String): UrlCheck {
        val text = raw.trim()
        if (text.isEmpty()) return UrlCheck.Invalid("Paste a playlist link")
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return UrlCheck.Invalid("That isn't a valid link")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return UrlCheck.Invalid("Only https links are supported")
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return UrlCheck.Invalid("The link has no host")
        val query = queryParams(uri.rawQuery)
        // Still percent-encoded: ids are checked and handed to providers exactly as they appear in the link.
        val path = uri.rawPath.orEmpty()

        val candidates = providers.filter { it.urlRule.hosts.isEmpty() || host in it.urlRule.hosts }
        if (candidates.isEmpty()) {
            return UrlCheck.Invalid("No supported service for $host (supported: ${providers.joinToString { it.displayName }})")
        }
        for (provider in candidates) {
            val rule = provider.urlRule
            val id = rule.idParam?.let { query[it] }
                ?: rule.pathPattern?.matchEntire(path)?.groupValues?.getOrNull(1)
                ?: continue
            if (!rule.idPattern.matches(id)) return UrlCheck.Invalid("The playlist id in that link looks malformed")
            return UrlCheck.Valid(PlaylistRef(provider.id, id, normalise(uri)), provider)
        }
        val expected = candidates.first().urlRule.idParam?.let { "a \"$it\" parameter" } ?: "a playlist path"
        return UrlCheck.Invalid("That link has no playlist in it (expected $expected)")
    }

    companion object {
        /** Query string → first value per name, percent-decoded. */
        fun queryParams(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in rawQuery.split('&')) {
                if (pair.isEmpty()) continue
                val name = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', ""))
                out.putIfAbsent(name, value)
            }
            return out
        }

        private fun decode(s: String) = URLDecoder.decode(s, Charsets.UTF_8.name())

        /** Lower-case host, no fragment: the same playlist pasted twice is recognised as one. */
        private fun normalise(uri: URI) =
            "${uri.scheme.lowercase()}://${uri.rawAuthority.lowercase()}${uri.rawPath.orEmpty()}" +
                (uri.rawQuery?.let { "?$it" } ?: "")
    }
}
