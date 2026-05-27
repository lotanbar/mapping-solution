package com.mappingsolution.ui.common

import androidx.annotation.DrawableRes
import com.mappingsolution.R

/**
 * Single source of truth for icons used throughout the app.
 *
 * Keys are Maki icon names (hyphen-separated, lower-case), matching the
 * drawable resource names with the "ic_maki_" prefix and hyphens replaced
 * by underscores (e.g. key "fast-food" → R.drawable.ic_maki_fast_food).
 *
 * Use [iconRes] to look up a drawable by key; unknown keys fall back to
 * [R.drawable.ic_maki_marker].
 */
object IconCatalog {

    data class IconEntry(val key: String, @DrawableRes val res: Int, val label: String)
    data class IconCategory(val name: String, val icons: List<IconEntry>)

    val categories: List<IconCategory> = listOf(
        IconCategory(
            "Food & Drink", listOf(
                IconEntry("restaurant",   R.drawable.ic_maki_restaurant,   "Restaurant"),
                IconEntry("cafe",         R.drawable.ic_maki_cafe,         "Café"),
                IconEntry("bar",          R.drawable.ic_maki_bar,          "Bar / Pub"),
                IconEntry("bakery",       R.drawable.ic_maki_bakery,       "Bakery"),
                IconEntry("fast-food",    R.drawable.ic_maki_fast_food,    "Fast Food"),
                IconEntry("ice-cream",    R.drawable.ic_maki_ice_cream,    "Ice Cream"),
                IconEntry("confectionery",R.drawable.ic_maki_confectionery,"Sweets"),
                IconEntry("grocery",      R.drawable.ic_maki_grocery,      "Grocery"),
                IconEntry("convenience",  R.drawable.ic_maki_convenience,  "Convenience"),
                IconEntry("beer",         R.drawable.ic_maki_beer,         "Beer / Wine"),
                IconEntry("bbq",          R.drawable.ic_maki_bbq,          "BBQ"),
            )
        ),
        IconCategory(
            "Nature", listOf(
                IconEntry("mountain",       R.drawable.ic_maki_mountain,       "Mountain / Peak"),
                IconEntry("volcano",        R.drawable.ic_maki_volcano,        "Volcano"),
                IconEntry("waterfall",      R.drawable.ic_maki_waterfall,      "Waterfall"),
                IconEntry("cave",           R.drawable.ic_maki_cave,           "Cave / Underground"),
                IconEntry("natural",        R.drawable.ic_maki_natural,        "Nature"),
                IconEntry("park",           R.drawable.ic_maki_park,           "Park / Forest"),
                IconEntry("beach",          R.drawable.ic_maki_beach,          "Beach"),
                IconEntry("wetland",        R.drawable.ic_maki_wetland,        "Wetland"),
                IconEntry("garden",         R.drawable.ic_maki_garden,         "Garden"),
                IconEntry("water",          R.drawable.ic_maki_water,          "Water"),
                IconEntry("dam",            R.drawable.ic_maki_dam,            "Dam"),
            )
        ),
        IconCategory(
            "Heritage", listOf(
                IconEntry("ruins",      R.drawable.ic_maki_ruins,      "Ruins / Archaeological"),
                IconEntry("quarry",     R.drawable.ic_maki_quarry,     "Quarry"),
                IconEntry("archway",    R.drawable.ic_maki_archway,    "Caravanserai"),
                IconEntry("historic",   R.drawable.ic_maki_historic,   "Historic (generic)"),
                IconEntry("castle",     R.drawable.ic_maki_castle,     "Castle / Fort"),
                IconEntry("monument",   R.drawable.ic_maki_monument,   "Monument"),
                IconEntry("cemetery",   R.drawable.ic_maki_cemetery,   "Cemetery / Tomb"),
                IconEntry("gate",       R.drawable.ic_maki_gate,       "Gate"),
                IconEntry("lighthouse", R.drawable.ic_maki_lighthouse, "Lighthouse"),
                IconEntry("windmill",   R.drawable.ic_maki_windmill,   "Windmill"),
                IconEntry("watermill",  R.drawable.ic_maki_watermill,  "Watermill"),
                IconEntry("bridge",     R.drawable.ic_maki_bridge,     "Bridge"),
            )
        ),
        IconCategory(
            "Religion", listOf(
                IconEntry("place-of-worship",  R.drawable.ic_maki_place_of_worship,  "Place of Worship"),
                IconEntry("religious-christian",R.drawable.ic_maki_religious_christian,"Church"),
                IconEntry("religious-muslim",   R.drawable.ic_maki_religious_muslim,  "Mosque"),
                IconEntry("religious-jewish",   R.drawable.ic_maki_religious_jewish,  "Synagogue"),
                IconEntry("religious-buddhist", R.drawable.ic_maki_religious_buddhist,"Buddhist Temple"),
                IconEntry("religious-shinto",   R.drawable.ic_maki_religious_shinto,  "Temple / Shrine"),
            )
        ),
        IconCategory(
            "Services", listOf(
                IconEntry("hospital",         R.drawable.ic_maki_hospital,         "Hospital"),
                IconEntry("pharmacy",         R.drawable.ic_maki_pharmacy,         "Pharmacy"),
                IconEntry("school",           R.drawable.ic_maki_school,           "School"),
                IconEntry("college",          R.drawable.ic_maki_college,          "University"),
                IconEntry("bank",             R.drawable.ic_maki_bank,             "Bank / ATM"),
                IconEntry("parking",          R.drawable.ic_maki_parking,          "Parking"),
                IconEntry("fuel",             R.drawable.ic_maki_fuel,             "Fuel"),
                IconEntry("charging-station", R.drawable.ic_maki_charging_station, "EV Charging"),
                IconEntry("police",           R.drawable.ic_maki_police,           "Police"),
                IconEntry("fire-station",     R.drawable.ic_maki_fire_station,     "Fire Station"),
                IconEntry("laundry",          R.drawable.ic_maki_laundry,          "Laundry"),
                IconEntry("shop",             R.drawable.ic_maki_shop,             "Shop"),
                IconEntry("information",      R.drawable.ic_maki_information,      "Information"),
            )
        ),
        IconCategory(
            "Transport", listOf(
                IconEntry("airport",   R.drawable.ic_maki_airport,   "Airport"),
                IconEntry("rail",      R.drawable.ic_maki_rail,      "Train"),
                IconEntry("rail-light",R.drawable.ic_maki_rail_light,"Tram / Metro"),
                IconEntry("bus",       R.drawable.ic_maki_bus,       "Bus"),
                IconEntry("ferry",     R.drawable.ic_maki_ferry,     "Ferry"),
                IconEntry("car",       R.drawable.ic_maki_car,       "Car"),
                IconEntry("bicycle",   R.drawable.ic_maki_bicycle,   "Bicycle"),
                IconEntry("taxi",      R.drawable.ic_maki_taxi,      "Taxi"),
                IconEntry("harbor",    R.drawable.ic_maki_harbor,    "Harbor / Marina"),
            )
        ),
        IconCategory(
            "Accommodation", listOf(
                IconEntry("lodging",  R.drawable.ic_maki_lodging,  "Hotel / Lodge"),
                IconEntry("campsite", R.drawable.ic_maki_campsite, "Campsite"),
                IconEntry("shelter",  R.drawable.ic_maki_shelter,  "Shelter / Hut"),
                IconEntry("home",     R.drawable.ic_maki_home,     "Home"),
            )
        ),
        IconCategory(
            "Entertainment", listOf(
                IconEntry("museum",     R.drawable.ic_maki_museum,     "Museum"),
                IconEntry("library",    R.drawable.ic_maki_library,    "Library"),
                IconEntry("cinema",     R.drawable.ic_maki_cinema,     "Cinema"),
                IconEntry("theatre",    R.drawable.ic_maki_theatre,    "Theatre"),
                IconEntry("music",      R.drawable.ic_maki_music,      "Music"),
                IconEntry("gaming",     R.drawable.ic_maki_gaming,     "Gaming / Casino"),
                IconEntry("zoo",        R.drawable.ic_maki_zoo,        "Zoo / Aquarium"),
                IconEntry("art-gallery",R.drawable.ic_maki_art_gallery,"Gallery"),
                IconEntry("attraction", R.drawable.ic_maki_attraction, "Attraction"),
                IconEntry("stadium",    R.drawable.ic_maki_stadium,    "Stadium"),
            )
        ),
        IconCategory(
            "Activities", listOf(
                IconEntry("viewpoint",      R.drawable.ic_maki_viewpoint,      "Viewpoint"),
                IconEntry("swimming",       R.drawable.ic_maki_swimming,       "Swimming"),
                IconEntry("skiing",         R.drawable.ic_maki_skiing,         "Skiing"),
                IconEntry("golf",           R.drawable.ic_maki_golf,           "Golf"),
                IconEntry("tennis",         R.drawable.ic_maki_tennis,         "Tennis"),
                IconEntry("fitness-centre", R.drawable.ic_maki_fitness_centre, "Gym"),
                IconEntry("horse-riding",   R.drawable.ic_maki_horse_riding,   "Horse Riding"),
                IconEntry("dog-park",       R.drawable.ic_maki_dog_park,       "Dog Park"),
                IconEntry("picnic-site",    R.drawable.ic_maki_picnic_site,    "Picnic"),
                IconEntry("farm",           R.drawable.ic_maki_farm,           "Farm / Vineyard"),
                IconEntry("observation-tower", R.drawable.ic_maki_observation_tower, "Tower"),
            )
        ),
        IconCategory(
            "Markers", listOf(
                IconEntry("marker",  R.drawable.ic_maki_marker,  "Marker"),
                IconEntry("circle",  R.drawable.ic_maki_circle,  "Circle"),
                IconEntry("village", R.drawable.ic_maki_village, "Village"),
                IconEntry("town",    R.drawable.ic_maki_town,    "Town"),
                IconEntry("danger",  R.drawable.ic_maki_danger,  "Danger"),
                IconEntry("caution", R.drawable.ic_maki_caution, "Caution"),
            )
        ),
    )

    private val allByKey: Map<String, Int> by lazy {
        categories.flatMap { it.icons }.associate { it.key to it.res }
    }

    @DrawableRes
    fun iconRes(key: String?): Int = allByKey[key] ?: R.drawable.ic_maki_marker
}
