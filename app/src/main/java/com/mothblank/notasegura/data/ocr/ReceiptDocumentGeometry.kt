package com.mothblank.notasegura.data.ocr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Geometry-only checks, intentionally independent of OpenCV and Android. */
internal data class PaperCorner(val x: Double, val y: Double)

internal data class ReceiptPaperQuad(
    /** Top-left, top-right, bottom-right, bottom-left in image coordinates. */
    val corners: List<PaperCorner>,
    val width: Double,
    val height: Double,
    val coverage: Double,
    val rectangularity: Double
)

internal object ReceiptDocumentGeometry {
    /**
     * Accept only a large, unambiguous, convex page-like quadrilateral whose
     * four corners are fully visible. Reject tight crops and severe foreshortening.
     *
     * Detection must remain an optional enhancement: failing these constraints
     * simply leaves the original image unchanged.
     */
    fun assess(
        points: List<PaperCorner>,
        imageWidth: Int,
        imageHeight: Int
    ): ReceiptPaperQuad? {
        if (points.size != 4 || imageWidth < 200 || imageHeight < 200) return null
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null

        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val angular = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val first = angular.indices.minByOrNull { angular[it].x + angular[it].y }
            ?: return null
        val quad = (0..3).map { angular[(first + it) % 4] }

        val perimeterCrosses = (0..3).map { index ->
            val a = quad[index]
            val b = quad[(index + 1) % 4]
            val c = quad[(index + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        if (perimeterCrosses.any { it <= 0.0 }) return null

        val smallSide = min(imageWidth, imageHeight).toDouble()
        val inset = max(4.0, smallSide * 0.008)
        if (quad.any {
                it.x < inset || it.y < inset ||
                    it.x > imageWidth - inset || it.y > imageHeight - inset
            }) return null

        val area = abs(quad.indices.sumOf { i ->
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            a.x * b.y - b.x * a.y
        }) / 2.0
        val coverage = area / (imageWidth.toDouble() * imageHeight)
        if (coverage !in 0.20..0.88) return null

        val boundingWidth = quad.maxOf { it.x } - quad.minOf { it.x }
        val boundingHeight = quad.maxOf { it.y } - quad.minOf { it.y }
        if (boundingWidth <= 0.0 || boundingHeight <= 0.0) return null
        val rectangularity = area / (boundingWidth * boundingHeight)
        if (rectangularity < 0.69) return null

        val top = length(quad[0], quad[1])
        val right = length(quad[1], quad[2])
        val bottom = length(quad[2], quad[3])
        val left = length(quad[3], quad[0])
        if (min(min(top, right), min(bottom, left)) < smallSide * 0.22) return null
        if (max(top, bottom) / min(top, bottom) > 1.85) return null
        if (max(left, right) / min(left, right) > 1.85) return null

        val width = max(top, bottom)
        val height = max(left, right)
        if (width / height !in 0.23..1.60) return null

        // Avoid triangular or bow-tie-shaped page detections.
        val diagonalA = length(quad[0], quad[2])
        val diagonalB = length(quad[1], quad[3])
        if (max(diagonalA, diagonalB) / min(diagonalA, diagonalB) > 1.7) {
            return null
        }

        return ReceiptPaperQuad(quad, width, height, coverage, rectangularity)
    }

    private fun length(a: PaperCorner, b: PaperCorner): Double =
        hypot(b.x - a.x, b.y - a.y)
}
