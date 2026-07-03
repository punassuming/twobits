package dev.scrybe.core.audio

import kotlinx.coroutines.flow.Flow

/**
 * Raw PCM16 mono audio capture for streaming transcription. Runs as a second, independent
 * capture source alongside [AudioRecorder]'s encoded-file recording — never instead of it, so a
 * streaming failure of any kind can never affect the file/telemetry path.
 */
interface RealtimeAudioSource {
    /** 16-bit little-endian mono PCM chunks at the sample rate [start] negotiated. */
    val pcmFrames: Flow<ByteArray>

    /**
     * Starts capture. Fails (without throwing) if the microphone is unavailable, permission is
     * missing, or the device rejects concurrent capture alongside the file recorder — callers
     * must treat any failure as "streaming unavailable for this session" and fall back silently,
     * never surfacing it as a recording error.
     */
    suspend fun start(sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ): Result<Unit>

    fun stop()

    companion object {
        /** OpenAI's Realtime API expects PCM16 at 24kHz mono. */
        const val DEFAULT_SAMPLE_RATE_HZ = 24_000
    }
}
