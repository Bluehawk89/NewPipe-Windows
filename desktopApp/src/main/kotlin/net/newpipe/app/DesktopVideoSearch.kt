/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
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
     * Obtain a playable progressive video+audio URL for VLC.
     * Video-only streams need separate audio handling and are intentionally not used yet.
     */
    fun resolvePlaybackUrl(videoUrl: String): String {
        val info = StreamInfo.getInfo(videoUrl)
        val streams = info.videoStreams.orEmpty()
            .filter { it.isUrl && it.content.startsWith("https://") }
            .sortedByDescending { stream ->
                Regex("(\\d{3,4})").find(stream.resolution.orEmpty())?.value?.toIntOrNull() ?: 0
            }
        val stream = streams.firstOrNull { it.resolution.contains("720") }
            ?: streams.firstOrNull { it.resolution.contains("480") }
            ?: streams.firstOrNull { it.resolution.contains("360") }
            ?: streams.firstOrNull()
        return stream?.content
            ?: info.hlsUrl?.takeIf { it.startsWith("https://") }
            ?: throw IllegalStateException(
                "No compatible combined audio/video stream was found for this video."
            )
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
