/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.lwjgl.opengl.GL
import java.io.File

/**
 * Isolated proof of mpv's real OpenGL render API (render.h/render_gl.h).
 * No mpv --wid window takeover: libmpv draws to the framebuffer owned by
 * our application. This is STILL an AWT-hosted experimental GL surface,
 * not final Compose/Skia texture interop or a production renderer.
 *
 * The graphics methods must run with the SAME GL context current. The
 * core methods must run off the GL-render thread; do not hold locks while
 * waiting for the render thread.
 */
internal interface MpvGpuApi : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(core: Pointer): Int
    fun mpv_set_option_string(core: Pointer, key: String, value: String): Int
    fun mpv_get_property_string(core: Pointer, key: String): Pointer?
    fun mpv_command(core: Pointer, args: Array<String>): Int
    fun mpv_wait_event(core: Pointer, timeout: Double): Pointer?
    fun mpv_free(ptr: Pointer)
    fun mpv_terminate_destroy(core: Pointer)

    fun mpv_render_context_create(result: PointerByReference, core: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(
        renderContext: Pointer, callback: MpvGpuUpdateCallback?, data: Pointer?
    )
    fun mpv_render_context_update(renderContext: Pointer): Long
    fun mpv_render_context_render(renderContext: Pointer, params: Pointer): Int
    fun mpv_render_context_report_swap(renderContext: Pointer)
    fun mpv_render_context_free(renderContext: Pointer)
}

internal interface MpvGpuProcCallback : Callback {
    fun invoke(context: Pointer?, name: String): Pointer?
}

internal interface MpvGpuUpdateCallback : Callback {
    fun invoke(context: Pointer?)
}

/**
 * Native mpv_render_param is { enum int type; void* data; } with pointer-size
 * alignment. Isolate ABI layout so it can be regression tested before loading
 * mpv. Do not use raw byte arrays for native pointer values.
 */
internal object MpvGlParams {
    const val API_TYPE = 1
    const val OPENGL_INIT = 2
    const val OPENGL_FBO = 3
    const val FLIP_Y = 4
    val pointerOffset: Long get() = if (Native.POINTER_SIZE == 8) 8L else 4L
    val entrySize: Long get() = pointerOffset + Native.POINTER_SIZE

    fun encode(vararg entries: Pair<Int, Pointer?>): Memory {
        val memory = Memory(entrySize * (entries.size + 1L))
        memory.clear()
        entries.forEachIndexed { index, (type, value) ->
            val offset = index * entrySize
            memory.setInt(offset, type)
            memory.setPointer(offset + pointerOffset, value)
        }
        return memory
    }
}

internal data class MpvGpuStatus(
    val video: String,
    val audio: String,
    val elapsed: String,
    val audioTracks: Int,
    val aVSync: String,
    val renderFrames: Long,
    val audioAttachment: String
)

internal class MpvGlRenderBridge {
    @Volatile var lastFailure: String? = null
        private set
    @Volatile var ready = false
        private set
    @Volatile var frameCount = 0L
        private set
    @Volatile var externalAudio = "none"
        private set

    private var api: MpvGpuApi? = null
    private var core: Pointer? = null
    private var renderContext: Pointer? = null
    private var procCallback: MpvGpuProcCallback? = null
    private var updateCallback: MpvGpuUpdateCallback? = null
    private var activeUrl: String? = null
    private var pendingAudio: String? = null
    private var loaded = false
    private var disposed = false

    /**
     * Called ON the OpenGL canvas thread while the right context is current.
     * Do not call Swing/AWT synchronous APIs or wait for core worker here.
     */
    fun createOnGlThread(onFrame: () -> Unit) {
        if (ready || disposed) return
        var createdCore: Pointer? = null
        var createdRender: Pointer? = null
        var loadedApi: MpvGpuApi? = null
        try {
            check(Native.POINTER_SIZE == 8) { "GPU proof currently supports Windows x64 only." }
            val base = System.getProperty("compose.application.resources.dir")
                ?: error("Bundled libmpv directory unavailable.")
            val dll = File(base, "libmpv-2.dll")
            check(dll.isFile) { "Verified libmpv DLL missing from installer." }
            val nativeApi = Native.load(dll.absolutePath, MpvGpuApi::class.java)
            loadedApi = nativeApi
            val mpv = nativeApi.mpv_create() ?: error("Unable to allocate libmpv core.")
            createdCore = mpv
            for ((name, value) in arrayOf(
                "config" to "no",
                "load-scripts" to "no",
                "terminal" to "no",
                "osc" to "no",
                "input-default-bindings" to "no",
                // OpenGL hardware video decode needs extra ANGLE/DX interop on
                // Windows; initially prove presentation with software decode.
                "hwdec" to "no"
            )) {
                check(nativeApi.mpv_set_option_string(mpv, name, value) >= 0) {
                    "mpv rejected render setup option " + name
                }
            }
            check(nativeApi.mpv_initialize(mpv) >= 0) { "mpv initialization failed." }

            val callback = object : MpvGpuProcCallback {
                override fun invoke(context: Pointer?, name: String): Pointer? {
                    val address = GL.getFunctionProvider().getFunctionAddress(name)
                    return if (address == 0L) null else Pointer.createConstant(address)
                }
            }
            procCallback = callback
            // mpv_opengl_init_params is exactly two pointers in libmpv 0.41.
            val glInit = Memory(2L * Native.POINTER_SIZE)
            glInit.clear()
            glInit.setPointer(0L, CallbackReference.getFunctionPointer(callback))
            val type = Memory(7)
            type.setString(0, "opengl", "UTF-8")
            val args = MpvGlParams.encode(
                MpvGlParams.API_TYPE to type,
                MpvGlParams.OPENGL_INIT to glInit
            )
            val output = PointerByReference()
            check(nativeApi.mpv_render_context_create(output, mpv, args) >= 0) {
                "libmpv could not create its OpenGL render context."
            }
            val renderer = output.value ?: error("Missing libmpv rendering context.")
            createdRender = renderer
            val notify = object : MpvGpuUpdateCallback {
                override fun invoke(context: Pointer?) {
                    // mpv calls this from an arbitrary worker. Never call
                    // mpv_render_context_update/render from inside callback.
                    onFrame()
                }
            }
            updateCallback = notify
            nativeApi.mpv_render_context_set_update_callback(renderer, notify, null)
            api = nativeApi
            core = mpv
            renderContext = renderer
            ready = true
        } catch (e: Throwable) {
            lastFailure = "GPU setup: " + e.javaClass.simpleName +
                (if (e is IllegalStateException) " — " + e.message.orEmpty().take(100) else "")
            if (createdRender != null) runCatching {
                loadedApi?.mpv_render_context_free(createdRender)
            }
            if (createdCore != null) runCatching {
                loadedApi?.mpv_terminate_destroy(createdCore)
            }
            procCallback = null
            updateCallback = null
        }
    }

