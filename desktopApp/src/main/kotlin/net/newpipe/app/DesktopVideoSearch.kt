/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/** A JVM-only bridge to NewPipe Extractor. Never run calls on the Compose UI thread. */
internal object DesktopVideoSearch {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloader = object : Downloader() {
        override fun execute(request: Request): Response {
            val method = request.httpMethod()
            val bytes = request.dataToSend()
            val body = if (method.equals("GET", true) || method.equals("HEAD", true)) {
                null
            } else {
                (bytes ?: byteArrayOf()).toRequestBody(null)
            }
            val outgoing = okhttp3.Request.Builder()
                .url(request.url())
                .method(method, body)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")

            request.headers().forEach { (name, values) ->
                outgoing.removeHeader(name)
                values.forEach { outgoing.addHeader(name, it) }
            }

            client.newCall(outgoing.build()).execute().use { incoming ->
                return Response(
                    incoming.code,
                    incoming.message,
                    incoming.headers.toMultimap(),
                    incoming.body?.string(),
                    incoming.request.url.toString()
                )
            }
        }
    }

    init {
        NewPipe.init(downloader)
    }

    fun search(query: String): List<VideoResult> {
        require(query.isNotBlank()) { "Enter a search term" }
        val youtube = NewPipe.getService("YouTube")
        val searchHandler = youtube.searchQHFactory.fromQuery(query.trim())
        val page = SearchInfo.getInfo(youtube, searchHandler)
        return page.relatedItems.orEmpty()
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                VideoResult(
                    title = item.name,
                    url = item.url,
                    channel = item.uploaderName.orEmpty(),
                    thumbnail = item.thumbnails.firstOrNull()?.url,
                    durationSeconds = item.duration,
                    views = item.viewCount
                )
            }
    }

    /**
     * Return alternative combined audio/video streams, including HLS, so VLC
     * failures can be recovered without repeating extraction.
     */
    fun resolvePlaybackOptions(videoUrl: String): List<PlaybackOption> {
        val info = StreamInfo.getInfo(videoUrl)
        val progressive = info.videoStreams.orEmpty()
            .filter { !it.isVideoOnly && it.isUrl && it.content.startsWith("https://") }
            .sortedWith(
                compareByDescending<VideoStream> {
                    if (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) 1 else 0
                }.thenByDescending {
                    if (it.format == MediaFormat.MPEG_4) 1 else 0
                }.thenByDescending { qualityPreference(it.resolution) }
            )
            .map { stream ->
                PlaybackOption(
                    url = stream.content,
                    label = "${stream.format?.name ?: "Video"} ${stream.resolution}".trim()
                )
            }
            .distinctBy { it.url }
            .take(6)

        val hlsUrl = info.hlsUrl?.takeIf { it.startsWith("https://") }
        val hls = hlsUrl?.let { listOf(PlaybackOption(it, "HLS master / live stream")) }
            .orEmpty()
        // A DASH MPD may be usable even when separate progressive video
        // and audio are unavailable to the JVM extractor.
        val dash = info.dashMpdUrl?.takeIf { it.startsWith("https://") }
            ?.let { listOf(PlaybackOption(it, "DASH manifest")) }
            .orEmpty()
        val hlsVariants = hlsUrl?.let { extractHlsVariants(it) }.orEmpty()
        // Individual HLS variants can play when VLC fails to negotiate the master.
        // Prefer the master first for correct alternate audio-track negotiation,
        // then try variants and DASH as fallback.
        val options = if (info.streamType.toString().contains("LIVE")) {
            (hls + hlsVariants + dash + progressive)
        } else {
            (progressive + dash + hls + hlsVariants)
        }.distinctBy { it.url }.take(14)
        if (options.isEmpty()) {
            throw IllegalStateException(
                "No compatible combined audio/video or HLS stream was found for this video."
            )
        }
        return options
    }

    /**
     * Read only a small bounded amount of an HLS master playlist. This is run
     * on the IO dispatcher, never from the desktop UI thread.
     * URLs are returned to the player but must never be included in diagnostics.
     */
    private fun extractHlsVariants(masterUrl: String): List<PlaybackOption> {
        return try {
            val outgoing = okhttp3.Request.Builder()
                .url(masterUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .get().build()
            client.newCall(outgoing).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val text = response.body?.byteStream()?.use {
                    String(it.readNBytes(256 * 1024), Charsets.UTF_8)
                } ?: return emptyList()
                if (!text.trimStart().startsWith("#EXTM3U")) return emptyList()
                val base = response.request.url.toUri()
                val choices = mutableListOf<PlaybackOption>()
                var streamMetadata: String? = null
                for (line in text.lineSequence().take(2000)) {
                    val raw = line.trim()
                    when {
                        raw.startsWith("#EXT-X-STREAM-INF:") -> streamMetadata = raw
                        raw.isNotBlank() && !raw.startsWith("#") && streamMetadata != null -> {
                            val variantUri = runCatching { base.resolve(raw) }.getOrNull()
                            if (variantUri?.scheme.equals("https", ignoreCase = true)) {
                                val height = Regex("RESOLUTION=\\d+x(\\d+)")
                                    .find(streamMetadata)?.groupValues?.getOrNull(1)
                                choices.add(
                                    PlaybackOption(
                                        variantUri.toString(),
                                        if (height == null) "HLS variant" else "HLS ${height}p"
                                    )
                                )
                            }
                            streamMetadata = null
                        }
                    }
                }
                choices.distinctBy { it.url }.sortedByDescending { option ->
                    val h = Regex("(\\d{3,4})p").find(option.label)
                        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    if (h <= 720) h else 720 - (h - 720)
                }.take(8)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resolvePlaybackUrl(videoUrl: String): String = resolvePlaybackOptions(videoUrl).first().url

    private fun qualityPreference(resolution: String): Int {
        val height = Regex("(\\d{3,4})").find(resolution)?.value?.toIntOrNull() ?: 0
        return when {
            height in 360..720 -> height
            height > 720 -> (720 - (height - 720)).coerceAtLeast(0)
            else -> height
        }
    }
}

internal data class VideoResult(
    val title: String,
    val url: String,
    val channel: String,
    val thumbnail: String?,
    val durationSeconds: Long,
    val views: Long
)

internal data class PlaybackOption(val url: String, val label: String)
