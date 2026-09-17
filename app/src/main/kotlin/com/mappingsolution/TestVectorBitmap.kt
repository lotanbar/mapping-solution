package com.mappingsolution

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.mappingsolution.ui.map.PoiMarkerColors

fun createPinBitmap(
    colorHex: String,
    width: Int = 120,
    height: Int = 160
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val pinColor = desaturateColor(try {
        Color.parseColor(colorHex)
    } catch (e: Exception) {
        Color.BLUE
    })

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    
    // Draw classic teardrop pin shape
    val path = Path().apply {
        val radius = width / 2f
        val cx = radius
        val cy = radius
        
        moveTo(cx, height.toFloat()) // bottom point
        
        // Curves to the top circle
        cubicTo(cx, height.toFloat() * 0.7f, 0f, cy * 1.5f, 0f, cy)
        arcTo(android.graphics.RectF(0f, 0f, width.toFloat(), width.toFloat()), 180f, 180f)
        cubicTo(width.toFloat(), cy * 1.5f, cx, height.toFloat() * 0.7f, cx, height.toFloat())
        close()
    }
    canvas.drawPath(path, paint)

    // Thin white outline keeps the marker readable over both satellite and dark maps.
    paint.apply {
        color = Color.WHITE
        alpha = 230
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawPath(path, paint)

    // Draw white inner circle for the icon
    paint.apply {
        color = Color.WHITE
        alpha = 255
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, width / 2f, width / 2.6f, paint)
    
    return bitmap
}



/** Reduces the saturation of [color] by multiplying the HSV saturation by [factor]. */
private fun desaturateColor(color: Int, factor: Float = 0.75f): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] *= factor
    return Color.HSVToColor(hsv)
}

private fun pinCanvas(
    iconKey: String,
    size: Int,
    borderColor: Int = Color.WHITE,
): Triple<Bitmap, Canvas, Paint> {
    val height = (size * 1.30f).toInt()
    val bitmap = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PoiMarkerColors.backgroundArgb(iconKey)
        style = Paint.Style.FILL
    }

    // All marker variants share a sharp bottom point. The wider top remains source-specific.
    val tail = Path().apply {
        moveTo(size * 0.22f, size * 0.64f)
        lineTo(size / 2f, height.toFloat())
        lineTo(size * 0.78f, size * 0.64f)
        close()
    }
    canvas.drawPath(tail, paint)
    canvas.drawPath(tail, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        alpha = 230
        style = Paint.Style.STROKE
        strokeWidth = 3f
    })
    return Triple(bitmap, canvas, paint)
}

/** Creates a rounded-square-head POI pin. */
fun createSquareIcon(
    iconKey: String,
    size: Int = 80,
): Bitmap {
    val (bitmap, canvas, bgPaint) = pinCanvas(iconKey, size)
    val cornerRadius = size * 0.20f
    val head = RectF(2f, 2f, size - 2f, size - 2f)
    canvas.drawRoundRect(
        head,
        cornerRadius,
        cornerRadius,
        bgPaint,
    )
    canvas.drawRoundRect(head, cornerRadius, cornerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 230
        style = Paint.Style.STROKE
        strokeWidth = 3f
    })
    return bitmap
}

/** Creates a circular-head pin for personal POI groups. */
fun createCircleIcon(
    iconKey: String,
    size: Int = 80,
    borderColor: Int = Color.WHITE,
): Bitmap {
    val (bitmap, canvas, bgPaint) = pinCanvas(iconKey, size, borderColor)
    val cx = size / 2f
    canvas.drawCircle(cx, cx, cx - 2f, bgPaint)
    canvas.drawCircle(cx, cx, cx - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        alpha = 230
        style = Paint.Style.STROKE
        strokeWidth = 3f
    })
    return bitmap
}

