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
    
    val pinColor = try {
        Color.parseColor(colorHex)
    } catch (e: Exception) {
        Color.BLUE
    }

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

    // Add a darker stroke for definition
    paint.apply {
        color = Color.BLACK
        alpha = 60
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
 * Background colours keyed by icon key — same icon key as in IconCatalog.
 * Each IconCatalog category has a distinct colour family:
 *   Location     → Indigo     (#3949AB)
 *   Nature       → Forest Green (#2E7D32) — water/snow/sun use semantic sub-colours
 *   Food & Drink → Warm Red   (#C62828)  — café=brown, bar/wine=wine-red, dessert=pink
 *   Activities   → Deep Orange (#E65100)  — water sports use blue as semantic exception
 *   Accommodation→ Deep Purple (#6A1B9A)
 *   Transport    → Blue-Grey  (#455A64)
 *   Services     → Teal       (#00695C)
 *   Entertainment→ Fuchsia    (#AD1457)
 *   Markers      → Amber/Gold (#F9A825)  — favorite=red, emergency=crimson stay semantic
 * Icons not listed fall back to [DEFAULT_BG].
 */
private val ICON_BG_COLORS: Map<String, Int> = mapOf(
    // ── Location — Indigo ────────────────────────────────────────────────────
    "place"          to 0xFF3949AB.toInt(),
    "location_on"    to 0xFF3949AB.toInt(),
    "my_location"    to 0xFF3949AB.toInt(),
    "explore"        to 0xFF3F51B5.toInt(),
    "travel_explore" to 0xFF3F51B5.toInt(),
    "navigation"     to 0xFF3949AB.toInt(),
    "near_me"        to 0xFF5C6BC0.toInt(),
    "gps_fixed"      to 0xFF3949AB.toInt(),
    "flag"           to 0xFF3949AB.toInt(),
    "tour"           to 0xFF5C6BC0.toInt(),
    "map"            to 0xFF3949AB.toInt(),
    "push_pin"       to 0xFF3949AB.toInt(),
    "satellite"      to 0xFF283593.toInt(),
    "location_city"  to 0xFF3949AB.toInt(),
    // ── Nature — Forest Green (water→blue, sun→amber, snow→blue: semantic) ──
    "park"           to 0xFF388E3C.toInt(),
    "terrain"        to 0xFF558B2F.toInt(),  // olive-green: mountain / hill
    "waves"          to 0xFF0277BD.toInt(),  // blue: water features
    "water_drop"     to 0xFF0288D1.toInt(),  // blue: springs / water sources
    "landscape"      to 0xFF6D4C41.toInt(),  // brown: canyon / gorge / terrain layers
    "nature"         to 0xFF2E7D32.toInt(),
    "grass"          to 0xFF388E3C.toInt(),
    "forest"         to 0xFF1B5E20.toInt(),
    "spa"            to 0xFF7B1FA2.toInt(),  // purple: beauty / flowers
    "filter_vintage" to 0xFF7B1FA2.toInt(),  // purple: wildflowers
    "eco"            to 0xFF33691E.toInt(),
    "ac_unit"        to 0xFF0277BD.toInt(),  // blue: glacier / ice / snow
    "wb_sunny"       to 0xFFF57F17.toInt(),  // amber: sun (semantic)
    "cloud"          to 0xFF607D8B.toInt(),
    // ── Food & Drink — Warm Red ──────────────────────────────────────────────
    "restaurant"     to 0xFFC62828.toInt(),
    "local_cafe"     to 0xFF6D4C41.toInt(),  // brown: coffee / café
    "local_bar"      to 0xFF880E4F.toInt(),  // wine-red: bar / pub
    "fastfood"       to 0xFFD32F2F.toInt(),
    "lunch_dining"   to 0xFFC62828.toInt(),
    "dinner_dining"  to 0xFFB71C1C.toInt(),
    "brunch_dining"  to 0xFFC62828.toInt(),
    "bakery_dining"  to 0xFFBF360C.toInt(),  // dark orange-red: baked goods / oven
    "ramen_dining"   to 0xFFD32F2F.toInt(),
    "local_pizza"    to 0xFFD32F2F.toInt(),
    "icecream"       to 0xFFE91E63.toInt(),  // pink: ice cream / sweets
    "cake"           to 0xFFE91E63.toInt(),  // pink: cake / dessert
    "wine_bar"       to 0xFF880E4F.toInt(),  // wine-red: wine
    "coffee"         to 0xFF6D4C41.toInt(),  // brown: coffee
    // ── Activities — Deep Orange (water sports→blue: semantic exception) ────
    "directions_walk"   to 0xFFE65100.toInt(),
    "directions_run"    to 0xFFE65100.toInt(),
    "directions_bike"   to 0xFFBF360C.toInt(),
    "hiking"            to 0xFFE65100.toInt(),
    "fitness_center"    to 0xFFF4511E.toInt(),
    "pool"              to 0xFF0277BD.toInt(),  // blue: swimming pool / water
    "sailing"           to 0xFF0277BD.toInt(),  // blue: on-water sport
    "kayaking"          to 0xFF01579B.toInt(),  // dark blue: paddling
    "snowboarding"      to 0xFF0277BD.toInt(),  // blue: snow / ice
    "downhill_skiing"   to 0xFF0277BD.toInt(),  // blue: snow / ice
    "surfing"           to 0xFF0277BD.toInt(),  // blue: ocean waves
    "sports_soccer"     to 0xFFBF360C.toInt(),
    "sports_basketball" to 0xFFE65100.toInt(),
    "golf_course"       to 0xFF558B2F.toInt(),  // olive-green: golf fairway
    "paragliding"       to 0xFFE65100.toInt(),
    // ── Accommodation — Deep Purple ──────────────────────────────────────────
    "hotel"          to 0xFF6A1B9A.toInt(),
    "home"           to 0xFF7B1FA2.toInt(),
    "apartment"      to 0xFF6A1B9A.toInt(),
    "house"          to 0xFF7B1FA2.toInt(),
    "night_shelter"  to 0xFF4A148C.toInt(),
    "beach_access"   to 0xFF6A1B9A.toInt(),
    "king_bed"       to 0xFF6A1B9A.toInt(),
    "single_bed"     to 0xFF7B1FA2.toInt(),
    "meeting_room"   to 0xFF4A148C.toInt(),
    // ── Transport — Blue-Grey ────────────────────────────────────────────────
    "directions_car"  to 0xFF455A64.toInt(),
    "directions_bus"  to 0xFF546E7A.toInt(),
    "train"           to 0xFF37474F.toInt(),
    "flight"          to 0xFF455A64.toInt(),
    "motorcycle"      to 0xFF546E7A.toInt(),
    "two_wheeler"     to 0xFF546E7A.toInt(),
    "electric_car"    to 0xFF37474F.toInt(),
    "directions_boat" to 0xFF455A64.toInt(),
    "anchor"          to 0xFF37474F.toInt(),
    "local_taxi"      to 0xFF455A64.toInt(),
    "tram"            to 0xFF546E7A.toInt(),
    // ── Services — Teal ──────────────────────────────────────────────────────
    "local_hospital"        to 0xFF00695C.toInt(),
    "local_pharmacy"        to 0xFF00796B.toInt(),
    "local_gas_station"     to 0xFF00695C.toInt(),
    "local_parking"         to 0xFF00796B.toInt(),
    "shopping_cart"         to 0xFF00897B.toInt(),
    "storefront"            to 0xFF00897B.toInt(),
    "local_atm"             to 0xFF00695C.toInt(),
    "account_balance"       to 0xFF004D40.toInt(),
    "school"                to 0xFF00796B.toInt(),
    "local_police"          to 0xFF004D40.toInt(),
    "local_fire_department" to 0xFF00695C.toInt(),
    "local_laundry"         to 0xFF00897B.toInt(),
    // ── Entertainment — Fuchsia / Pink ───────────────────────────────────────
    "museum"          to 0xFFAD1457.toInt(),
    "music_note"      to 0xFFE91E63.toInt(),
    "nightlife"       to 0xFF880E4F.toInt(),
    "theaters"        to 0xFFAD1457.toInt(),
    "casino"          to 0xFF880E4F.toInt(),
    "sports_bar"      to 0xFFAD1457.toInt(),
    "sports_esports"  to 0xFFE91E63.toInt(),
    "photo_camera"    to 0xFFAD1457.toInt(),
    "attractions"     to 0xFFE91E63.toInt(),
    // ── Markers — Amber / Gold ───────────────────────────────────────────────
    "star"            to 0xFFF9A825.toInt(),
    "favorite"        to 0xFFE53935.toInt(),  // red: heart / memorial — semantic
    "bookmark"        to 0xFFF9A825.toInt(),
    "label"           to 0xFFF57F17.toInt(),
    "warning"         to 0xFFF9A825.toInt(),
    "info"            to 0xFFFFB300.toInt(),
    "emergency"       to 0xFFB71C1C.toInt(),  // crimson: danger — semantic
    "whatshot"        to 0xFFF57F17.toInt(),
    "bolt"            to 0xFFF9A825.toInt(),
    "visibility"      to 0xFFFFB300.toInt(),
    "work"            to 0xFFF57F17.toInt(),
    "business_center" to 0xFFF57F17.toInt(),
)

private val DEFAULT_BG = 0xFF3949AB.toInt()

/**
 * Creates a square bitmap of [size]×[size] with a rounded-rect background coloured from
 * [ICON_BG_COLORS], matching the style of [createCircleIcon] but square.
 * Corner radius is ~20% of size for a "squircle" look.
 */
fun createSquareIcon(
    iconKey: String,
    size: Int = 80,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cornerRadius = size * 0.20f
    val bgColor = ICON_BG_COLORS[iconKey] ?: DEFAULT_BG
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), cornerRadius, cornerRadius, bgPaint)
    return bitmap
}

/**
 * Creates a square bitmap of [size]×[size] showing a filled circle with
 * background colour from [ICON_BG_COLORS] keyed by [iconKey].
 * The white icon is drawn on top by the Compose-side helper [createPoiCircle] in MapComponent.
 */
fun createCircleIcon(
    iconKey: String,
    size: Int = 80,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val bgColor = ICON_BG_COLORS[iconKey] ?: DEFAULT_BG
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cx, cx - 1f, bgPaint)
    return bitmap
}

