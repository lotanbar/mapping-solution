package com.mappingsolution.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter

/**
 * A circular-head map pin with a pointed tail, matching the Android map markers: the head uses the
 * icon's category colour and [icon] is drawn white on top ("marker" gets a plain white dot).
 */
class PoiMarkerPainter(
    private val iconKey: String,
    private val icon: Painter?,
    private val borderColor: Color = Color.White,
) : Painter() {

    override val intrinsicSize: Size = Size(WIDTH, WIDTH * 1.30f)

    override fun DrawScope.onDraw() {
        val scale = size.width / WIDTH
        val w = WIDTH * scale
        val h = w * 1.30f
        val background = PoiMarkerColors.background(iconKey)
        val border = borderColor.copy(alpha = 230 / 255f)
        val strokeWidth = 3f * scale

        val tail = Path().apply {
            moveTo(w * 0.22f, w * 0.64f)
            lineTo(w / 2f, h)
            lineTo(w * 0.78f, w * 0.64f)
            close()
        }
        drawPath(tail, background)
        drawPath(tail, border, style = Stroke(strokeWidth))

        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - 2f * scale
        drawCircle(background, radius, center)
        drawCircle(border, radius, center, style = Stroke(strokeWidth))

        if (iconKey == "marker" || icon == null) {
            drawCircle(Color.White, w * 0.20f, center)
            return
        }
        val iconSize = w * 0.55f
        val offset = (w - iconSize) / 2f
        translate(offset, offset) {
            with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }

    private companion object {
        const val WIDTH = 80f
    }
}
