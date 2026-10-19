/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Canvas
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Disposable proof-of-concept comparing libmpv decoding and synchronization
 * with the baseline VLC implementation. Does NOT qualify as a final renderer:
 * mpv is embedded through an AWT HWND; its GPU render API remains the target.
 */
@Composable
internal fun DesktopMpvComparisonPlayer(video: VideoResult) {
    var candidates by remember(video.url) { mutableStateOf<List<PlaybackOption>>(emptyList()) }
    var selectedIndex by remember(video.url) { mutableIntStateOf(0) }
    val source = candidates.getOrNull(selectedIndex)
    var error by remember(video.url) { mutableStateOf<String?>(null) }
    var reading by remember(video.url) { mutableStateOf<MpvReading?>(null) }
    var volume by remember(video.url) { mutableFloatStateOf(100f) }
    var copied by remember(video.url) { mutableStateOf(false) }
    var audioHttpReport by remember(video.url) { mutableStateOf("not checked") }
    val surface = remember(video.url) {
        Canvas().apply { background = java.awt.Color.BLACK }
    }
    val controller = remember(video.url) { MpvNativeController() }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) {
        onDispose {
            // Native termination can block while decoder threads wind down;
            // never run that operation on Compose's UI/render thread.
            Thread({
                // Mute the old session promptly before native teardown
                // completes. Search/navigation must not leave soundtrack
                // playing after the video surface disappears.
                controller.pause(true)
                controller.close()
            }, "mpv-test-dispose").apply {
                isDaemon = true
                start()
            }
        }
    }

    LaunchedEffect(video.url) {
        error = null
        reading = null
        try {
            candidates = withContext(Dispatchers.IO) {
                MpvStreamSelection.prioritize(
                    DesktopVideoSearch.resolvePlaybackOptions(video.url)
                )
            }
            selectedIndex = 0
            if (candidates.isEmpty()) error = "NewPipe Extractor did not find a playable format."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Stream extraction failed; try the VLC baseline player."
        }
    }

    // The mpv HWND must already be attached to the visible SwingPanel before
    // we supply that window id to libmpv.
    LaunchedEffect(surface, source) {
        val candidate = source ?: return@LaunchedEffect
        error = null
        reading = null
        audioHttpReport = "not checked"
        var ready = false
        for (attempt in 0 until 80) {
            if (surface.isShowing && surface.width > 0 && surface.height > 0) {
                ready = true
                break
            }
            delay(100)
        }
        if (!ready) {
            error = "Windows video surface could not attach to the application."
            return@LaunchedEffect
        }
        error = withContext(Dispatchers.IO) {
            // Switching formats must not dispose the embedded player or
            // recreate its native HWND. Keep one mpv session per viewport.
            controller.openOrSwitch(surface, candidate)
        }
        if (error != null && selectedIndex < candidates.lastIndex) {
            // Immediate native rejection: try another source once rather than
            // showing a dead player when alternatives are still available.
            selectedIndex += 1
            return@LaunchedEffect
        }
        if (error == null) {
            // A successful mpv command only means an input was opened.
            // An HLS master may still produce no picture/sound, or silent
            // video. Move on after a grace period instead of requiring the
            // user to manually skip a known unusable option.
            var secondsWithoutTracks = 0
            var secondsVideoOnly = 0
            while (isActive) {
                delay(1000)
                val current = withContext(Dispatchers.IO) { controller.snapshot() }
                reading = current
                if (current == null) {
                    secondsWithoutTracks = (secondsWithoutTracks + 1).coerceAtMost(15)
                    if (MpvStartupRecovery.advance(
                            optionsRemain = selectedIndex < candidates.lastIndex,
                            paused = false,
                            secondsWithoutTracks = secondsWithoutTracks,
                            secondsVideoOnly = 0
                        )
                    ) {
                        selectedIndex += 1
                        break
                    }
                    continue
                }
                if (current.isPaused) continue
                val noVideo = current.activeVideo == "no" ||
                    current.activeVideo == "unknown"
                val noAudio = current.activeAudio == "no" ||
                    current.activeAudio == "unknown"
                secondsWithoutTracks = if (noVideo && noAudio) {
                    (secondsWithoutTracks + 1).coerceAtMost(15)
                } else 0
                // Wait until video actually advances; do not misclassify
                // the initial buffering period as permanent audio failure.
                secondsVideoOnly = if (!noVideo && noAudio &&
                    (current.timeSeconds ?: 0.0) > 2.0) {
                    (secondsVideoOnly + 1).coerceAtMost(12)
                } else 0
                if (MpvStartupRecovery.advance(
                        optionsRemain = selectedIndex < candidates.lastIndex,
                        paused = current.isPaused,
                        secondsWithoutTracks = secondsWithoutTracks,
                        secondsVideoOnly = secondsVideoOnly
                    )
                ) {
                    selectedIndex += 1
                    break
                }
            }
        }
    }

    // Probe a silent separate audio rendition once, after mpv has had time
    // to load it. The probe verifies network access, not audible sound.
    LaunchedEffect(source, reading?.activeAudio) {
        val audioUrl = source?.audioUrl
        if (audioUrl != null && reading?.activeAudio == "no" &&
            audioHttpReport == "not checked") {
            delay(7000)
            if (reading?.activeAudio == "no") {
                audioHttpReport = "checking"
                audioHttpReport = withContext(Dispatchers.IO) {
                    MpvAudioProbe.inspect(audioUrl)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("mpv experimental engine — native Windows A/B playback test")
        Text(video.title, color = Color(0xFFDEE4F1))
        Text(
            "This compares decoding, audio and stability. Its embedded-window renderer " +
                "is not our proposed final GPU renderer.",
            color = Color(0xFFADB4C4)
        )
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            SwingPanel(factory = { surface }, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth().height(252.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val candidate = source
            Text(
                "Format " + (selectedIndex + 1) + "/" + candidates.size + ": " +
                    (candidate?.label ?: "Resolving…")
            )
            if (error != null) {
                Text(error.orEmpty(), color = Color(0xFFFF9898))
            } else if (reading == null) {
                Text("Preparing embedded mpv player…")
            } else {
                val current = reading
                Text(
                    "Time: " + (current?.timeSeconds?.toInt() ?: 0) + "s" +
                        " | video: " + (current?.activeVideo ?: "?") +
                        " | audio: " + (current?.activeAudio ?: "?")
                )
                Text(
                    "Video width: " + (current?.videoWidth ?: "?") +
                        " | audio sample rate: " + (current?.audioRate ?: "?") +
                        " | A/V offset: " + (current?.aVSync ?: "?")
                )
                Text(
                    "Audio streams found: " + (current?.audioTrackCount ?: 0) +
                        " | audio codec: " + (current?.audioCodec ?: "?") +
                        " | output: " + (current?.audioOutput ?: "?")
                )
                Text("External audio: " + (current?.externalAudioStatus ?: "not checked"))
                if (source?.audioUrl != null) {
                    Text("Audio endpoint: " + audioHttpReport)
                }
                if (current?.activeAudio == "no" ||
                    current?.activeAudio == "0" ||
                    current?.activeAudio == "unknown") {
                    Text(
                        "No active audio track. Select another stream to test the full HLS master or an audio-paired rendition.",
                        color = Color(0xFFFF9898)
                    )
                }
                if (current?.idle == true) {
                    Text("mpv is idle or buffering.", color = Color(0xFFFFBF80))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = selectedIndex < candidates.lastIndex,
                    onClick = { selectedIndex += 1 }
                ) { Text("Try next stream") }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        controller.pause(reading?.isPaused != true)
                    }
                }) { Text(if (reading?.isPaused == true) "Resume" else "Pause") }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) { controller.seekBy(-10) }
                }) { Text("-10 sec") }
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) { controller.seekBy(10) }
                }) { Text("+10 sec") }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume")
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        scope.launch(Dispatchers.IO) { controller.setVolume(it.toInt()) }
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(160.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val current = reading
                    val report = listOf(
                        "Unofficial Windows native mpv M2.8 audio A/B test",
                        "Video: " + video.url,
                        "Format: " + (source?.label ?: "unknown"),
                        "Separate audio declared: " + (source?.audioUrl != null),
                        "Native playback error: " + (error ?: "none"),
                        "Selected option: " + (selectedIndex + 1) + "/" + candidates.size,
                        "External audio command: " + (current?.externalAudioStatus ?: "unknown"),
                        "Audio tracks detected: " + (current?.audioTrackCount ?: 0),
                        "Audio codec: " + (current?.audioCodec ?: "unknown"),
                        "Audio output: " + (current?.audioOutput ?: "unknown"),
                        "Core error: " + (current?.coreError ?: "unknown"),
                        "External audio HTTP: " + audioHttpReport,
                        "Video: " + (current?.activeVideo ?: "unknown"),
                        "Audio: " + (current?.activeAudio ?: "unknown"),
                        "Video width: " + (current?.videoWidth ?: "unknown"),
                        "Audio sample rate: " + (current?.audioRate ?: "unknown"),
                        "A/V offset: " + (current?.aVSync ?: "unknown"),
                        "Playback time: " + (current?.timeSeconds ?: 0.0)
                    ).joinToString("\n")
                    copied = runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            StringSelection(report), null
                        )
                    }.isSuccess
                }) { Text(if (copied) "Copied" else "Copy test report") }
            }
        }
    }
}
