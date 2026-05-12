package dev.scrybe.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaPlayer
    @Inject
    constructor() : AudioPlayer {
        private val playerScope = CoroutineScope(Dispatchers.Default)
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState

        private var mediaPlayer: MediaPlayer? = null
        private var progressJob: Job? = null
        private var currentFilePath: String? = null
        private val playerLock = Any()

        override suspend fun play(filePath: String): Result<Unit> =
            runCatching {
                synchronized(playerLock) {
                    val resumePosition =
                        if (currentFilePath == File(filePath).absolutePath) {
                            _playbackState.value.currentPositionMs
                        } else {
                            0L
                        }
                    prepare(filePath, resumePosition).getOrThrow()
                    val player = requireNotNull(mediaPlayer)
                    player.start()
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    startProgressUpdates(player, requireNotNull(currentFilePath))
                }
            }

        override suspend fun prepare(
            filePath: String,
            startPositionMs: Long,
        ): Result<Unit> =
            runCatching {
                synchronized(playerLock) {
                    val file = File(filePath)
                    require(file.exists()) { "Recorded audio file not found" }

                    val currentPlayer = mediaPlayer
                    if (currentPlayer != null && currentFilePath == file.absolutePath) {
                        primeExistingPlayer(currentPlayer, file.absolutePath, startPositionMs)
                        return@runCatching
                    }

                    stop()
                    val player = buildPlayer(file.absolutePath)
                    mediaPlayer = player
                    currentFilePath = file.absolutePath
                    primeExistingPlayer(player, file.absolutePath, startPositionMs)
                }
            }

        override fun pause() {
            synchronized(playerLock) {
                mediaPlayer?.takeIf { it.isPlaying }?.pause()
                progressJob?.cancel()
                _playbackState.value =
                    _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = mediaPlayer?.currentPosition?.toLong() ?: _playbackState.value.currentPositionMs,
                    )
            }
        }

        override fun seekTo(positionMs: Long) {
            synchronized(playerLock) {
                val player = mediaPlayer ?: return
                val target = positionMs.coerceIn(0L, player.duration.toLong())
                player.seekTo(target.toInt())
                _playbackState.value = _playbackState.value.copy(currentPositionMs = target)
            }
        }

        override fun stop() {
            synchronized(playerLock) {
                progressJob?.cancel()
                mediaPlayer?.runCatching {
                    if (isPlaying) {
                        stop()
                    }
                    release()
                }
                mediaPlayer = null
                currentFilePath = null
                _playbackState.value = PlaybackState()
            }
        }

        private fun buildPlayer(filePath: String): MediaPlayer =
            MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    progressJob?.cancel()
                    _playbackState.value =
                        _playbackState.value.copy(
                            isPlaying = false,
                            currentPositionMs = _playbackState.value.durationMs,
                        )
                    release()
                    mediaPlayer = null
                    currentFilePath = null
                }
                prepare()
            }

        private fun primeExistingPlayer(
            player: MediaPlayer,
            filePath: String,
            startPositionMs: Long,
        ) {
            if (player.isPlaying) {
                player.pause()
            }
            progressJob?.cancel()
            val target = startPositionMs.coerceIn(0L, player.duration.toLong())
            player.seekTo(target.toInt())
            _playbackState.value =
                PlaybackState(
                    filePath = filePath,
                    isPlaying = false,
                    currentPositionMs = target,
                    durationMs = player.duration.toLong(),
                )
        }

        private fun startProgressUpdates(
            player: MediaPlayer,
            filePath: String,
        ) {
            progressJob?.cancel()
            progressJob =
                playerScope.launch {
                    while (isActive && mediaPlayer === player) {
                        _playbackState.value =
                            PlaybackState(
                                filePath = filePath,
                                isPlaying = player.isPlaying,
                                currentPositionMs = player.currentPosition.toLong(),
                                durationMs = player.duration.toLong(),
                            )
                        delay(200)
                    }
                }
        }
    }
