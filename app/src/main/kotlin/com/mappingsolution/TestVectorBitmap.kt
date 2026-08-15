package com.mappingsolution

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

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


/**
 * Background colours keyed by Maki icon key — same key as in IconCatalog.
 * Category colour families:
 *   Food & Drink  → Warm Red     (#C62828)  — café=brown, bar=wine-red, sweets=pink
 *   Nature        → Forest Green (#2E7D32)  — water=blue, hot-spring=coral, wetland=teal
 *   Heritage      → Earth tones  (maroon/sandstone/espresso per sub-type)
 *   Religion      → Faith-specific hues
 *   Services      → Teal         (#00695C)
 *   Transport     → Blue-Grey    (#455A64)
 *   Accommodation → Deep Purple  (#6A1B9A)
 *   Entertainment → Fuchsia      (#AD1457)
 *   Activities    → Deep Orange  (#E65100)  — water sports = blue semantic exception
 *   Markers       → Amber/Gold   (#F9A825)
 * Icons not listed fall back to [DEFAULT_BG].
 */
private val ICON_BG_COLORS: Map<String, Int> = mapOf(
    // ── Food & Drink — Warm Red ──────────────────────────────────────────────
    "restaurant"    to 0xFFC62828.toInt(),
    "cafe"          to 0xFF6D4C41.toInt(),  // brown: coffee / café
    "bar"           to 0xFF880E4F.toInt(),  // wine-red: bar / pub
    "bakery"        to 0xFFBF360C.toInt(),  // dark orange-red: baked goods
    "fast-food"     to 0xFFD32F2F.toInt(),
    "ice-cream"     to 0xFFE91E63.toInt(),  // pink: ice cream
    "confectionery" to 0xFFE91E63.toInt(),  // pink: sweets / dessert
    "grocery"       to 0xFFC62828.toInt(),
    "convenience"   to 0xFFD32F2F.toInt(),
    "beer"          to 0xFF880E4F.toInt(),  // wine-red: beer / wine
    "bbq"           to 0xFFBF4000.toInt(),  // charcoal-orange: BBQ
    // ── Nature — Forest Green (semantic sub-colours for water/heat/ice) ──────
    "mountain"       to 0xFF558B2F.toInt(),  // olive-green: mountain / hill
    "volcano"        to 0xFF78909C.toInt(),  // blue-grey volcanic slate
    "waterfall"      to 0xFF0277BD.toInt(),  // blue: waterfalls
    "cave"           to 0xFF4E342E.toInt(),  // dark espresso: underground / cave
    "natural"        to 0xFF2E7D32.toInt(),  // forest green: general nature
    "park"           to 0xFF388E3C.toInt(),
    "beach"          to 0xFF0277BD.toInt(),  // blue: coast
    "wetland"        to 0xFF00695C.toInt(),  // dark teal: wetland / marsh
    "garden"         to 0xFF8BC34A.toInt(),  // spring green: garden / flower
    "water"          to 0xFF1E88E5.toInt(),  // royal blue: all water features
    "dam"            to 0xFF455A64.toInt(),  // blue-grey: dam / weir
    // ── Heritage — earth tones ───────────────────────────────────────────────
    "ruins"         to 0xFF8D6E63.toInt(),  // warm brown: ancient ruins
    "quarry"        to 0xFF546E7A.toInt(),  // slate blue-grey: quarry / excavation
    "archway"       to 0xFF795548.toInt(),  // warm sienna: caravanserai / Middle Eastern
    "historic"      to 0xFFA1887F.toInt(),  // warm sandstone: generic historic
    "castle"        to 0xFF7B1010.toInt(),  // dark maroon: medieval stone
    "monument"      to 0xFF5C4A1A.toInt(),  // khaki parchment: monuments
    "cemetery"      to 0xFF616161.toInt(),  // neutral grey: somber / burial
    "gate"          to 0xFF8D6E63.toInt(),  // warm brown: gate / entrance
    "lighthouse"    to 0xFF37474F.toInt(),  // dark blue-grey: coastal
    "windmill"      to 0xFF76A828.toInt(),  // lime-herbaceous: windmill
    "watermill"     to 0xFF4E342E.toInt(),  // dark espresso: historic mill
    "bridge"        to 0xFF4E342E.toInt(),  // dark espresso: ancient bridge
    // ── Religion — faith-specific hues ──────────────────────────────────────
    "place-of-worship"   to 0xFFF9A825.toInt(),  // amber: generic worship
    "religious-christian" to 0xFF1A237E.toInt(),  // deep navy: Christian
    "religious-muslim"   to 0xFF1B5B35.toInt(),  // dark emerald: Islamic
    "religious-jewish"   to 0xFF1565C0.toInt(),  // royal blue: Jewish
    "religious-buddhist" to 0xFFFF8F00.toInt(),  // golden saffron: Buddhist
    "religious-shinto"   to 0xFFBF6000.toInt(),  // dark saffron: Hindu / Shinto
    // ── Services — Teal ──────────────────────────────────────────────────────
    "hospital"         to 0xFF00695C.toInt(),
    "pharmacy"         to 0xFF00796B.toInt(),
    "school"           to 0xFF00796B.toInt(),
    "college"          to 0xFF00695C.toInt(),
    "bank"             to 0xFF004D40.toInt(),
    "parking"          to 0xFF00796B.toInt(),
    "fuel"             to 0xFF00695C.toInt(),
    "charging-station" to 0xFF558B00.toInt(),  // lime-olive: EV charging
    "police"           to 0xFF004D40.toInt(),
    "fire-station"     to 0xFF00695C.toInt(),
    "laundry"          to 0xFF00897B.toInt(),
    "shop"             to 0xFF00897B.toInt(),
    "information"      to 0xFFFFB300.toInt(),  // amber: info points
    // ── Transport — Blue-Grey ────────────────────────────────────────────────
    "airport"    to 0xFF455A64.toInt(),
    "rail"       to 0xFF37474F.toInt(),
    "rail-light" to 0xFF546E7A.toInt(),
    "bus"        to 0xFF546E7A.toInt(),
    "ferry"      to 0xFF455A64.toInt(),
    "car"        to 0xFF455A64.toInt(),
    "bicycle"    to 0xFFBF360C.toInt(),  // dark orange: cycling
    "taxi"       to 0xFF455A64.toInt(),
    "harbor"     to 0xFF37474F.toInt(),
    // ── Accommodation — Deep Purple ──────────────────────────────────────────
    "lodging"  to 0xFF6A1B9A.toInt(),
    "campsite" to 0xFF4A148C.toInt(),
    "shelter"  to 0xFF4A148C.toInt(),
    "home"     to 0xFF7B1FA2.toInt(),
    // ── Entertainment — Fuchsia / Pink ───────────────────────────────────────
    "museum"     to 0xFFAD1457.toInt(),
    "library"    to 0xFF006064.toInt(),  // dark cyan: knowledge
    "cinema"     to 0xFFAD1457.toInt(),
    "theatre"    to 0xFF6A0080.toInt(),  // deep violet: live theatre
    "music"      to 0xFFE91E63.toInt(),
    "gaming"     to 0xFF880E4F.toInt(),
    "zoo"        to 0xFF1A7A2E.toInt(),  // vibrant forest: wildlife
    "art-gallery" to 0xFFAD1457.toInt(),
    "attraction" to 0xFFE91E63.toInt(),
    "stadium"    to 0xFFD84315.toInt(),  // burnt deep-orange: stadium
    // ── Activities — Deep Orange (water sports→blue semantic exception) ────
    "viewpoint"       to 0xFFFFB300.toInt(),  // amber: lookout
    "swimming"        to 0xFF0277BD.toInt(),  // blue: water sport
    "skiing"          to 0xFF0277BD.toInt(),  // blue: snow / ice
    "golf"            to 0xFF558B2F.toInt(),  // olive-green: fairway
    "tennis"          to 0xFFCDDC39.toInt(),  // lime-yellow: court
    "fitness-centre"  to 0xFFF4511E.toInt(),
    "horse-riding"    to 0xFF8D5524.toInt(),  // warm sienna
    "dog-park"        to 0xFF388E3C.toInt(),
    "picnic-site"     to 0xFF388E3C.toInt(),
    "farm"            to 0xFF827717.toInt(),  // harvest olive: farm / vineyard
    "observation-tower" to 0xFF283593.toInt(),  // deep indigo: tower
    // ── Markers — Amber / Gold ───────────────────────────────────────────────
    "marker"  to 0xFF3949AB.toInt(),  // indigo: default marker
    "circle"  to 0xFF3949AB.toInt(),
    "village" to 0xFF3E2723.toInt(),  // very dark brown: rural settlement
    "town"    to 0xFF37474F.toInt(),  // blue-grey: town
    "danger"  to 0xFFD32F2F.toInt(),  // emergency red
    "caution" to 0xFFF9A825.toInt(),  // amber: warning
)

private val DEFAULT_BG = 0xFF3949AB.toInt()

/** Reduces the saturation of [color] by multiplying the HSV saturation by [factor]. */
private fun desaturateColor(color: Int, factor: Float = 0.75f): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] *= factor
    return Color.HSVToColor(hsv)
}

private fun pinCanvas(iconKey: String, size: Int): Triple<Bitmap, Canvas, Paint> {
    val height = (size * 1.30f).toInt()
    val bitmap = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = desaturateColor(ICON_BG_COLORS[iconKey] ?: DEFAULT_BG)
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
        color = Color.WHITE
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
): Bitmap {
    val (bitmap, canvas, bgPaint) = pinCanvas(iconKey, size)
    val cx = size / 2f
    canvas.drawCircle(cx, cx, cx - 2f, bgPaint)
    canvas.drawCircle(cx, cx, cx - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 230
        style = Paint.Style.STROKE
        strokeWidth = 3f
    })
    return bitmap
}

