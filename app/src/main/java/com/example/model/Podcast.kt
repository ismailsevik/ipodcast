package com.example.model

data class Podcast(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val feedUrl: String = "",
    val subscribersCount: String = "",
    val isSubscribed: Boolean = false,
    val episodesCount: Int = 0
)

data class Episode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastCoverUrl: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationSeconds: Int,
    val publishedDate: String,
    val isDownloaded: Boolean = false,
    val isInQueue: Boolean = false,
    val progressSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val localFilePath: String? = null,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false
) {
    val durationFormatted: String
        get() {
            if (durationSeconds <= 0) return ""
            val minutes = durationSeconds / 60
            val remainingSeconds = durationSeconds % 60
            return if (minutes >= 60) {
                val hours = minutes / 60
                val mins = minutes % 60
                "${hours} sa ${mins} dk"
            } else {
                "${minutes} dk ${remainingSeconds} sn"
            }
        }

    val remainingFormatted: String
        get() {
            if (durationSeconds <= 0) return ""
            val remaining = (durationSeconds - progressSeconds).coerceAtLeast(0)
            val minutes = remaining / 60
            val seconds = remaining % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val progressFormatted: String
        get() {
            val minutes = progressSeconds / 60
            val seconds = progressSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
