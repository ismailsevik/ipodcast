package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PodcastDownloadManager(private val context: Context) {

    private val tag = "PodcastDownloadManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Map of episodeId -> downloadProgress (0f to 1f)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private fun getStorageDir(): File {
        val externalPodcastsDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
        val dir = externalPodcastsDir ?: File(context.filesDir, "podcasts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun downloadEpisode(
        episode: Episode,
        onProgress: (Float) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val audioUrl = episode.audioUrl
        if (audioUrl.isBlank()) return@withContext null

        val dir = getStorageDir()
        val extension = if (audioUrl.contains(".m4a", ignoreCase = true)) ".m4a" else ".mp3"
        val cleanId = episode.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val destinationFile = File(dir, "ep_${cleanId}$extension")

        // If file already exists and is complete (> 100KB)
        if (destinationFile.exists() && destinationFile.length() > 100_000) {
            return@withContext destinationFile.absolutePath
        }

        try {
            updateProgress(episode.id, 0.05f)
            onProgress(0.05f)

            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "iPodcast/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(tag, "Download failed with HTTP code: ${response.code}")
                    clearProgress(episode.id)
                    return@withContext null
                }

                val body = response.body ?: run {
                    clearProgress(episode.id)
                    return@withContext null
                }

                val contentLength = body.contentLength()
                var inputStream: InputStream? = null
                var outputStream: FileOutputStream? = null

                try {
                    inputStream = body.byteStream()
                    outputStream = FileOutputStream(destinationFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (contentLength > 0) {
                            val fraction = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            updateProgress(episode.id, fraction)
                            onProgress(fraction)
                        }
                    }

                    outputStream.flush()
                    clearProgress(episode.id)
                    onProgress(1f)
                    return@withContext destinationFile.absolutePath
                } finally {
                    try { inputStream?.close() } catch (ignored: Exception) {}
                    try { outputStream?.close() } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during download of ${episode.title}", e)
            clearProgress(episode.id)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            null
        }
    }

    fun deleteDownloadedFile(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return try {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            } else false
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete file: $localPath", e)
            false
        }
    }

    fun getTotalDownloadedBytes(): Long {
        return try {
            val dir = getStorageDir()
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun clearAllDownloads(): Boolean {
        return try {
            val dir = getStorageDir()
            dir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateProgress(episodeId: String, progress: Float) {
        val current = _downloadProgressMap.value.toMutableMap()
        current[episodeId] = progress
        _downloadProgressMap.value = current
    }

    private fun clearProgress(episodeId: String) {
        val current = _downloadProgressMap.value.toMutableMap()
        current.remove(episodeId)
        _downloadProgressMap.value = current
    }
}
