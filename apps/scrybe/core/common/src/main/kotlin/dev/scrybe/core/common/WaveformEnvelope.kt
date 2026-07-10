package dev.scrybe.core.common

import androidx.compose.ui.graphics.Path

/**
 * Builds the closed, mirrored envelope shape shared by every waveform renderer (live recording,
 * list rows, playback timeline). [points] are 0..1 amplitudes laid out evenly from [left] to
 * [right]; each becomes a half-height around [centerY] and the outline is smoothed with
 * quadratic curves through segment midpoints, so the discrete points read as one continuous
 * shape. [minHalfHeight] keeps silence visible as a thin band instead of a gap.
 *
 * Callers fill the returned path with their own brush, and can re-fill it under `clipRect` to
 * color horizontal spans differently (playback progress, speaker segments).
 */
fun buildWaveformEnvelopePath(
    points: List<Float>,
    left: Float,
    right: Float,
    centerY: Float,
    maxHalfHeight: Float,
    minHalfHeight: Float = 0f,
): Path {
    val path = Path()
    if (points.isEmpty() || right <= left) return path
    val n = points.size
    val stepX = if (n == 1) 0f else (right - left) / (n - 1)

    fun xAt(i: Int) = left + stepX * i

    fun halfAt(i: Int) = minHalfHeight + points[i].coerceIn(0f, 1f) * (maxHalfHeight - minHalfHeight)

    if (n == 1) {
        val half = halfAt(0)
        path.moveTo(left, centerY - half)
        path.lineTo(right, centerY - half)
        path.lineTo(right, centerY + half)
        path.lineTo(left, centerY + half)
        path.close()
        return path
    }

    // Top edge, left to right: quadratics with the data point as control and segment midpoints
    // as endpoints — the standard smoothing that guarantees a continuous tangent at every join.
    path.moveTo(xAt(0), centerY - halfAt(0))
    for (i in 1 until n) {
        val prevX = xAt(i - 1)
        val prevY = centerY - halfAt(i - 1)
        val midX = (prevX + xAt(i)) / 2f
        val midY = (prevY + (centerY - halfAt(i))) / 2f
        path.quadraticTo(prevX, prevY, midX, midY)
    }
    path.lineTo(xAt(n - 1), centerY - halfAt(n - 1))

    // Bottom edge mirrored, right to left, closing the region.
    path.lineTo(xAt(n - 1), centerY + halfAt(n - 1))
    for (i in n - 2 downTo 0) {
        val prevX = xAt(i + 1)
        val prevY = centerY + halfAt(i + 1)
        val midX = (prevX + xAt(i)) / 2f
        val midY = (prevY + (centerY + halfAt(i))) / 2f
        path.quadraticTo(prevX, prevY, midX, midY)
    }
    path.lineTo(xAt(0), centerY + halfAt(0))
    path.close()
    return path
}
