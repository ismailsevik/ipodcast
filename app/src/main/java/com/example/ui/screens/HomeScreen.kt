package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Episode
import com.example.model.Podcast
import com.example.ui.components.EpisodeItem
import com.example.ui.theme.GoogleBlue

@Composable
fun HomeScreen(
    subscribedPodcasts: List<Podcast>,
    episodes: List<Episode>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onNavigateToSearch: () -> Unit,
    onSelectPodcast: (Podcast) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleQueue: (String) -> Unit,
    onToggleDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subscribedPodcasts.isEmpty()) {
        // Minimalist clean initial state
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = GoogleBlue,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "iPodcast'e Hoş Geldiniz",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Milyonlarca gerçek podcast arasından arama yapın veya RSS bağlantısı ekleyerek abone olun.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToSearch,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(48.dp)
                    .testTag("home_search_button")
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Podcast Ara ve Keşfet", fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Google Podcasts Subscriptions Top Carousel
            item {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    Text(
                        text = "Abonelikleriniz (${subscribedPodcasts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(subscribedPodcasts, key = { it.id }) { podcast ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { onSelectPodcast(podcast) }
                                    .testTag("subscription_avatar_${podcast.id}")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    AsyncImage(
                                        model = podcast.coverUrl,
                                        contentDescription = podcast.title,
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(GoogleBlue)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = podcast.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: Son Bölümler
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aboneliklerinizden Son Bölümler",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (episodes.isEmpty()) {
                item {
                    Text(
                        text = "Bölümler güncelleniyor...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(episodes, key = { it.id }) { ep ->
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
}
