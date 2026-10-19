/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Canvas
import java.io.File
import javax.swing.SwingUtilities

/**
 * Narrow native libmpv boundary for an independent A/B proof of concept.
 *
 * This variant uses mpv's Windows window-id embedding ONLY to test its demux,
 * decoding and audio synchronization against LibVLC on the same PC.
 * The shipping renderer must still evaluate libmpv's render API.
 *
 * All caller-supplied stream URLs may carry expiring signatures; never put
 * arguments, native errors, or URLs in logs or user-copyable diagnostics.
 */
internal interface MpvNativeApi : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_command(ctx: Pointer, args: Array<String>): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_free(value: Pointer)
    fun mpv_terminate_destroy(ctx: Pointer)
}

internal data class MpvReading(
    val timeSeconds: Double?,
    val activeVideo: String,
    val activeAudio: String,
    val videoWidth: String,
    val audioRate: String,
    val aVSync: String,
    val isPaused: Boolean,
    val idle: Boolean,
    val coreError: String,
    val audioTrackCount: Int,
    val externalAudioStatus: String,
    val audioOutput: String,
    val audioCodec: String
)

internal class MpvNativeController {
    private var native: MpvNativeApi? = null
    private var instance: Pointer? = null
    private var pendingAudioUrl: String? = null
    private var audioAttachmentStatus = "not required"
    private var fileLoaded = false

    @Synchronized
    fun open(surface: Canvas, source: PlaybackOption): String? {
        if (instance != null) return "This experimental player is already running."
        val root = System.getProperty("compose.application.resources.dir")
            ?: return "Bundled mpv runtime not found (launch the installed MSI)."
        val dll = File(root, "libmpv-2.dll")
        if (!dll.isFile) return "Bundled libmpv-2.dll is missing from the installer."
        val library = try {
            // Never load arbitrary DLLs from PATH or the current directory.
            Native.load(dll.absolutePath, MpvNativeApi::class.java)
        } catch (_: Throwable) {
            return "Windows could not load the bundled mpv library or one of its dependencies."
        }
        val ctx = library.mpv_create() ?: return "mpv could not allocate a player."
        try {
            // Do not allow local configuration or scripts to influence test
            // results. Use mpv's own decoder and audio/video clock management.
            // AWT native window handles must be acquired on the Swing EDT.
            // Never truncate a 64-bit HWND to a 32-bit integer.
            val hwnd = LongArray(1)
            SwingUtilities.invokeAndWait { hwnd[0] = Native.getComponentID(surface) }
            val wid = hwnd[0]
            if (wid == 0L) error("video window not initialized")
            fun option(name: String, value: String) {
                val rc = library.mpv_set_option_string(ctx, name, value)
                if (rc < 0) error("unsupported mpv option: " + name)
            }
            option("config", "no")
            option("load-scripts", "no")
            option("terminal", "no")
            option("osc", "no")
            option("input-default-bindings", "no")
            option("input-vo-keyboard", "no")
            option("wid", wid.toString())
            option("keep-open", "yes")
            option("hwdec", "auto-safe")
            // mpv's list option is not a reliable per-file selection here.
            // Add the separate HLS audio rendition using audio-add SELECT
            // *after* MPV_EVENT_FILE_LOADED, and verify its selected track.
            pendingAudioUrl = source.audioUrl
            audioAttachmentStatus = if (source.audioUrl == null) "not required" else "waiting for media load"
            fileLoaded = false
            if (library.mpv_initialize(ctx) < 0) error("mpv initialization")
            instance = ctx
            native = library
            val result = library.mpv_command(ctx, arrayOf("loadfile", source.url, "replace"))
            if (result < 0) error("mpv could not open this format")
            return null
        } catch (error: Throwable) {
            runCatching { library.mpv_terminate_destroy(ctx) }
            native = null
            instance = null
            pendingAudioUrl = null
            return if (error is IllegalStateException)
                "mpv setup failed: " + error.message.orEmpty().take(110)
            else "mpv setup failed (" + error.javaClass.simpleName + ")"
        }
    }

    @Synchronized
    fun snapshot(): MpvReading? {
        val l = native ?: return null
        val ctx = instance ?: return null
        fun prop(key: String): String? {
            val ptr = l.mpv_get_property_string(ctx, key) ?: return null
            return try { ptr.getString(0, "UTF-8") } finally { l.mpv_free(ptr) }
        }
        return runCatching {
            // mpv requires consumers to drain its event queue. FILE_LOADED
            // is the correct point to load audio tracks belonging to this
            // stream rather than setting a global startup option.
            repeat(32) {
                val event = l.mpv_wait_event(ctx, 0.0) ?: return@repeat
                val eventId = event.getInt(0)
                if (eventId == 0) return@repeat
                if (eventId == 8) fileLoaded = true
            }
            val videoWidth = prop("video-params/w") ?: "?"
            if (videoWidth.toIntOrNull()?.let { it > 0 } == true) {
                fileLoaded = true
            }
            if (fileLoaded && pendingAudioUrl != null) {
                val audioUri = pendingAudioUrl!!
                pendingAudioUrl = null
                val rc = l.mpv_command(ctx, arrayOf("audio-add", audioUri, "select"))
                audioAttachmentStatus = if (rc >= 0) "audio-add accepted; awaiting decoder"
                    else "audio-add rejected (mpv code " + rc + ")"
            }
            val count = (prop("track-list/count")?.toIntOrNull() ?: 0).coerceIn(0, 48)
            val countAudio = (0 until count).count { prop("track-list/" + it + "/type") == "audio" }
            val activeAudio = prop("aid") ?: "unknown"
            if (activeAudio != "no" && activeAudio != "unknown" &&
                audioAttachmentStatus.startsWith("audio-add accepted")) {
                audioAttachmentStatus = "external audio selected"
            }
            MpvReading(
                timeSeconds = prop("time-pos")?.toDoubleOrNull(),
                activeVideo = prop("vid") ?: "unknown",
                activeAudio = activeAudio,
                videoWidth = videoWidth,
                audioRate = prop("audio-params/samplerate") ?: "?",
                aVSync = prop("avsync") ?: "unknown",
                isPaused = prop("pause") == "yes",
                idle = prop("core-idle") == "yes",
                coreError = prop("error") ?: "none",
                audioTrackCount = countAudio,
                externalAudioStatus = audioAttachmentStatus,
                audioOutput = prop("ao") ?: "unknown",
                audioCodec = prop("audio-codec-name") ?: "unknown"
            )
        }.getOrNull()
    }

    @Synchronized
    fun pause(paused: Boolean) {
        val ptr = instance ?: return
        runCatching { native?.mpv_set_property_string(ptr, "pause", if (paused) "yes" else "no") }
    }

    @Synchronized
    fun seekBy(seconds: Int) {
        val ptr = instance ?: return
        runCatching { native?.mpv_command(ptr, arrayOf("seek", seconds.toString(), "relative")) }
    }

    @Synchronized
    fun setVolume(volume: Int) {
        val ptr = instance ?: return
        runCatching {
            native?.mpv_set_property_string(ptr, "volume", volume.coerceIn(0, 100).toString())
        }
    }

    @Synchronized
    fun close() {
        val ptr = instance ?: return
        val l = native
        instance = null
        native = null
        pendingAudioUrl = null
        audioAttachmentStatus = "not required"
        fileLoaded = false
        runCatching { l?.mpv_terminate_destroy(ptr) }
    }
}