    /**
     * Called with current GL context and framebuffer width/height in PHYSICAL
     * pixels, not Compose logical dp. No core command is issued from here.
     */
    fun renderOnGlThread(width: Int, height: Int): Boolean {
        val a = api ?: return false
        val r = renderContext ?: return false
        if (width <= 0 || height <= 0 || disposed) return false
        val fbo = Memory(16L)
        fbo.setInt(0L, 0) // Default framebuffer owned by AWTGLCanvas
        fbo.setInt(4L, width)
        fbo.setInt(8L, height)
        fbo.setInt(12L, 0)
        val flip = Memory(4L)
        flip.setInt(0L, 1)
        val args = MpvGlParams.encode(
            MpvGlParams.OPENGL_FBO to fbo,
            MpvGlParams.FLIP_Y to flip
        )
        val result = a.mpv_render_context_render(r, args)
        if (result >= 0) frameCount++
        else lastFailure = "OpenGL render rejected (mpv error " + result + ")"
        return result >= 0
    }

    fun reportSwapOnGlThread() {
        val a = api ?: return
        val r = renderContext ?: return
        a.mpv_render_context_report_swap(r)
    }

    // Core commands/snapshots must execute from the IO worker, not from
    // the OpenGL canvas render thread.
    @Synchronized
    fun play(source: PlaybackOption): String? {
        val a = api ?: return "GPU renderer is not initialized."
        val mpv = core ?: return "mpv core is not initialized."
        activeUrl = source.url
        pendingAudio = source.audioUrl
        loaded = false
        externalAudio = if (source.audioUrl != null) "awaiting video" else "built-in audio"
        val result = runCatching {
            a.mpv_command(mpv, arrayOf("loadfile", source.url, "replace"))
        }.getOrElse { return "Source rejected (" + it.javaClass.simpleName + ")" }
        return if (result >= 0) null else "Native media command failed (" + result + ")"
    }

    @Synchronized
    fun poll(): MpvGpuStatus? {
        val a = api ?: return null
        val mpv = core ?: return null
        fun prop(name: String): String? {
            val result = a.mpv_get_property_string(mpv, name) ?: return null
            return try { result.getString(0L, "UTF-8") } finally { a.mpv_free(result) }
        }
        return runCatching {
            for (attempt in 0 until 32) {
                val event = a.mpv_wait_event(mpv, 0.0) ?: break
                if (event.getInt(0L) == 0) break
                if (event.getInt(0L) == 8) {
                    loaded = MpvMediaEventGuard.matchesLoadedMedia(
                        8, prop("path"), activeUrl
                    )
                }
            }
            if (loaded && pendingAudio != null) {
                val url = pendingAudio!!
                pendingAudio = null
                val result = a.mpv_command(mpv, arrayOf("audio-add", url, "select"))
                externalAudio = if (result >= 0) "audio-add accepted" else
                    "audio-add error " + result
            }
            val tracks = (prop("track-list/count")?.toIntOrNull() ?: 0).coerceIn(0, 48)
            val audioCount = (0 until tracks).count {
                prop("track-list/" + it + "/type") == "audio"
            }
            MpvGpuStatus(
                video = prop("vid") ?: "unknown",
                audio = prop("aid") ?: "unknown",
                elapsed = prop("time-pos") ?: "0",
                audioTracks = audioCount,
                aVSync = prop("avsync") ?: "unknown",
                renderFrames = frameCount,
                audioAttachment = externalAudio
            )
        }.getOrNull()
    }

    @Synchronized
    fun pause(paused: Boolean) {
        val a = api ?: return
        val mpv = core ?: return
        a.mpv_command(mpv, arrayOf("set", "pause", if (paused) "yes" else "no"))
    }

    @Synchronized
    fun stop() {
        val a = api ?: return
        val mpv = core ?: return
        runCatching { a.mpv_command(mpv, arrayOf("stop")) }
    }

    /**
     * Call only while the OWNED GL context is current and BEFORE destroying
     * libmpv core. The native canvas calls this in disposeGL().
     */
    fun releaseRendererOnGlThread() {
        if (disposed) return
        disposed = true
        ready = false
        val a = api ?: return
        val r = renderContext ?: return
        a.mpv_render_context_set_update_callback(r, null, null)
        a.mpv_render_context_free(r)
        renderContext = null
        updateCallback = null
        procCallback = null
    }

    @Synchronized
    fun destroyCoreAfterRenderer() {
        val a = api ?: return
        val mpv = core ?: return
        core = null
        api = null
        activeUrl = null
        pendingAudio = null
        runCatching { a.mpv_terminate_destroy(mpv) }
    }
}
