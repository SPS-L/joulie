// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom donut/pie chart drawn directly with [Canvas]. Used by the AC/DC and
 * Locations tabs of [org.spsl.evtracker.ui.charts.ChartsTabFragment]; Vico
 * (the line/column chart library) ships no pie primitive, and pulling in a
 * second chart library for one tab class wasn't worth the bloat. Slice-angle
 * math is covered by `PieChartViewSliceMathTest`.
 *
 * Behavioural surface (set by the caller's [renderAcDc] / [renderLocations]):
 * - donut style (configurable hole radius; default 0.55× of outer radius).
 * - per-slice label drawn at the slice centroid (value + label), skipped when
 *   the slice's sweep is too small to fit text without overlap.
 * - legend below the donut, one row, items wrap to additional rows when needed.
 * - optional centre text (used by AC/DC for the "N charges" total).
 * - 0°→360° sweep animation on the first data set, default 400 ms; TASK-79's
 *   `RoborazziSetup` idles the test looper for 800 ms after fragment commit
 *   so screenshot baselines capture the post-animation final state
 *   deterministically.
 *
 * Theme awareness: [labelColor] should be set by the caller from
 * `?attr/colorOnSurface`. Slice colours come in via [Slice.color].
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Float,
        @param:ColorInt val color: Int,
    )

    var slices: List<Slice> = emptyList()
        set(value) {
            field = value
            invalidateAndAnimate()
        }

    /** Optional centre text (e.g. "24 charges" for AC/DC). null hides centre text. */
    var centerText: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Donut hole as a fraction of the chart radius. 0.0 = solid pie, 0.6 = donut. */
    var holeRadiusFraction: Float = DEFAULT_HOLE_FRACTION
        set(value) {
            field = value.coerceIn(0f, 0.95f)
            invalidate()
        }

    /** Theme-aware text colour for centre text + slice + legend labels. */
    @ColorInt
    var labelColor: Int = DEFAULT_LABEL_COLOR
        set(value) {
            field = value
            slicePaint.color = value
            centrePaint.color = value
            legendPaint.color = value
            invalidate()
        }

    /** Sweep duration. 0 disables animation. */
    var animationDurationMs: Long = DEFAULT_ANIMATION_MS

    private val piePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        color = labelColor
    }
    private val centrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(14f)
        color = labelColor
        isFakeBoldText = true
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = sp(12f)
        color = labelColor
    }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val arcRect = RectF()
    private var animationFraction: Float = 1f
    private var animator: ValueAnimator? = null

    private fun invalidateAndAnimate() {
        animator?.cancel()
        if (animationDurationMs <= 0L || slices.isEmpty()) {
            animationFraction = 1f
            invalidate()
            return
        }
        animationFraction = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDurationMs
            addUpdateListener {
                animationFraction = it.animatedValue as Float
                invalidate()
            }
            doOnEnd { animationFraction = 1f }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = slices.fold(0f) { acc, s -> acc + s.value }
        if (slices.isEmpty() || total <= 0f) return

        // Reserve a single legend row of height = textSize * 1.6 + paddingV;
        // multi-row wrapping handled in drawLegend() and consumes more rows
        // when needed. Keep the donut area square.
        val legendHeight = legendRowsHeight(width.toFloat())
        val areaSize = min(width, (height - legendHeight.toInt()).coerceAtLeast(0))
        if (areaSize <= 0) return

        val left = (width - areaSize) / 2f
        val top = 0f
        arcRect.set(left, top, left + areaSize, top + areaSize)

        // Draw arcs.
        var startAngle = ANIMATION_START_ANGLE
        val sweepTotal = ANIMATION_FULL_SWEEP * animationFraction
        for (slice in slices) {
            val sweep = sweepTotal * (slice.value / total)
            piePaint.color = slice.color
            canvas.drawArc(arcRect, startAngle, sweep, true, piePaint)
            startAngle += sweep
        }

        // Punch the donut hole.
        if (holeRadiusFraction > 0f) {
            val cx = arcRect.centerX()
            val cy = arcRect.centerY()
            val outerRadius = arcRect.width() / 2f
            val holeRadius = outerRadius * holeRadiusFraction
            piePaint.color = currentBackgroundColor()
            canvas.drawCircle(cx, cy, holeRadius, piePaint)
        }

        // Slice labels at centroids (skip slivers).
        if (animationFraction >= LABEL_REVEAL_THRESHOLD) {
            drawSliceLabels(canvas, total)
        }

        // Centre text.
        centerText?.let { drawCentreText(canvas, it) }

        // Legend below.
        drawLegend(canvas, areaSize.toFloat() + legendPadding())
    }

    private fun drawSliceLabels(canvas: Canvas, total: Float) {
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()
        val outerRadius = arcRect.width() / 2f
        val holeRadius = outerRadius * holeRadiusFraction
        // Place label at the radial midpoint of the slice ring.
        val labelRadius = (outerRadius + holeRadius) / 2f
        var angle = ANIMATION_START_ANGLE
        for (slice in slices) {
            val sweep = ANIMATION_FULL_SWEEP * (slice.value / total)
            if (sweep < SLIVER_THRESHOLD_DEG) {
                angle += sweep
                continue
            }
            val midRad = Math.toRadians((angle + sweep / 2f).toDouble())
            val tx = cx + labelRadius * cos(midRad).toFloat()
            val ty = cy + labelRadius * sin(midRad).toFloat()
            // Two-line label: value (top), label (below). Centre vertically by
            // shifting the first baseline up by half the line height.
            val lineHeight = slicePaint.textSize * LABEL_LINE_FACTOR
            slicePaint.color = pickContrastingLabelColor(slice.color)
            canvas.drawText(formatValue(slice.value), tx, ty - lineHeight / 2f, slicePaint)
            canvas.drawText(slice.label, tx, ty + lineHeight / 2f + slicePaint.textSize / 4f, slicePaint)
            angle += sweep
        }
        // Restore default paint colour for centre / legend.
        slicePaint.color = labelColor
    }

    private fun drawCentreText(canvas: Canvas, text: String) {
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()
        // Vertical centring: baseline at cy + (textSize/3) approximates centre.
        canvas.drawText(text, cx, cy + centrePaint.textSize / 3f, centrePaint)
    }

    private fun drawLegend(canvas: Canvas, top: Float) {
        if (slices.isEmpty()) return
        val padH = dp(8f)
        val swatchSize = dp(10f)
        val swatchTextGap = dp(4f)
        val itemGap = dp(12f)
        val rowHeight = legendPaint.textSize * LEGEND_ROW_FACTOR
        var x = padH
        var y = top + rowHeight - dp(2f)
        for (slice in slices) {
            val labelText = slice.label
            val textWidth = legendPaint.measureText(labelText)
            val itemWidth = swatchSize + swatchTextGap + textWidth
            if (x + itemWidth > width - padH && x > padH) {
                // wrap
                x = padH
                y += rowHeight
            }
            swatchPaint.color = slice.color
            val swatchTop = y - swatchSize - dp(1f)
            canvas.drawRect(x, swatchTop, x + swatchSize, swatchTop + swatchSize, swatchPaint)
            canvas.drawText(labelText, x + swatchSize + swatchTextGap, y, legendPaint)
            x += itemWidth + itemGap
        }
    }

    private fun legendRowsHeight(viewWidth: Float): Float {
        if (slices.isEmpty() || viewWidth <= 0f) return 0f
        val padH = dp(8f)
        val swatchSize = dp(10f)
        val swatchTextGap = dp(4f)
        val itemGap = dp(12f)
        val rowHeight = legendPaint.textSize * LEGEND_ROW_FACTOR
        var x = padH
        var rows = 1
        for (slice in slices) {
            val itemWidth = swatchSize + swatchTextGap + legendPaint.measureText(slice.label)
            if (x + itemWidth > viewWidth - padH && x > padH) {
                rows++
                x = padH
            }
            x += itemWidth + itemGap
        }
        return rows * rowHeight + dp(4f)
    }

    private fun legendPadding(): Float = dp(4f)

    private fun pickContrastingLabelColor(@ColorInt sliceColor: Int): Int {
        // Simple luminance heuristic: dark slice → white label, light slice → black.
        val r = (sliceColor shr 16) and 0xff
        val g = (sliceColor shr 8) and 0xff
        val b = sliceColor and 0xff
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance < LUMINANCE_THRESHOLD) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    }

    private fun currentBackgroundColor(): Int {
        // Resolve the current activity's surface colour so the donut hole blends.
        val tv = TypedValue()
        val attr = com.google.android.material.R.attr.colorSurface
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else android.graphics.Color.TRANSPARENT
    }

    private fun formatValue(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        const val DEFAULT_HOLE_FRACTION: Float = 0.55f
        const val DEFAULT_ANIMATION_MS: Long = 400L

        @ColorInt
        const val DEFAULT_LABEL_COLOR: Int = android.graphics.Color.BLACK

        // Drawing constants.
        private const val ANIMATION_START_ANGLE: Float = -90f
        private const val ANIMATION_FULL_SWEEP: Float = 360f
        private const val SLIVER_THRESHOLD_DEG: Float = 18f
        private const val LABEL_REVEAL_THRESHOLD: Float = 0.95f
        private const val LABEL_LINE_FACTOR: Float = 1.15f
        private const val LEGEND_ROW_FACTOR: Float = 1.6f
        private const val LUMINANCE_THRESHOLD: Double = 0.55
    }
}
