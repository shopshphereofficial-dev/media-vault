package com.mediavault.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

object MediaPlayerManager {
    private var exoPlayer: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        return exoPlayer ?: synchronized(this) {
            exoPlayer ?: ExoPlayer.Builder(context.applicationContext).build().also {
                exoPlayer = it
            }
        }
    }

    fun playMedia(context: Context, filePath: String) {
        val player = getPlayer(context)
        val mediaItem = MediaItem.fromUri(filePath)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
