package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Episode
import com.example.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PodcastRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ipodcast_prefs", Context.MODE_PRIVATE)
    private val networkService = PodcastNetworkService()
    val downloadManager = PodcastDownloadManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Subscribed podcasts (persisted)
    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val podcasts: StateFlow<List<Podcast>> = _podcasts.asStateFlow()

    // Episodes across subscribed and downloaded podcasts
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    // Real search results state
    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    val searchResults: StateFlow<List<Podcast>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes: StateFlow<Boolean> = _isLoadingEpisodes.asStateFlow()

    init {
        loadPersistedSubscriptions()
        loadPersistedDownloadedEpisodes()
    }

    private fun loadPersistedSubscriptions() {
        try {
            val jsonStr = prefs.getString("subscribed_podcasts", null)
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Podcast>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Podcast(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            author = obj.optString("author", ""),
                            description = obj.optString("description", ""),
                            coverUrl = obj.optString("coverUrl", ""),
                            feedUrl = obj.optString("feedUrl", ""),
                            isSubscribed = true
                        )
                    )
                }
                _podcasts.value = list

                scope.launch {
                    refreshSubscribedEpisodes(list)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPersistedDownloadedEpisodes() {
        try {
            val jsonStr = prefs.getString("downloaded_episodes_list", null)
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Episode>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val localPath = obj.optString("localFilePath", "")
                    if (localPath.isNotEmpty() && File(localPath).exists()) {
                        list.add(
                            Episode(
                                id = obj.getString("id"),
                                podcastId = obj.getString("podcastId"),
                                podcastTitle = obj.getString("podcastTitle"),
                                podcastCoverUrl = obj.optString("podcastCoverUrl", ""),
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                audioUrl = obj.getString("audioUrl"),
                                durationSeconds = obj.optInt("durationSeconds", 0),
                                publishedDate = obj.optString("publishedDate", ""),
                                isDownloaded = true,
                                localFilePath = localPath
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    _episodes.update { current ->
                        val existingIds = current.map { it.id }.toSet()
                        val newOnly = list.filter { !existingIds.contains(it.id) }
                        current + newOnly
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistSubscriptions(list: List<Podcast>) {
        try {
            val array = JSONArray()
            list.filter { it.isSubscribed }.forEach { pod ->
                val obj = JSONObject().apply {
                    put("id", pod.id)
                    put("title", pod.title)
                    put("author", pod.author)
                    put("description", pod.description)
                    put("coverUrl", pod.coverUrl)
                    put("feedUrl", pod.feedUrl)
                }
                array.put(obj)
            }
            prefs.edit().putString("subscribed_podcasts", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistDownloads(list: List<Episode>) {
        try {
            val array = JSONArray()
            list.filter { it.isDownloaded && !it.localFilePath.isNullOrBlank() }.forEach { ep ->
                val obj = JSONObject().apply {
                    put("id", ep.id)
                    put("podcastId", ep.podcastId)
                    put("podcastTitle", ep.podcastTitle)
                    put("podcastCoverUrl", ep.podcastCoverUrl)
                    put("title", ep.title)
                    put("description", ep.description)
                    put("audioUrl", ep.audioUrl)
                    put("durationSeconds", ep.durationSeconds)
                    put("publishedDate", ep.publishedDate)
                    put("localFilePath", ep.localFilePath)
                }
                array.put(obj)
            }
            prefs.edit().putString("downloaded_episodes_list", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun searchRealPodcasts(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        val results = if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true)) {
            val (pod, _) = networkService.fetchPodcastFromRssUrl(query)
            if (pod != null) listOf(pod) else emptyList()
        } else {
            networkService.searchPodcasts(query)
        }

        val subscribedIds = _podcasts.value.map { it.id }.toSet()
        val mapped = results.map { it.copy(isSubscribed = subscribedIds.contains(it.id)) }

        _searchResults.value = mapped
        _isSearching.value = false
    }

    suspend fun getEpisodesForPodcast(podcast: Podcast): List<Episode> {
        _isLoadingEpisodes.value = true
        val fetched = networkService.fetchEpisodes(podcast)
        _isLoadingEpisodes.value = false

        val existingMap = _episodes.value.associateBy { it.id }
        val merged = fetched.map { ep ->
            val existing = existingMap[ep.id]
            if (existing != null) {
                ep.copy(
                    isInQueue = existing.isInQueue,
                    isDownloaded = existing.isDownloaded,
                    localFilePath = existing.localFilePath,
                    progressSeconds = existing.progressSeconds,
                    isCompleted = existing.isCompleted
                )
            } else ep
        }

        _episodes.update { current ->
            val nonPodcastEps = current.filter { it.podcastId != podcast.id }
            (merged + nonPodcastEps)
        }

        return merged
    }

    private suspend fun refreshSubscribedEpisodes(subscriptions: List<Podcast>) {
        val allEpisodes = mutableListOf<Episode>()
        val existingDownloads = _episodes.value.filter { it.isDownloaded }.associateBy { it.id }

        for (pod in subscriptions) {
            val eps = networkService.fetchEpisodes(pod)
            val mapped = eps.map { ep ->
                val dl = existingDownloads[ep.id]
                if (dl != null) {
                    ep.copy(isDownloaded = true, localFilePath = dl.localFilePath)
                } else ep
            }
            allEpisodes.addAll(mapped)
        }
        _episodes.value = allEpisodes
    }

    fun toggleSubscription(podcast: Podcast): Boolean {
        val exists = _podcasts.value.any { it.id == podcast.id }
        val newSubState = !exists

        if (newSubState) {
            val updatedPod = podcast.copy(isSubscribed = true)
            _podcasts.update { it + updatedPod }
            scope.launch {
                getEpisodesForPodcast(updatedPod)
            }
        } else {
            _podcasts.update { current -> current.filter { it.id != podcast.id } }
        }

        persistSubscriptions(_podcasts.value)

        _searchResults.update { list ->
            list.map { if (it.id == podcast.id) it.copy(isSubscribed = newSubState) else it }
        }

        return newSubState
    }

    fun addPodcastByRss(rssUrl: String, onComplete: (Boolean, String) -> Unit) {
        scope.launch {
            _isSearching.value = true
            val (pod, eps) = networkService.fetchPodcastFromRssUrl(rssUrl)
            _isSearching.value = false

            if (pod != null) {
                _podcasts.update { it + pod }
                _episodes.update { eps + it }
                persistSubscriptions(_podcasts.value)
                onComplete(true, "${pod.title} başarıyla eklendi!")
            } else {
                onComplete(false, "Geçersiz veya okunamayan RSS adresi.")
            }
        }
    }

    fun toggleQueue(episodeId: String) {
        _episodes.update { list ->
            list.map { ep ->
                if (ep.id == episodeId) ep.copy(isInQueue = !ep.isInQueue) else ep
            }
        }
    }

    fun toggleDownload(episode: Episode, onStatus: (String) -> Unit) {
        val currentEp = _episodes.value.find { it.id == episode.id } ?: episode

        if (currentEp.isDownloaded) {
            // Delete downloaded file from storage
            downloadManager.deleteDownloadedFile(currentEp.localFilePath)
            _episodes.update { list ->
                list.map {
                    if (it.id == episode.id) {
                        it.copy(
                            isDownloaded = false,
                            localFilePath = null,
                            downloadProgress = 0f,
                            isDownloading = false
                        )
                    } else it
                }
            }
            persistDownloads(_episodes.value)
            onStatus("İndirilen dosya cihazdan silindi")
        } else {
            // Start downloading to device storage
            _episodes.update { list ->
                list.map {
                    if (it.id == episode.id) it.copy(isDownloading = true, downloadProgress = 0.05f) else it
                }
            }
            onStatus("İndirme başlatıldı...")

            scope.launch {
                val savedPath = downloadManager.downloadEpisode(currentEp) { progress ->
                    _episodes.update { list ->
                        list.map {
                            if (it.id == episode.id) it.copy(downloadProgress = progress) else it
                        }
                    }
                }

                if (savedPath != null) {
                    _episodes.update { list ->
                        list.map {
                            if (it.id == episode.id) {
                                it.copy(
                                    isDownloaded = true,
                                    isDownloading = false,
                                    localFilePath = savedPath,
                                    downloadProgress = 1f
                                )
                            } else it
                        }
                    }
                    persistDownloads(_episodes.value)
                    onStatus("Bölüm cihaz hafızasına indirildi! (Çevrimdışı hazır)")
                } else {
                    _episodes.update { list ->
                        list.map {
                            if (it.id == episode.id) it.copy(isDownloading = false, downloadProgress = 0f) else it
                        }
                    }
                    onStatus("İndirme başarısız oldu. Lütfen tekrar deneyin.")
                }
            }
        }
    }

    fun updateProgress(episodeId: String, currentSeconds: Int) {
        _episodes.update { list ->
            list.map { ep ->
                if (ep.id == episodeId) {
                    val isCompleted = ep.durationSeconds > 0 && currentSeconds >= ep.durationSeconds - 5
                    ep.copy(
                        progressSeconds = currentSeconds,
                        isCompleted = isCompleted
                    )
                } else ep
            }
        }
    }

    fun markAsPlayed(episodeId: String) {
        _episodes.update { list ->
            list.map { ep ->
                if (ep.id == episodeId) {
                    ep.copy(progressSeconds = ep.durationSeconds, isCompleted = true)
                } else ep
            }
        }
    }

    fun getDownloadedStorageSizeFormatted(): String {
        val bytes = downloadManager.getTotalDownloadedBytes()
        val mb = bytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }

    fun clearAllDownloadedEpisodes(): Boolean {
        downloadManager.clearAllDownloads()
        _episodes.update { list ->
            list.map { it.copy(isDownloaded = false, localFilePath = null, downloadProgress = 0f) }
        }
        persistDownloads(_episodes.value)
        return true
    }
}
