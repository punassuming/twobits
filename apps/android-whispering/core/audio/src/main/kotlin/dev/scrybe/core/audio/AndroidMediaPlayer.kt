package dev.scrybe.core.audio

import android.media.MediaPlayer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Singleton
class AndroidMediaPlayer @Inject constructor() : AudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private var mediaPlayer: MediaPlayer? = null

    override suspend fun play(filePath: String): Result<Unit> = runCatching {
        val file = File(filePath)
        require(file.exists()) { "Recorded audio file not found" }

        stop()

        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                _isPlaying.value = false
                release()
                mediaPlayer = null
            }
            prepare()
            start()
        }

        mediaPlayer = player
        _isPlaying.value = true
    }

    override fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
    }
}
