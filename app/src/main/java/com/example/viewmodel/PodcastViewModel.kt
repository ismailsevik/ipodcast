package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AudioPlayerManager
import com.example.data.BillingRepository
import com.example.data.PodcastRepository
import com.example.model.AppSettings
import com.example.model.BillingPlan
import com.example.model.Episode
import com.example.model.PlaybackSpeed
import com.example.model.PlusFeature
import com.example.model.Podcast
import com.example.model.SleepTimerDuration
import com.example.model.SubscriptionState
import com.example.model.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    private val podcastRepo = PodcastRepository(application)
    private val billingRepo = BillingRepository()
    val playerManager = AudioPlayerManager(application)

    private var searchDebounceJob: Job? = null

    init {
        playerManager.onProgressChanged = { episode, progressSec ->
            podcastRepo.updateProgress(episode.id, progressSec)
        }
        playerManager.onEpisodeCompleted = { episode ->
            podcastRepo.markAsPlayed(episode.id)
            playNextInQueue()
        }
    }

    // Subscribed podcasts and episodes
    val podcasts: StateFlow<List<Podcast>> = podcastRepo.podcasts
    val episodes: StateFlow<List<Episode>> = podcastRepo.episodes

    val searchResults: StateFlow<List<Podcast>> = podcastRepo.searchResults
    val isSearching: StateFlow<Boolean> = podcastRepo.isSearching
    val isLoadingEpisodes: StateFlow<Boolean> = podcastRepo.isLoadingEpisodes

    val subscribedPodcasts: StateFlow<List<Podcast>> = podcasts

    val queueEpisodes: StateFlow<List<Episode>> = episodes.combine(MutableStateFlow(Unit)) { list, _ ->
        list.filter { it.isInQueue }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedEpisodes: StateFlow<List<Episode>> = episodes.combine(MutableStateFlow(Unit)) { list, _ ->
        list.filter { it.isDownloaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyEpisodes: StateFlow<List<Episode>> = episodes.combine(MutableStateFlow(Unit)) { list, _ ->
        list.filter { it.progressSeconds > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player state
    val currentEpisode: StateFlow<Episode?> = playerManager.currentEpisode
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val isLoadingAudio: StateFlow<Boolean> = playerManager.isLoading
    val currentPositionSeconds: StateFlow<Int> = playerManager.currentPositionSeconds
    val durationSeconds: StateFlow<Int> = playerManager.durationSeconds
    val playbackSpeed: StateFlow<PlaybackSpeed> = playerManager.playbackSpeed
    val sleepTimerDuration: StateFlow<SleepTimerDuration> = playerManager.sleepTimerDuration
    val sleepTimerRemaining: StateFlow<Int?> = playerManager.sleepTimerRemaining

    // Minimalist 50 TL Support Billing
    val subscriptionState: StateFlow<SubscriptionState> = billingRepo.subscriptionState
    val supportPlan: BillingPlan = billingRepo.supportPlan
    val plusFeatures: List<PlusFeature> = billingRepo.plusFeatures

    // UI Navigation & Modals
    private val _isBillingSheetOpen = MutableStateFlow(false)
    val isBillingSheetOpen: StateFlow<Boolean> = _isBillingSheetOpen.asStateFlow()

    private val _isProfileSheetOpen = MutableStateFlow(false)
    val isProfileSheetOpen: StateFlow<Boolean> = _isProfileSheetOpen.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val _selectedPodcastDetail = MutableStateFlow<Podcast?>(null)
    val selectedPodcastDetail: StateFlow<Podcast?> = _selectedPodcastDetail.asStateFlow()

    private val _detailEpisodes = MutableStateFlow<List<Episode>>(emptyList())
    val detailEpisodes: StateFlow<List<Episode>> = _detailEpisodes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun openProfileSheet() {
        _isProfileSheetOpen.value = true
    }

    fun closeProfileSheet() {
        _isProfileSheetOpen.value = false
    }

    fun setThemeMode(mode: ThemeMode) {
        _appSettings.update { it.copy(themeMode = mode) }
    }

    fun setDownloadOnlyOnWifi(enabled: Boolean) {
        _appSettings.update { it.copy(downloadOnlyOnWifi = enabled) }
        showToast(if (enabled) "Yalnızca Wi-Fi ile indirme açık" else "Tüm ağlarla indirme açık")
    }

    fun setAutoDeletePlayed(enabled: Boolean) {
        _appSettings.update { it.copy(autoDeletePlayed = enabled) }
        showToast(if (enabled) "Dinlenen bölümleri otomatik silme açık" else "Otomatik silme kapalı")
    }

    fun clearCache() {
        podcastRepo.clearAllDownloadedEpisodes()
        showToast("Önbellek ve indirilen ses dosyaları temizlendi (0.0 MB)")
    }

    fun getStorageUsage(): String {
        return podcastRepo.getDownloadedStorageSizeFormatted()
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun playEpisode(episode: Episode) {
        playerManager.play(episode)
    }

    fun togglePlayPause(episode: Episode? = null) {
        playerManager.togglePlayPause(episode)
    }

    fun seekTo(seconds: Int) {
        playerManager.seekTo(seconds)
    }

    fun seekBackward10() {
        playerManager.seekBy(-10)
    }

    fun seekForward30() {
        playerManager.seekBy(30)
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        if (speed == PlaybackSpeed.SPEED_3_0 && !subscriptionState.value.isPlusActive) {
            openBillingSheet()
            showToast("3.0x Hız Plus destekçilerine özeldir.")
            return
        }
        playerManager.setSpeed(speed)
    }

    fun setSleepTimer(duration: SleepTimerDuration) {
        playerManager.setSleepTimer(duration)
    }

    fun toggleSubscription(podcast: Podcast) {
        val isNowSubscribed = podcastRepo.toggleSubscription(podcast)
        val status = if (isNowSubscribed) "Abone olundu" else "Abonelikten çıkıldı"
        showToast("${podcast.title} için $status")

        // Update selected detail if matches
        if (_selectedPodcastDetail.value?.id == podcast.id) {
            _selectedPodcastDetail.value = podcast.copy(isSubscribed = isNowSubscribed)
        }
    }

    fun toggleQueue(episodeId: String) {
        podcastRepo.toggleQueue(episodeId)
        val ep = episodes.value.find { it.id == episodeId } ?: _detailEpisodes.value.find { it.id == episodeId }
        if (ep != null) {
            val status = if (!ep.isInQueue) "Sıraya eklendi" else "Sıradan çıkarıldı"
            showToast(status)
        }
    }

    fun toggleDownload(episodeId: String) {
        val ep = episodes.value.find { it.id == episodeId } ?: _detailEpisodes.value.find { it.id == episodeId }
        if (ep == null) return

        if (!subscriptionState.value.isPlusActive) {
            val currentDownloadsCount = downloadedEpisodes.value.size
            if (currentDownloadsCount >= 2 && !ep.isDownloaded) {
                openBillingSheet()
                showToast("Sınırsız çevrimdışı indirme için Plus desteği verin!")
                return
            }
        }
        podcastRepo.toggleDownload(ep) { statusMsg ->
            showToast(statusMsg)
        }
    }

    fun markAsPlayed(episodeId: String) {
        podcastRepo.markAsPlayed(episodeId)
    }

    private fun playNextInQueue() {
        val next = queueEpisodes.value.firstOrNull { it.id != currentEpisode.value?.id && !it.isCompleted }
        if (next != null) {
            playerManager.play(next)
        }
    }

    fun purchaseSupport() {
        viewModelScope.launch {
            val success = billingRepo.purchaseSupport()
            if (success) {
                _isBillingSheetOpen.value = false
                showToast("Geliştiriciye desteğiniz için çok teşekkürler! Plus aktif edildi ✨")
            }
        }
    }

    fun redeemPromoCode(code: String) {
        val (success, message) = billingRepo.redeemPromoCode(code)
        if (success) {
            _isBillingSheetOpen.value = false
        }
        showToast(message)
    }

    fun restorePurchases() {
        val (success, message) = billingRepo.restorePurchases()
        if (success && billingRepo.subscriptionState.value.isPlusActive) {
            _isBillingSheetOpen.value = false
        }
        showToast(message)
    }

    fun openBillingSheet() {
        _isBillingSheetOpen.value = true
    }

    fun closeBillingSheet() {
        _isBillingSheetOpen.value = false
    }

    fun openFullPlayer() {
        _isFullPlayerOpen.value = true
    }

    fun closeFullPlayer() {
        _isFullPlayerOpen.value = false
    }

    fun selectPodcastForDetail(podcast: Podcast) {
        _selectedPodcastDetail.value = podcast
        // Fetch real episodes for this podcast
        viewModelScope.launch {
            val eps = podcastRepo.getEpisodesForPodcast(podcast)
            _detailEpisodes.value = eps
        }
    }

    fun clearPodcastDetail() {
        _selectedPodcastDetail.value = null
        _detailEpisodes.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(400) // Debounce network search
            if (query.isNotBlank()) {
                podcastRepo.searchRealPodcasts(query)
            }
        }
    }

    fun addPodcastByRss(rssUrl: String) {
        podcastRepo.addPodcastByRss(rssUrl) { success, message ->
            showToast(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
