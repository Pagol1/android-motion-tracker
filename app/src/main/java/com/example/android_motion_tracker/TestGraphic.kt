package com.example.android_motion_tracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.android_motion_tracker.GraphicOverlay.Graphic
import kotlin.math.max
import kotlin.math.min

/** Graphic instance for rendering Barcode position and content information in an overlay view. */
class TestGraphic constructor(overlay: GraphicOverlay?) :
    Graphic(overlay) {
    private val rectPaint: Paint = Paint()
    private val barcodePaint: Paint
    private val labelPaint: Paint

    init {
        rectPaint.color = MARKER_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH
        barcodePaint = Paint()
        barcodePaint.color = TEXT_COLOR
        barcodePaint.textSize = TEXT_SIZE
        labelPaint = Paint()
        labelPaint.color = MARKER_COLOR
        labelPaint.style = Paint.Style.FILL
    }

    /**
     * Draws the barcode block annotations for position, size, and raw value on the supplied canvas.
     */
    override fun draw(canvas: Canvas) {

        val rect = RectF(0.2F,0.2F,0.2F,0.2F)
        // If the image is flipped, the left will be translated to right, and the right to left.
        val x0 = translateX(rect.left)
        val x1 = translateX(rect.right)
        rect.left = min(x0, x1)
        rect.right = max(x0, x1)
        rect.top = translateY(rect.top)
        rect.bottom = translateY(rect.bottom)
        canvas.drawRect(rect, rectPaint)
        // Draws other object info.
        val lineHeight = TEXT_SIZE + 2 * STROKE_WIDTH
        canvas.drawRect(
            rect.left - STROKE_WIDTH,
            rect.top - lineHeight,
            rect.right + STROKE_WIDTH,
            rect.top,
            labelPaint
        )
    }

    companion object {
        private const val TEXT_COLOR = Color.BLACK
        private const val MARKER_COLOR = Color.WHITE
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }
}