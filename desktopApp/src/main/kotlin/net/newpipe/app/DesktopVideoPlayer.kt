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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Desktop
import java.net.URI

/**
 * Native embedded Windows player. M2.1 adds timeout diagnosis and alternate streams.
 * This test build still uses a separately installed 64-bit VLC 3.x.
 */
@Composable
internal fun DesktopVideoPlayer(video: VideoResult, onBack: () -> Unit) {
    var options by remember(video.url) { mutableStateOf<List<PlaybackOption>?>(null) }
    var resolveError by remember(video.url) { mutableStateOf<String?>(null) }
    var optionIndex by remember(video.url) { mutableIntStateOf(0) }
    var retryNumber by remember(video.url) { mutableIntStateOf(0) }
    var playbackIssue by remember(video.url) { mutableStateOf<String?>(null) }
    var status by remember(video.url) { mutableStateOf("Resolving video…") }
    var started by remember(video.url) { mutableStateOf(false) }
    var paused by remember(video.url) { mutableStateOf(false) }
    var position by remember(video.url) { mutableFloatStateOf(0f) }

    LaunchedEffect(video.url) {
        options = null
        resolveError = null
        optionIndex = 0
        try {
            options = withContext(Dispatchers.IO) {
                DesktopVideoSearch.resolvePlaybackOptions(video.url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            resolveError = e.message ?: "Unable to fetch video information."
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Back to results") }
            Spacer(Modifier.width(16.dp))
            Text("Now playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(video.channel, color = Color(0xFFADB4C4))
        Spacer(Modifier.height(18.dp))

        val candidateList = options
        when {
            resolveError != null -> {
                Text("Couldn't resolve this video", color = Color(0xFFFF9898))
                Text(resolveError.orEmpty())
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { openBrowser(video.url) }) {
                    Text("Open in browser")
                }
            }
            candidateList == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(14.dp))
                    Text("Checking available video streams…")
                }
            }
            candidateList.isEmpty() -> {
                Text("No compatible video streams were found.", color = Color(0xFFFF9898))
            }
            else -> {
                val selectedIndex = optionIndex.coerceIn(0, candidateList.lastIndex)
                val candidate = candidateList[selectedIndex]
                val player = remember(candidate.url, retryNumber) {
                    runCatching { EmbeddedMediaPlayerComponent() }.getOrNull()
                }

                if (player == null) {
                    Text("VLC's embedded video engine could not be loaded", color = Color(0xFFFF9898))
                    Spacer(Modifier.height(8.dp))
                    Text("Install or repair VLC 3.x 64-bit, close the app, and try again.")
                    OutlinedButton(onClick = { openBrowser(video.url) }) { Text("Open in browser") }
                } else {
                    fun nextStream() {
                        if (optionIndex < candidateList.lastIndex) {
                            optionIndex += 1
                            playbackIssue = null
                            started = false
                            paused = false
                            position = 0f
                        }
                    }

                    DisposableEffect(player) {
                        onDispose {
                            runCatching { player.mediaPlayer().controls().stop() }
                            runCatching { player.release() }
                        }
                    }

                    LaunchedEffect(player, candidate.url) {
                        status = "Connecting to " + candidate.label + " (" +
                            (selectedIndex + 1).toString() + "/" + candidateList.size.toString() + ")"
                        playbackIssue = null
                        started = false
                        paused = false
                        position = 0f

                        try {
                            val accepted = withContext(Dispatchers.IO) {
                                player.mediaPlayer().media().play(candidate.url)
                            }
                            var reason: String? = if (!accepted) {
                                "VLC refused to open this stream."
                            } else null

                            if (accepted) {
                                // libVLC can accept an URL and then fail asynchronously.
                                // Polling detects a silent black screen without requiring
                                // VLC callbacks on Compose's UI thread.
                                for (tick in 0 until 40) {
                                    delay(500)
                                    val current = runCatching {
                                        player.mediaPlayer().status().state().toString()
                                    }.getOrDefault("UNKNOWN")
                                    val playing = runCatching {
                                        player.mediaPlayer().status().isPlaying
                                    }.getOrDefault(false)
                                    if (playing) {
                                        started = true
                                        status = "Playing " + candidate.label
                                        break
                                    }
                                    if (current.equals("ERROR", ignoreCase = true)) {
                                        reason = "VLC reported a playback error."
                                        break
                                    }
                                }
                                if (!started && reason == null) {
                                    reason = "No playback detected after 20 seconds."
                                }
                            }

                            if (!started && reason != null) {
                                if (optionIndex < candidateList.lastIndex) {
                                    status = reason + " Trying a different stream…"
                                    nextStream()
                                } else {
                                    status = "Unable to play video"
                                    playbackIssue = reason + " All available streams have been tried."
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (optionIndex < candidateList.lastIndex) {
                                status = "Stream failed. Trying a different format…"
                                nextStream()
                            } else {
                                status = "Unable to play video"
                                playbackIssue = e.message ?: "VLC couldn't start playback."
                            }
                        }
                    }

                    LaunchedEffect(player) {
                        while (isActive) {
                            delay(500)
                            runCatching {
                                player.mediaPlayer().status().position()
                            }.onSuccess {
                                if (it.isFinite() && it >= 0f) position = it
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().height(420.dp).background(Color.Black)) {
                        SwingPanel(factory = { player }, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        status,
                        color = if (playbackIssue != null) Color(0xFFFF9898) else Color(0xFFADB4C4),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (playbackIssue != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(playbackIssue.orEmpty(), color = Color(0xFFFF9898))
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = position.coerceIn(0f, 1f),
                        onValueChange = { position = it },
                        onValueChangeFinished = {
                            runCatching { player.mediaPlayer().controls().setPosition(position) }
                        },
                        enabled = started && runCatching {
                            player.mediaPlayer().status().isSeekable
                        }.getOrDefault(false),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = started, onClick = {
                            if (paused) player.mediaPlayer().controls().play()
                            else player.mediaPlayer().controls().pause()
                            paused = !paused
                        }) { Text(if (paused) "▶ Resume" else "⏸ Pause") }

                        OutlinedButton(onClick = {
                            player.mediaPlayer().controls().stop()
                            paused = true
                        }) { Text("Stop") }

                        OutlinedButton(onClick = {
                            retryNumber += 1
                            playbackIssue = null
                            status = "Retrying the current stream…"
                        }) { Text("Retry") }

                        if (selectedIndex < candidateList.lastIndex) {
                            OutlinedButton(onClick = { nextStream() }) {
                                Text("Try another format")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { openBrowser(video.url) }) {
                        Text("Open in browser ↗")
                    }
                }
            }
        }
    }
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
