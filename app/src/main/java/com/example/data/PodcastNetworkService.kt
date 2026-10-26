package com.example.data

import android.util.Log
import android.util.Xml
import com.example.model.Episode
import com.example.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PodcastNetworkService {

    private val tag = "PodcastNetworkService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchPodcasts(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&media=podcast&entity=podcast&limit=30"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "iPodcast/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                val list = mutableListOf<Podcast>()

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val id = item.optLong("collectionId", 0L).toString()
                    val title = item.optString("collectionName", "").trim()
                    val author = item.optString("artistName", "").trim()
                    val feedUrl = item.optString("feedUrl", "").trim()
                    val cover = item.optString("artworkUrl600", item.optString("artworkUrl100", ""))

                    if (title.isNotEmpty() && feedUrl.isNotEmpty()) {
                        list.add(
                            Podcast(
                                id = id,
                                title = title,
                                author = author,
                                description = "",
                                coverUrl = cover,
                                feedUrl = feedUrl,
                                isSubscribed = false
                            )
                        )
                    }
                }
                list
            }
        } catch (e: Exception) {
            Log.e(tag, "Search failed for query: $query", e)
            emptyList()
        }
    }

    suspend fun fetchEpisodes(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val feedUrl = podcast.feedUrl
        if (feedUrl.isBlank()) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "iPodcast/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val xml = response.body?.string() ?: return@withContext emptyList()
                parseRssXml(xml, podcast)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch episodes for ${podcast.title} from $feedUrl", e)
            emptyList()
        }
    }

    suspend fun fetchPodcastFromRssUrl(rssUrl: String): Pair<Podcast?, List<Episode>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(rssUrl.trim())
                .header("User-Agent", "iPodcast/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Pair(null, emptyList())
                val xml = response.body?.string() ?: return@withContext Pair(null, emptyList())

                val (channelInfo, episodes) = parseRssFeedAndPodcast(xml, rssUrl.trim())
                Pair(channelInfo, episodes)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse direct RSS URL $rssUrl", e)
            Pair(null, emptyList())
        }
    }

    private fun parseRssXml(xml: String, podcast: Podcast): List<Episode> {
        val episodes = mutableListOf<Episode>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inItem = false

            var epTitle = ""
            var epDesc = ""
            var epAudioUrl = ""
            var epPubDate = ""
            var epDurationSec = 0
            var epCover = podcast.coverUrl

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "item" -> {
                                inItem = true
                                epTitle = ""
                                epDesc = ""
                                epAudioUrl = ""
                                epPubDate = ""
                                epDurationSec = 0
                                epCover = podcast.coverUrl
                            }
                            "title" -> if (inItem && epTitle.isEmpty()) {
                                epTitle = cleanHtml(parser.nextText())
                            }
                            "description", "summary" -> if (inItem && epDesc.isEmpty()) {
                                epDesc = cleanHtml(parser.nextText())
                            }
                            "pubdate" -> if (inItem && epPubDate.isEmpty()) {
                                epPubDate = formatPubDate(parser.nextText())
                            }
                            "enclosure" -> if (inItem) {
                                val url = parser.getAttributeValue(null, "url") ?: ""
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                if (url.isNotEmpty() && (type.contains("audio") || url.endsWith(".mp3") || url.endsWith(".m4a") || epAudioUrl.isEmpty())) {
                                    epAudioUrl = url
                                }
                            }
                            "duration" -> if (inItem) {
                                epDurationSec = parseDuration(parser.nextText())
                            }
                            "image" -> if (inItem) {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) epCover = href
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "item" && inItem) {
                            if (epTitle.isNotEmpty() && epAudioUrl.isNotEmpty()) {
                                val episodeId = "ep_" + Math.abs((podcast.id + epTitle + epAudioUrl).hashCode()).toString()
                                episodes.add(
                                    Episode(
                                        id = episodeId,
                                        podcastId = podcast.id,
                                        podcastTitle = podcast.title,
                                        podcastCoverUrl = epCover.ifBlank { podcast.coverUrl },
                                        title = epTitle,
                                        description = epDesc,
                                        audioUrl = epAudioUrl,
                                        durationSeconds = epDurationSec,
                                        publishedDate = epPubDate.ifEmpty { "Son Bölüm" }
                                    )
                                )
                            }
                            inItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing RSS XML", e)
        }
        return episodes
    }

    private fun parseRssFeedAndPodcast(xml: String, feedUrl: String): Pair<Podcast?, List<Episode>> {
        var channelTitle = ""
        var channelAuthor = ""
        var channelDesc = ""
        var channelImage = ""

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            var inChannel = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "channel") inChannel = true
                        if (inChannel) {
                            when (tag) {
                                "title" -> if (channelTitle.isEmpty()) channelTitle = cleanHtml(parser.nextText())
                                "author" -> if (channelAuthor.isEmpty()) channelAuthor = cleanHtml(parser.nextText())
                                "description" -> if (channelDesc.isEmpty()) channelDesc = cleanHtml(parser.nextText())
                                "image" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank() && channelImage.isEmpty()) channelImage = href
                                }
                            }
                        }
                        if (tag == "item") break // Done reading channel metadata
                    }
                }
                eventType = parser.next()
            }

            if (channelTitle.isNotEmpty()) {
                val pod = Podcast(
                    id = "rss_" + Math.abs(feedUrl.hashCode()),
                    title = channelTitle,
                    author = channelAuthor.ifEmpty { "Podcast Yayıncısı" },
                    description = channelDesc,
                    coverUrl = channelImage,
                    feedUrl = feedUrl,
                    isSubscribed = true
                )
                val episodes = parseRssXml(xml, pod)
                return Pair(pod.copy(episodesCount = episodes.size), episodes)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing RSS channel", e)
        }
        return Pair(null, emptyList())
    }

    private fun cleanHtml(raw: String): String {
        return raw.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun formatPubDate(rawDate: String): String {
        val trimmed = rawDate.trim()
        val parts = trimmed.split(" ")
        return if (parts.size >= 4) {
            "${parts[1]} ${parts[2]} ${parts[3]}"
        } else {
            trimmed.take(16)
        }
    }

    private fun parseDuration(raw: String): Int {
        val trimmed = raw.trim()
        return try {
            if (trimmed.contains(":")) {
                val tokens = trimmed.split(":")
                if (tokens.size == 2) {
                    val mins = tokens[0].toIntOrNull() ?: 0
                    val secs = tokens[1].toIntOrNull() ?: 0
                    mins * 60 + secs
                } else if (tokens.size == 3) {
                    val hours = tokens[0].toIntOrNull() ?: 0
                    val mins = tokens[1].toIntOrNull() ?: 0
                    val secs = tokens[2].toIntOrNull() ?: 0
                    hours * 3600 + mins * 60 + secs
                } else 0
            } else {
                trimmed.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
