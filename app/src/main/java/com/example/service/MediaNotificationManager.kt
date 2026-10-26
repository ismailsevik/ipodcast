package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.Episode

class MediaNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ipodcast_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "iPodcast Oynatıcı"
            val descriptionText = "Bildirim panelinde podcast oynatma ve zaman kontrolleri"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(episode: Episode?, isPlaying: Boolean) {
        if (episode == null) {
            cancelNotification()
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Rewind 10
        val rewindIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_REWIND_10
        }
        val rewindPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause
        val playPauseIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Forward 30
        val forwardIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_FORWARD_30
        }
        val forwardPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop
        val stopIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseTitle = if (isPlaying) "Duraklat" else "Oynat"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(episode.title)
            .setContentText(episode.podcastTitle)
            .setSubText("iPodcast")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_launcher_foreground, "-10 sn", rewindPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, playPauseTitle, playPausePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "+30 sn", forwardPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Kapat", stopPendingIntent)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            // Can happen if notification permission is not yet granted
            e.printStackTrace()
        }
    }

    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
