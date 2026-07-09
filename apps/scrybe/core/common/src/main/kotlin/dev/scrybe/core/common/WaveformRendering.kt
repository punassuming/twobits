package dev.scrybe.core.common

import kotlin.math.sqrt

/**
 * Downsamples raw waveform amplitudes into exactly [targetCount] display bars, shared by every
 * waveform renderer (capture list rows, history rows, live recording) so they read as one visual
 * language:
 * - peak-per-bucket downsampling (keeps transients visible instead of averaging them away)
 * - peak normalization (quiet recordings still fill the row instead of rendering near-flat)
 * - square-root shaping (perceptual loudness — low amplitudes stay visible, peaks don't clip flat)
 */
fun shapeWaveformBars(
    samples: List<Float>,
    targetCount: Int,
): List<Float> {
    if (targetCount <= 0) return emptyList()
    if (samples.isEmpty()) return List(targetCount) { 0f }
    val buckets = FloatArray(targetCount)
    samples.forEachIndexed { index, sample ->
        val bucket =
            ((index.toFloat() / samples.size) * targetCount)
                .toInt()
                .coerceIn(0, targetCount - 1)
        if (sample > buckets[bucket]) buckets[bucket] = sample
    }
    val peak = buckets.max()
    if (peak <= 0.01f) return List(targetCount) { 0f }
    return buckets.map { sqrt((it / peak).coerceIn(0f, 1f)) }
}

/** Perceptual shaping for a single live amplitude value (already 0..1), no normalization. */
fun shapeLiveAmplitude(amplitude: Float): Float = sqrt(amplitude.coerceIn(0f, 1f))
