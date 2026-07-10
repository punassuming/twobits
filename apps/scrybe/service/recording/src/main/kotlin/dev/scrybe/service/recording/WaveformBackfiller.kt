package dev.scrybe.service.recording

import dev.scrybe.core.audio.WaveformExtractor
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot pass that generates waveform samples for sessions that don't have any — recordings
 * made before waveforms were captured, and imports/recoveries that stored an empty string.
 * Run from a background scope at app start; list rows observe Room flows, so each session's
 * waveform appears as soon as its row is updated.
 */
@Singleton
class WaveformBackfiller
    @Inject
    constructor(
        private val recordingSessionDao: RecordingSessionDao,
        private val waveformExtractor: WaveformExtractor,
    ) {
        /**
         * Sequential on purpose: decoding is CPU-heavy and this is opportunistic housekeeping.
         * Sessions whose audio file is missing or undecodable stay empty (their rows render a
         * flat baseline) and are retried on the next launch, which fails fast for broken files.
         */
        suspend fun backfillMissingWaveforms() {
            recordingSessionDao.getSessionsMissingWaveform().forEach { session ->
                val file = File(session.audioFilePath)
                if (!file.exists()) return@forEach
                val samples = waveformExtractor.extract(file)
                if (samples.isNotEmpty()) {
                    recordingSessionDao.updateWaveformSamples(
                        id = session.id,
                        waveformSamples = WaveformCodec.encode(samples),
                    )
                }
            }
        }
    }
