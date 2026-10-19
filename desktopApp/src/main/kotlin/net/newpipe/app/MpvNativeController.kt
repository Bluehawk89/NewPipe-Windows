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
    val coreError: String
)

internal class MpvNativeController {
    private var native: MpvNativeApi? = null
    private var instance: Pointer? = null

    @Synchronized
    fun open(surface: Canvas, source: PlaybackOption): String? {
        if (instance != null) return "This experimental player is already running."
        val root = System.getProperty("compose.application.resources.dir")
            ?: return "Bundled mpv runtime not found (launch the installed MSI)."
        val dll = File(root, "libmpv-2.dll")
        if (!dll.isFile) return "Bundled libmpv-2.dll is missing from the installer."
        val library = try {
            // Never load arbitrary DLLs from PATH or the current directory.
            NativeLibrary.addSearchPath("libmpv-2", dll.parentFile.absolutePath)
            Native.load("libmpv-2", MpvNativeApi::class.java)
        } catch (_: Throwable) {
            return "Windows could not load the bundled mpv library or one of its dependencies."
        }
        val ctx = library.mpv_create() ?: return "mpv could not allocate a player."
        try {
            // Do not allow local configuration or scripts to influence test
            // results. Use mpv's own decoder and audio/video clock management.
            val wid = Native.getComponentID(surface) and 0xffffffffL
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
            // Use libmpv's external audio demuxer and clock rather than
            // opening a second player for a split HLS video/audio rendition.
            // The option is allowed to fail (some streams mux audio already).
            if (source.audioUrl != null) {
                library.mpv_set_option_string(ctx, "audio-files-append", source.audioUrl)
            }
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
            MpvReading(
                timeSeconds = prop("time-pos")?.toDoubleOrNull(),
                activeVideo = prop("vid") ?: "unknown",
                activeAudio = prop("aid") ?: "unknown",
                videoWidth = prop("video-params/w") ?: "?",
                audioRate = prop("audio-params/samplerate") ?: "?",
                aVSync = prop("avsync") ?: "unknown",
                isPaused = prop("pause") == "yes",
                idle = prop("core-idle") == "yes",
                coreError = prop("error") ?: "none"
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
        runCatching { l?.mpv_terminate_destroy(ptr) }
    }
}
