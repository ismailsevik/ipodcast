package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Episode
import com.example.model.Podcast
import com.example.ui.components.EpisodeItem

@Composable
fun LibraryScreen(
    queueEpisodes: List<Episode>,
    downloadedEpisodes: List<Episode>,
    historyEpisodes: List<Episode>,
    subscribedPodcasts: List<Podcast>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onSelectPodcast: (Podcast) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleQueue: (String) -> Unit,
    onToggleDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Dinleme Sırası", "İndirilenler", "Geçmiş", "Abonelikler")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Sub tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp,
            divider = {},
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("library_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tab Content
        when (selectedTabIndex) {
            0 -> {
                // Queue Tab
                if (queueEpisodes.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.PlaylistPlay,
                        title = "Dinleme sıranız boş",
                        description = "Bölümlerin yanındaki '+' ikonuna dokunarak sıraya ekleyebilirsiniz."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(queueEpisodes, key = { it.id }) { ep ->
                            val isCurrent = ep.id == currentPlayingEpisodeId
                            EpisodeItem(
                                episode = ep,
                                isCurrentPlaying = isCurrent && isPlaying,
                                onPlayClick = { onPlayEpisode(ep) },
                                onItemClick = { onPlayEpisode(ep) },
                                onToggleQueue = { onToggleQueue(ep.id) },
                                onToggleDownload = { onToggleDownload(ep.id) }
                            )
                        }
                    }
                }
            }

            1 -> {
                // Downloads Tab
                if (downloadedEpisodes.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Download,
                        title = "İndirilen bölüm yok",
                        description = "Bölümleri indirerek internetsiz her yerde çevrimdışı dinleyin."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(downloadedEpisodes, key = { it.id }) { ep ->
                            val isCurrent = ep.id == currentPlayingEpisodeId
                            EpisodeItem(
                                episode = ep,
                                isCurrentPlaying = isCurrent && isPlaying,
                                onPlayClick = { onPlayEpisode(ep) },
                                onItemClick = { onPlayEpisode(ep) },
                                onToggleQueue = { onToggleQueue(ep.id) },
                                onToggleDownload = { onToggleDownload(ep.id) }
                            )
                        }
                    }
                }
            }

            2 -> {
                // History Tab
                if (historyEpisodes.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "Dinleme geçmişi boş",
                        description = "Dinlemeye başladığınız bölümler kaldığınız yerle birlikte burada listelenir."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(historyEpisodes, key = { it.id }) { ep ->
                            val isCurrent = ep.id == currentPlayingEpisodeId
                            EpisodeItem(
                                episode = ep,
                                isCurrentPlaying = isCurrent && isPlaying,
                                onPlayClick = { onPlayEpisode(ep) },
                                onItemClick = { onPlayEpisode(ep) },
                                onToggleQueue = { onToggleQueue(ep.id) },
                                onToggleDownload = { onToggleDownload(ep.id) }
                            )
                        }
                    }
                }
            }

            3 -> {
                // Subscriptions Grid
                if (subscribedPodcasts.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Subscriptions,
                        title = "Henüz bir podcast'e abone olmadınız",
                        description = "Keşfet sekmesinden dilediğiniz yayına abone olabilirsiniz."
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(subscribedPodcasts, key = { it.id }) { pod ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onSelectPodcast(pod) }
                                    .testTag("grid_podcast_${pod.id}")
                            ) {
                                AsyncImage(
                                    model = pod.coverUrl,
                                    contentDescription = pod.title,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pod.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = pod.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
