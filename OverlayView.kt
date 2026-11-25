package com.example.objectcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Draws boxes + labels on top of the camera preview / photo.
 * Boxes are in image pixels; they are mapped to the view using the same
 * "fill + center-crop" rule that PreviewView and the ImageView use.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections: List<Det> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1

    /** true = whole image visible (photos); false = fill and crop (live camera). */
    var fitInside = false

    private val palette = intArrayOf(
        Color.parseColor("#FF5252"), Color.parseColor("#40C4FF"), Color.parseColor("#69F0AE"),
        Color.parseColor("#FFD740"), Color.parseColor("#E040FB"), Color.parseColor("#FFAB40"),
        Color.parseColor("#18FFFF"), Color.parseColor("#B2FF59")
    )
    private val colorByLabel = HashMap<String, Int>()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 34f
        isFakeBoldText = true
    }

    fun setDetections(list: List<Det>, imgW: Int, imgH: Int) {
        detections = list
        imageWidth = max(imgW, 1)
        imageHeight = max(imgH, 1)
        postInvalidate()
    }

    fun clear() {
        detections = emptyList()
        postInvalidate()
    }

    private fun colorFor(label: String): Int =
        colorByLabel.getOrPut(label) { palette[colorByLabel.size % palette.size] }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val sx = width.toFloat() / imageWidth
        val sy = height.toFloat() / imageHeight
        val scale = if (fitInside) minOf(sx, sy) else max(sx, sy)
        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        for (d in detections) {
            val r = RectF(
                d.box.left * scale + dx, d.box.top * scale + dy,
                d.box.right * scale + dx, d.box.bottom * scale + dy
            )
            val color = colorFor(d.label)
            boxPaint.color = color
            canvas.drawRect(r, boxPaint)

            val text = "${d.label} ${(d.score * 100).toInt()}%"
            val tw = textPaint.measureText(text)
            val th = textPaint.textSize
            fillPaint.color = color
            val top = (r.top - th - 8f).coerceAtLeast(0f)
            canvas.drawRect(r.left, top, r.left + tw + 16f, top + th + 8f, fillPaint)
            canvas.drawText(text, r.left + 8f, top + th - 4f, textPaint)
        }
    }
}
