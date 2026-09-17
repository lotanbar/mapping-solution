package com.mappingsolution.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Marker head colours for POI pins, shared by the Android and desktop maps. */
object PoiMarkerColors {
    /** Desaturated background for [iconKey] as an ARGB int. */
    fun backgroundArgb(iconKey: String): Int = background(iconKey).toArgb()

    /** Desaturated background for [iconKey]; unknown keys use the default indigo. */
    fun background(iconKey: String): Color {
        val base = Color(ICON_BG_COLORS[iconKey] ?: DEFAULT_BG)
        return desaturate(base, factor = 0.75f)
    }

    /** Scales HSV saturation by [factor] (matches android.graphics.Color.colorToHSV/HSVToColor). */
    private fun desaturate(color: Color, factor: Float): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (max == 0f) 0f else delta / max
        return Color.hsv(hue, (saturation * factor).coerceIn(0f, 1f), max)
    }
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
private val ICON_BG_COLORS: Map<String, Long> = mapOf(
    // ── Food & Drink — Warm Red ──────────────────────────────────────────────
    "restaurant"    to 0xFFC62828,
    "cafe"          to 0xFF6D4C41,  // brown: coffee / café
    "bar"           to 0xFF880E4F,  // wine-red: bar / pub
    "bakery"        to 0xFFBF360C,  // dark orange-red: baked goods
    "fast-food"     to 0xFFD32F2F,
    "ice-cream"     to 0xFFE91E63,  // pink: ice cream
    "confectionery" to 0xFFE91E63,  // pink: sweets / dessert
    "grocery"       to 0xFFC62828,
    "convenience"   to 0xFFD32F2F,
    "beer"          to 0xFF880E4F,  // wine-red: beer / wine
    "bbq"           to 0xFFBF4000,  // charcoal-orange: BBQ
    // ── Nature — Forest Green (semantic sub-colours for water/heat/ice) ──────
    "mountain"       to 0xFF558B2F,  // olive-green: mountain / hill
    "volcano"        to 0xFF78909C,  // blue-grey volcanic slate
    "waterfall"      to 0xFF0277BD,  // blue: waterfalls
    "cave"           to 0xFF4E342E,  // dark espresso: underground / cave
    "natural"        to 0xFF2E7D32,  // forest green: general nature
    "park"           to 0xFF388E3C,
    "beach"          to 0xFF0277BD,  // blue: coast
    "wetland"        to 0xFF00695C,  // dark teal: wetland / marsh
    "garden"         to 0xFF8BC34A,  // spring green: garden / flower
    "water"          to 0xFF1E88E5,  // royal blue: all water features
    "dam"            to 0xFF455A64,  // blue-grey: dam / weir
    // ── Heritage — earth tones ───────────────────────────────────────────────
    "ruins"         to 0xFF8D6E63,  // warm brown: ancient ruins
    "quarry"        to 0xFF546E7A,  // slate blue-grey: quarry / excavation
    "archway"       to 0xFF795548,  // warm sienna: caravanserai / Middle Eastern
    "historic"      to 0xFFA1887F,  // warm sandstone: generic historic
    "castle"        to 0xFF7B1010,  // dark maroon: medieval stone
    "monument"      to 0xFF5C4A1A,  // khaki parchment: monuments
    "cemetery"      to 0xFF616161,  // neutral grey: somber / burial
    "gate"          to 0xFF8D6E63,  // warm brown: gate / entrance
    "lighthouse"    to 0xFF37474F,  // dark blue-grey: coastal
    "windmill"      to 0xFF76A828,  // lime-herbaceous: windmill
    "watermill"     to 0xFF4E342E,  // dark espresso: historic mill
    "bridge"        to 0xFF4E342E,  // dark espresso: ancient bridge
    // ── Religion — faith-specific hues ──────────────────────────────────────
    "place-of-worship"   to 0xFFF9A825,  // amber: generic worship
    "religious-christian" to 0xFF1A237E,  // deep navy: Christian
    "religious-muslim"   to 0xFF1B5B35,  // dark emerald: Islamic
    "religious-jewish"   to 0xFF1565C0,  // royal blue: Jewish
    "religious-buddhist" to 0xFFFF8F00,  // golden saffron: Buddhist
    "religious-shinto"   to 0xFFBF6000,  // dark saffron: Hindu / Shinto
    // ── Services — Teal ──────────────────────────────────────────────────────
    "hospital"         to 0xFF00695C,
    "pharmacy"         to 0xFF00796B,
    "school"           to 0xFF00796B,
    "college"          to 0xFF00695C,
    "bank"             to 0xFF004D40,
    "parking"          to 0xFF00796B,
    "fuel"             to 0xFF00695C,
    "charging-station" to 0xFF558B00,  // lime-olive: EV charging
    "police"           to 0xFF004D40,
    "fire-station"     to 0xFF00695C,
    "laundry"          to 0xFF00897B,
    "shop"             to 0xFF00897B,
    "information"      to 0xFFFFB300,  // amber: info points
    // ── Transport — Blue-Grey ────────────────────────────────────────────────
    "airport"    to 0xFF455A64,
    "rail"       to 0xFF37474F,
    "rail-light" to 0xFF546E7A,
    "bus"        to 0xFF546E7A,
    "ferry"      to 0xFF455A64,
    "car"        to 0xFF455A64,
    "bicycle"    to 0xFFBF360C,  // dark orange: cycling
    "taxi"       to 0xFF455A64,
    "harbor"     to 0xFF37474F,
    // ── Accommodation — Deep Purple ──────────────────────────────────────────
    "lodging"  to 0xFF6A1B9A,
    "campsite" to 0xFF4A148C,
    "shelter"  to 0xFF4A148C,
    "home"     to 0xFF7B1FA2,
    // ── Entertainment — Fuchsia / Pink ───────────────────────────────────────
    "museum"     to 0xFFAD1457,
    "library"    to 0xFF006064,  // dark cyan: knowledge
    "cinema"     to 0xFFAD1457,
    "theatre"    to 0xFF6A0080,  // deep violet: live theatre
    "music"      to 0xFFE91E63,
    "gaming"     to 0xFF880E4F,
    "zoo"        to 0xFF1A7A2E,  // vibrant forest: wildlife
    "art-gallery" to 0xFFAD1457,
    "attraction" to 0xFFE91E63,
    "stadium"    to 0xFFD84315,  // burnt deep-orange: stadium
    // ── Activities — Deep Orange (water sports→blue semantic exception) ────
    "viewpoint"       to 0xFFFFB300,  // amber: lookout
    "swimming"        to 0xFF0277BD,  // blue: water sport
    "skiing"          to 0xFF0277BD,  // blue: snow / ice
    "golf"            to 0xFF558B2F,  // olive-green: fairway
    "tennis"          to 0xFFCDDC39,  // lime-yellow: court
    "fitness-centre"  to 0xFFF4511E,
    "horse-riding"    to 0xFF8D5524,  // warm sienna
    "dog-park"        to 0xFF388E3C,
    "picnic-site"     to 0xFF388E3C,
    "farm"            to 0xFF827717,  // harvest olive: farm / vineyard
    "observation-tower" to 0xFF283593,  // deep indigo: tower
    // ── Markers — Amber / Gold ───────────────────────────────────────────────
    "marker"  to 0xFF3949AB,  // indigo: default marker
    "circle"  to 0xFF3949AB,
    "village" to 0xFF3E2723,  // very dark brown: rural settlement
    "town"    to 0xFF37474F,  // blue-grey: town
    "danger"  to 0xFFD32F2F,  // emergency red
    "caution" to 0xFFF9A825,  // amber: warning
)

private const val DEFAULT_BG = 0xFF3949AB
