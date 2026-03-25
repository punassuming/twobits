package dev.scrybe.core.transcription

object TranscriptionPricing {
    const val USD_PER_MINUTE = 0.006

    fun estimateUsd(durationMs: Long): Double {
        if (durationMs <= 0L) return 0.0
        val durationMinutes = durationMs / 60_000.0
        return durationMinutes * USD_PER_MINUTE
    }
}
