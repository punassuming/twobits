package dev.scrybe.core.common

import kotlin.math.roundToInt

object WaveformCodec {
    fun encode(samples: List<Float>): String = samples.joinToString(",") { ((it.coerceIn(0f, 1f)) * 1000).roundToInt().toString() }

    fun decode(encoded: String?): List<Float> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(',')
            .mapNotNull { token ->
                token.toIntOrNull()?.let { value ->
                    (value / 1000f).coerceIn(0f, 1f)
                }
            }
    }
}
