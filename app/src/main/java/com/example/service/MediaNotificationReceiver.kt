package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.ismailsevik.ipodcast.ACTION_PLAY_PAUSE"
        const val ACTION_REWIND_10 = "com.ismailsevik.ipodcast.ACTION_REWIND_10"
        const val ACTION_FORWARD_30 = "com.ismailsevik.ipodcast.ACTION_FORWARD_30"
        const val ACTION_STOP = "com.ismailsevik.ipodcast.ACTION_STOP"

        var playerActionHandler: ((String) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        playerActionHandler?.invoke(action)
    }
}
