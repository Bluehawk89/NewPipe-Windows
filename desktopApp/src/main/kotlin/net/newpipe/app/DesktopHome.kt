/*
 * SPDX-FileCopyrightText: 2026 NewPipe Windows contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.newpipe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.text.NumberFormat

private val accent = Color(0xFFE84752)
private val background = Color(0xFF0F1118)
private val sidebar = Color(0xFF171B25)
private val panel = Color(0xFF202534)
private val muted = Color(0xFFADB4C4)

private enum class Section(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    SUBSCRIPTIONS("Subscriptions"),
    HISTORY("History"),
    DOWNLOADS("Downloads")
}

@Composable
internal fun DesktopHome(onAbout: () -> Unit) {
    var section by remember { mutableStateOf(Section.HOME) }
    var query by remember { mutableStateOf("") }
    var searchedTerm by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoResult?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun doSearch(term: String = query) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        query = clean
        searchedTerm = clean
        section = Section.SEARCH
        loading = true
        error = null
        results = emptyList()
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { DesktopVideoSearch.search(clean) }
                results = found
            } catch (ex: Exception) {
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                error = ex.message ?: "Search failed. Please try again."
            } finally {
                loading = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = background,
            onBackground = Color.White,
            surface = sidebar,
            onSurface = Color.White
        )
    ) {
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Row {
                Column(
                    modifier = Modifier.width(225.dp).fillMaxHeight().background(sidebar).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = accent, shape = RoundedCornerShape(11.dp)) {
                            Text("▶", color = Color.White, modifier = Modifier.padding(11.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("NewPipe", fontWeight = FontWeight.Bold)
                            Text("WINDOWS · ALPHA", color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    Section.entries.forEach { entry ->
                        SidebarItem(
                            title = entry.label,
                            selected = section == entry,
                            onClick = {
                                selectedVideo = null
                                section = entry
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = Color(0xFF353B49))
                    Spacer(Modifier.height(12.dp))
                    SidebarItem("About & licenses", false, onClick = onAbout)
                    Text(
                        "Privacy-minded video browsing",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text("Search YouTube") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() })
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { doSearch() }, enabled = query.isNotBlank() && !loading) {
                            Text("Search")
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    val selected = selectedVideo
                    if (selected != null) {
                        DesktopVideoPlayer(video = selected, onBack = {
                            selectedVideo = null
                            section = Section.SEARCH
                        })
                    } else when (section) {
                        Section.HOME -> HomeWelcome(onQuickSearch = { doSearch(it) })
                        Section.SEARCH -> {
                            Text(
                                if (searchedTerm.isEmpty()) "Search" else "Results for \"$searchedTerm\"",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(16.dp))
                            when {
                                loading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Text("Searching with NewPipe Extractor…", color = muted)
                                    }
                                }
                                error != null -> {
                                    Text("Search could not be completed", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(error.orEmpty(), color = muted)
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedButton(onClick = { doSearch(searchedTerm) }) {
                                        Text("Try again")
                                    }
                                }
                                searchedTerm.isEmpty() -> Text("Type a query above to search YouTube.", color = muted)
                                results.isEmpty() -> Text("No videos found for this search.", color = muted)
                                else -> {
                                    Text(
                                        "${results.size} video results • Open in browser temporarily; native playback is next.",
                                        color = muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(results) { video ->
                                            VideoRow(video, onPlay = { selectedVideo = video })
                                        }
                                    }
                                }
                            }
                        }
                        else -> FeatureComingSoon(section.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF343040) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (selected) Color.White else muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun HomeWelcome(onQuickSearch: (String) -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text("Your videos. Your desktop.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Search without signing into YouTube. Built with NewPipe Extractor and a real Windows desktop interface.",
        color = muted
    )
    Spacer(Modifier.height(36.dp))
    Surface(color = panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("Find something to watch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Enter anything in the search bar above, or try a topic:", color = muted)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Nature documentaries", "Technology", "Live music").forEach { topic ->
                    OutlinedButton(onClick = { onQuickSearch(topic) }) { Text(topic) }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "Coming next: built-in playback, subscriptions, history and downloads.",
        color = muted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun FeatureComingSoon(label: String) {
    Spacer(Modifier.height(24.dp))
    Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("This feature is planned for an upcoming milestone.", color = muted)
    Text("Search is available now using the bar above.", color = muted)
}

@Composable
private fun VideoRow(video: VideoResult, onPlay: () -> Unit) {
    var openError by remember(video.url) { mutableStateOf(false) }
    Surface(
        color = panel,
        border = BorderStroke(1.dp, Color(0xFF353B49)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(192.dp).height(108.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF30384B))) {
                    if (video.thumbnail != null) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = "Thumbnail for ${video.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(video.channel.ifBlank { "YouTube" }, color = muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (video.durationSeconds > 0L) append(formatDuration(video.durationSeconds))
                            if (video.views >= 0L) {
                                if (isNotEmpty()) append("  ·  ")
                                append(NumberFormat.getIntegerInstance().format(video.views))
                                append(" views")
                            }
                        },
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = onPlay) { Text("▶ Play in app") }
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(onClick = {
                        openError = runCatching {
                            if (!Desktop.isDesktopSupported()) error("Desktop browsing is unavailable")
                            Desktop.getDesktop().browse(URI(video.url))
                        }.isFailure
                    }) { Text("Open video in browser ↗") }
                }
            }
            if (openError) {
                Text(
                    "Unable to open your browser. You can copy the link from GitHub later.",
                    color = Color(0xFFFF9A9A),
                    modifier = Modifier.padding(start = 14.dp, bottom = 12.dp)
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
    else "%d:%02d".format(minutes, remaining)
}
