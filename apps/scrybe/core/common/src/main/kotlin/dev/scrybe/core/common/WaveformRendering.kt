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
    // Each bar takes the max over its (fractional) slice of the sample array. Mapping samples
    // INTO buckets — the previous approach — left interior buckets empty whenever there were
    // fewer samples than bars, which rendered as a periodic dotted pattern in list rows. Slicing
    // the other way guarantees every bar covers at least one sample (nearest-sample upsampling
    // when samples are sparse).
    val step = samples.size.toFloat() / targetCount
    val buckets =
        FloatArray(targetCount) { i ->
            val from = (i * step).toInt().coerceIn(0, samples.lastIndex)
            val to = (((i + 1) * step).toInt() - 1).coerceIn(from, samples.lastIndex)
            var peakInSlice = 0f
            for (j in from..to) {
                if (samples[j] > peakInSlice) peakInSlice = samples[j]
            }
            peakInSlice
        }
    val peak = buckets.max()
    if (peak <= 0.01f) return List(targetCount) { 0f }
    return buckets.map { sqrt((it / peak).coerceIn(0f, 1f)) }
}

/** Perceptual shaping for a single live amplitude value (already 0..1), no normalization. */
fun shapeLiveAmplitude(amplitude: Float): Float = sqrt(amplitude.coerceIn(0f, 1f))
