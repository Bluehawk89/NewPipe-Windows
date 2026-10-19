/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import java.net.URI

/**
 * Pure HLS master-playlist parser: no network, native library, or UI state.
 * The caller supplies an already resolved playlist base URI.
 *
 * HLS attributes use comma-separated fields, but quoted values may contain
 * commas (especially CODECS). Splitting the line on commas loses track links.
 */
internal object HlsMasterParser {
    fun variants(text: String, base: URI, limit: Int = 8): List<PlaybackOption> {
        if (limit <= 0 || !text.trimStart().startsWith("#EXTM3U")) return emptyList()
        val lines = text.lineSequence().map { it.trim() }.take(2000).toList()
        val groups = mutableMapOf<String, MutableList<AudioRendition>>()

        lines.forEach { tag ->
            if (!tag.startsWith("#EXT-X-MEDIA:")) return@forEach
            val attrs = attributes(tag.substringAfter(':'))
            if (attrs["TYPE"] != "AUDIO") return@forEach
            val group = attrs["GROUP-ID"] ?: return@forEach
            val source = resolveHttps(base, attrs["URI"]) ?: return@forEach
            groups.getOrPut(group) { mutableListOf() }.add(
                AudioRendition(
                    url = source,
                    default = attrs["DEFAULT"].equals("YES", ignoreCase = true),
                    language = attrs["LANGUAGE"].orEmpty()
                )
            )
        }

        val choices = mutableListOf<PlaybackOption>()
        var lastStream: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    lastStream = attributes(line.substringAfter(':'))
                }
                line.startsWith("#") || line.isEmpty() -> Unit
                lastStream != null -> {
                    val streamAttrs = lastStream
                    lastStream = null
                    val url = resolveHttps(base, line) ?: continue
                    val height = streamAttrs?.get("RESOLUTION")
                        ?.substringAfter('x', "")?.toIntOrNull()
                    val group = streamAttrs?.get("AUDIO")
                    val audio = group?.let { groups[it] }.orEmpty()
                        .sortedWith(
                            compareByDescending<AudioRendition> { it.default }
                                .thenByDescending { it.language.startsWith("en") }
                        ).firstOrNull()
                    val label = if (height == null) "HLS variant" else "HLS " + height + "p"
                    choices.add(
                        PlaybackOption(
                            url = url,
                            label = label + if (audio != null) " + audio" else "",
                            audioUrl = audio?.url
                        )
                    )
                }
            }
        }

        return choices.distinctBy { it.url }
            .sortedWith(
                compareByDescending<PlaybackOption> { it.audioUrl != null }
                    .thenByDescending { preferenceHeight(it.label) }
            )
            .take(limit)
    }

    internal fun attributes(data: String): Map<String, String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        for (c in data) {
            when {
                escaped -> {
                    current.append(c)
                    escaped = false
                }
                quoted && c == '\\' -> {
                    current.append(c)
                    escaped = true
                }
                c == '"' -> {
                    current.append(c)
                    quoted = !quoted
                }
                c == ',' && !quoted -> {
                    parts.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
        }
        parts.add(current.toString())
        return parts.mapNotNull { item ->
            val sep = item.indexOf('=')
            if (sep <= 0) null else {
                val key = item.substring(0, sep).trim()
                val value = item.substring(sep + 1).trim()
                    .removeSurrounding("\"")
                if (key.isEmpty()) null else key to value
            }
        }.toMap()
    }

    private fun preferenceHeight(label: String): Int {
        val height = Regex("(\\d{3,4})p").find(label)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return if (height in 360..720) height else if (height > 720)
            (720 - (height - 720)).coerceAtLeast(0) else height
    }

    private fun resolveHttps(base: URI, raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 8192) return null
        return runCatching {
            val uri = base.resolve(raw.trim())
            if (uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
            ) uri.toString() else null
        }.getOrNull()
    }

    private data class AudioRendition(
        val url: String,
        val default: Boolean,
        val language: String
    )
}
