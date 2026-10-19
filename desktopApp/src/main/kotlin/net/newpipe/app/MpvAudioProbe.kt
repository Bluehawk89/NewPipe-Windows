/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Bounded diagnostic of YouTube-provided audio URLs. HTTP acceptance does not
 * prove that mpv decodes the audio. No signed URLs, headers or response bodies
 * are ever returned to users or debug logs.
 */
internal object MpvAudioProbe {
    fun inspect(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return "Invalid audio URI"
        if (!isSafeMediaUri(uri)) return "Audio probe skipped (unexpected host or scheme)"
        val connection = open(uri.toURL()) ?: return "Audio request connection failed"
        return try {
            val status = connection.responseCode
            val mime = connection.contentType?.substringBefore(';')?.take(48) ?: "unknown"
            val first = "Audio HTTP: " + status + " / " + mime
            if (status !in 200..299 || !(mime.contains("mpegurl", true) ||
                        mime.contains("m3u", true))) first
            else {
                val body = connection.inputStream.use {
                    String(it.readNBytes(48 * 1024), StandardCharsets.UTF_8)
                }
                val summary = summarizeHls(body)
                val firstChild = body.lineSequence().map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                val child = firstChild?.let {
                    val resolved = runCatching { connection.url.toURI().resolve(it) }.getOrNull()
                    if (resolved != null && isSafeMediaUri(resolved)) {
                        val conn = open(resolved.toURL())
                        if (conn == null) "audio segment connection unavailable"
                        else try {
                            "first audio child HTTP " + conn.responseCode
                        } catch (e: Exception) {
                            "audio child request " + e.javaClass.simpleName
                        } finally { conn.disconnect() }
                    } else "audio child host not checked"
                } ?: "no audio segment URL found"
                first + "; " + summary + "; " + child
            }
        } catch (e: Exception) {
            "Audio HTTP diagnostic: " + e.javaClass.simpleName
        } finally {
            connection.disconnect()
        }
    }

    internal fun summarizeHls(text: String): String {
        val lines = text.lineSequence().map { it.trim() }.take(1000).toList()
        if (lines.firstOrNull() != "#EXTM3U") return "not an HLS playlist"
        val parts = lines.count { it.startsWith("#EXTINF:") }
        val variants = lines.count { it.startsWith("#EXT-X-STREAM-INF:") }
        val encrypted = lines.any {
            it.startsWith("#EXT-X-KEY:") && !it.contains("METHOD=NONE")
        }
        return "HLS audio segments listed=" + parts +
            ", variants=" + variants + ", keys=" + encrypted
    }

    private fun isSafeMediaUri(uri: URI): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", true) && uri.userInfo == null &&
            (host == "googlevideo.com" || host.endsWith(".googlevideo.com") ||
                host == "youtube.com" || host.endsWith(".youtube.com"))
    }

    private fun open(url: URL): HttpURLConnection? {
        return runCatching {
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 7000
                readTimeout = 6000
                setRequestProperty("Range", "bytes=0-49151")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36"
                )
                setRequestProperty("Referer", "https://www.youtube.com/")
            }
        }.getOrNull()
    }
}
