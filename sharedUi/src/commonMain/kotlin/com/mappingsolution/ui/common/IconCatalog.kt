package com.mappingsolution.ui.common

import com.mappingsolution.ui.resources.Res
import com.mappingsolution.ui.resources.ic_maki_marker
import com.mappingsolution.ui.resources.ic_maki_airport
import com.mappingsolution.ui.resources.ic_maki_archway
import com.mappingsolution.ui.resources.ic_maki_art_gallery
import com.mappingsolution.ui.resources.ic_maki_attraction
import com.mappingsolution.ui.resources.ic_maki_bakery
import com.mappingsolution.ui.resources.ic_maki_bank
import com.mappingsolution.ui.resources.ic_maki_bar
import com.mappingsolution.ui.resources.ic_maki_bbq
import com.mappingsolution.ui.resources.ic_maki_beach
import com.mappingsolution.ui.resources.ic_maki_beer
import com.mappingsolution.ui.resources.ic_maki_bicycle
import com.mappingsolution.ui.resources.ic_maki_bridge
import com.mappingsolution.ui.resources.ic_maki_bus
import com.mappingsolution.ui.resources.ic_maki_cafe
import com.mappingsolution.ui.resources.ic_maki_campsite
import com.mappingsolution.ui.resources.ic_maki_car
import com.mappingsolution.ui.resources.ic_maki_castle
import com.mappingsolution.ui.resources.ic_maki_caution
import com.mappingsolution.ui.resources.ic_maki_cave
import com.mappingsolution.ui.resources.ic_maki_cemetery
import com.mappingsolution.ui.resources.ic_maki_charging_station
import com.mappingsolution.ui.resources.ic_maki_cinema
import com.mappingsolution.ui.resources.ic_maki_circle
import com.mappingsolution.ui.resources.ic_maki_college
import com.mappingsolution.ui.resources.ic_maki_confectionery
import com.mappingsolution.ui.resources.ic_maki_convenience
import com.mappingsolution.ui.resources.ic_maki_dam
import com.mappingsolution.ui.resources.ic_maki_danger
import com.mappingsolution.ui.resources.ic_maki_dog_park
import com.mappingsolution.ui.resources.ic_maki_farm
import com.mappingsolution.ui.resources.ic_maki_fast_food
import com.mappingsolution.ui.resources.ic_maki_ferry
import com.mappingsolution.ui.resources.ic_maki_fire_station
import com.mappingsolution.ui.resources.ic_maki_fitness_centre
import com.mappingsolution.ui.resources.ic_maki_fuel
import com.mappingsolution.ui.resources.ic_maki_gaming
import com.mappingsolution.ui.resources.ic_maki_garden
import com.mappingsolution.ui.resources.ic_maki_gate
import com.mappingsolution.ui.resources.ic_maki_golf
import com.mappingsolution.ui.resources.ic_maki_grocery
import com.mappingsolution.ui.resources.ic_maki_harbor
import com.mappingsolution.ui.resources.ic_maki_historic
import com.mappingsolution.ui.resources.ic_maki_home
import com.mappingsolution.ui.resources.ic_maki_horse_riding
import com.mappingsolution.ui.resources.ic_maki_hospital
import com.mappingsolution.ui.resources.ic_maki_ice_cream
import com.mappingsolution.ui.resources.ic_maki_information
import com.mappingsolution.ui.resources.ic_maki_laundry
import com.mappingsolution.ui.resources.ic_maki_library
import com.mappingsolution.ui.resources.ic_maki_lighthouse
import com.mappingsolution.ui.resources.ic_maki_lodging
import com.mappingsolution.ui.resources.ic_maki_monument
import com.mappingsolution.ui.resources.ic_maki_mountain
import com.mappingsolution.ui.resources.ic_maki_museum
import com.mappingsolution.ui.resources.ic_maki_music
import com.mappingsolution.ui.resources.ic_maki_natural
import com.mappingsolution.ui.resources.ic_maki_observation_tower
import com.mappingsolution.ui.resources.ic_maki_park
import com.mappingsolution.ui.resources.ic_maki_parking
import com.mappingsolution.ui.resources.ic_maki_pharmacy
import com.mappingsolution.ui.resources.ic_maki_picnic_site
import com.mappingsolution.ui.resources.ic_maki_place_of_worship
import com.mappingsolution.ui.resources.ic_maki_police
import com.mappingsolution.ui.resources.ic_maki_quarry
import com.mappingsolution.ui.resources.ic_maki_rail
import com.mappingsolution.ui.resources.ic_maki_rail_light
import com.mappingsolution.ui.resources.ic_maki_religious_buddhist
import com.mappingsolution.ui.resources.ic_maki_religious_christian
import com.mappingsolution.ui.resources.ic_maki_religious_jewish
import com.mappingsolution.ui.resources.ic_maki_religious_muslim
import com.mappingsolution.ui.resources.ic_maki_religious_shinto
import com.mappingsolution.ui.resources.ic_maki_restaurant
import com.mappingsolution.ui.resources.ic_maki_ruins
import com.mappingsolution.ui.resources.ic_maki_school
import com.mappingsolution.ui.resources.ic_maki_shelter
import com.mappingsolution.ui.resources.ic_maki_shop
import com.mappingsolution.ui.resources.ic_maki_skiing
import com.mappingsolution.ui.resources.ic_maki_stadium
import com.mappingsolution.ui.resources.ic_maki_swimming
import com.mappingsolution.ui.resources.ic_maki_taxi
import com.mappingsolution.ui.resources.ic_maki_tennis
import com.mappingsolution.ui.resources.ic_maki_theatre
import com.mappingsolution.ui.resources.ic_maki_town
import com.mappingsolution.ui.resources.ic_maki_viewpoint
import com.mappingsolution.ui.resources.ic_maki_village
import com.mappingsolution.ui.resources.ic_maki_volcano
import com.mappingsolution.ui.resources.ic_maki_water
import com.mappingsolution.ui.resources.ic_maki_waterfall
import com.mappingsolution.ui.resources.ic_maki_watermill
import com.mappingsolution.ui.resources.ic_maki_wetland
import com.mappingsolution.ui.resources.ic_maki_windmill
import com.mappingsolution.ui.resources.ic_maki_zoo
import org.jetbrains.compose.resources.DrawableResource

/**
 * Single source of truth for icons used throughout the app.
 *
 * Keys are Maki icon names (hyphen-separated, lower-case), matching the
 * drawable resource names with the "ic_maki_" prefix and hyphens replaced
 * by underscores (e.g. key "fast-food" → Res.drawable.ic_maki_fast_food).
 *
 * Use [iconRes] to look up a drawable by key; unknown keys fall back to
 * [Res.drawable.ic_maki_marker].
 */
object IconCatalog {

    data class IconEntry(val key: String, val res: DrawableResource, val label: String)
    data class IconCategory(val name: String, val icons: List<IconEntry>)

    val categories: List<IconCategory> = listOf(
        IconCategory(
            "Food & Drink", listOf(
                IconEntry("restaurant",   Res.drawable.ic_maki_restaurant,   "Restaurant"),
                IconEntry("cafe",         Res.drawable.ic_maki_cafe,         "Café"),
                IconEntry("bar",          Res.drawable.ic_maki_bar,          "Bar / Pub"),
                IconEntry("bakery",       Res.drawable.ic_maki_bakery,       "Bakery"),
                IconEntry("fast-food",    Res.drawable.ic_maki_fast_food,    "Fast Food"),
                IconEntry("ice-cream",    Res.drawable.ic_maki_ice_cream,    "Ice Cream"),
                IconEntry("confectionery",Res.drawable.ic_maki_confectionery,"Sweets"),
                IconEntry("grocery",      Res.drawable.ic_maki_grocery,      "Grocery"),
                IconEntry("convenience",  Res.drawable.ic_maki_convenience,  "Convenience"),
                IconEntry("beer",         Res.drawable.ic_maki_beer,         "Beer / Wine"),
                IconEntry("bbq",          Res.drawable.ic_maki_bbq,          "BBQ"),
            )
        ),
        IconCategory(
            "Nature", listOf(
                IconEntry("mountain",       Res.drawable.ic_maki_mountain,       "Mountain / Peak"),
                IconEntry("volcano",        Res.drawable.ic_maki_volcano,        "Volcano"),
                IconEntry("waterfall",      Res.drawable.ic_maki_waterfall,      "Waterfall"),
                IconEntry("cave",           Res.drawable.ic_maki_cave,           "Cave / Underground"),
                IconEntry("natural",        Res.drawable.ic_maki_natural,        "Nature"),
                IconEntry("park",           Res.drawable.ic_maki_park,           "Park / Forest"),
                IconEntry("beach",          Res.drawable.ic_maki_beach,          "Beach"),
                IconEntry("wetland",        Res.drawable.ic_maki_wetland,        "Wetland"),
                IconEntry("garden",         Res.drawable.ic_maki_garden,         "Garden"),
                IconEntry("water",          Res.drawable.ic_maki_water,          "Water"),
                IconEntry("dam",            Res.drawable.ic_maki_dam,            "Dam"),
            )
        ),
        IconCategory(
            "Heritage", listOf(
                IconEntry("ruins",      Res.drawable.ic_maki_ruins,      "Ruins / Archaeological"),
                IconEntry("quarry",     Res.drawable.ic_maki_quarry,     "Quarry"),
                IconEntry("archway",    Res.drawable.ic_maki_archway,    "Caravanserai"),
                IconEntry("historic",   Res.drawable.ic_maki_historic,   "Historic (generic)"),
                IconEntry("castle",     Res.drawable.ic_maki_castle,     "Castle / Fort"),
                IconEntry("monument",   Res.drawable.ic_maki_monument,   "Monument"),
                IconEntry("cemetery",   Res.drawable.ic_maki_cemetery,   "Cemetery / Tomb"),
                IconEntry("gate",       Res.drawable.ic_maki_gate,       "Gate"),
                IconEntry("lighthouse", Res.drawable.ic_maki_lighthouse, "Lighthouse"),
                IconEntry("windmill",   Res.drawable.ic_maki_windmill,   "Windmill"),
                IconEntry("watermill",  Res.drawable.ic_maki_watermill,  "Watermill"),
                IconEntry("bridge",     Res.drawable.ic_maki_bridge,     "Bridge"),
            )
        ),
        IconCategory(
            "Religion", listOf(
                IconEntry("place-of-worship",  Res.drawable.ic_maki_place_of_worship,  "Place of Worship"),
                IconEntry("religious-christian",Res.drawable.ic_maki_religious_christian,"Church"),
                IconEntry("religious-muslim",   Res.drawable.ic_maki_religious_muslim,  "Mosque"),
                IconEntry("religious-jewish",   Res.drawable.ic_maki_religious_jewish,  "Synagogue"),
                IconEntry("religious-buddhist", Res.drawable.ic_maki_religious_buddhist,"Buddhist Temple"),
                IconEntry("religious-shinto",   Res.drawable.ic_maki_religious_shinto,  "Temple / Shrine"),
            )
        ),
        IconCategory(
            "Services", listOf(
                IconEntry("hospital",         Res.drawable.ic_maki_hospital,         "Hospital"),
                IconEntry("pharmacy",         Res.drawable.ic_maki_pharmacy,         "Pharmacy"),
                IconEntry("school",           Res.drawable.ic_maki_school,           "School"),
                IconEntry("college",          Res.drawable.ic_maki_college,          "University"),
                IconEntry("bank",             Res.drawable.ic_maki_bank,             "Bank / ATM"),
                IconEntry("parking",          Res.drawable.ic_maki_parking,          "Parking"),
                IconEntry("fuel",             Res.drawable.ic_maki_fuel,             "Fuel"),
                IconEntry("charging-station", Res.drawable.ic_maki_charging_station, "EV Charging"),
                IconEntry("police",           Res.drawable.ic_maki_police,           "Police"),
                IconEntry("fire-station",     Res.drawable.ic_maki_fire_station,     "Fire Station"),
                IconEntry("laundry",          Res.drawable.ic_maki_laundry,          "Laundry"),
                IconEntry("shop",             Res.drawable.ic_maki_shop,             "Shop"),
                IconEntry("information",      Res.drawable.ic_maki_information,      "Information"),
            )
        ),
        IconCategory(
            "Transport", listOf(
                IconEntry("airport",   Res.drawable.ic_maki_airport,   "Airport"),
                IconEntry("rail",      Res.drawable.ic_maki_rail,      "Train"),
                IconEntry("rail-light",Res.drawable.ic_maki_rail_light,"Tram / Metro"),
                IconEntry("bus",       Res.drawable.ic_maki_bus,       "Bus"),
                IconEntry("ferry",     Res.drawable.ic_maki_ferry,     "Ferry"),
                IconEntry("car",       Res.drawable.ic_maki_car,       "Car"),
                IconEntry("bicycle",   Res.drawable.ic_maki_bicycle,   "Bicycle"),
                IconEntry("taxi",      Res.drawable.ic_maki_taxi,      "Taxi"),
                IconEntry("harbor",    Res.drawable.ic_maki_harbor,    "Harbor / Marina"),
            )
        ),
        IconCategory(
            "Accommodation", listOf(
                IconEntry("lodging",  Res.drawable.ic_maki_lodging,  "Hotel / Lodge"),
                IconEntry("campsite", Res.drawable.ic_maki_campsite, "Campsite"),
                IconEntry("shelter",  Res.drawable.ic_maki_shelter,  "Shelter / Hut"),
                IconEntry("home",     Res.drawable.ic_maki_home,     "Home"),
            )
        ),
        IconCategory(
            "Entertainment", listOf(
                IconEntry("museum",     Res.drawable.ic_maki_museum,     "Museum"),
                IconEntry("library",    Res.drawable.ic_maki_library,    "Library"),
                IconEntry("cinema",     Res.drawable.ic_maki_cinema,     "Cinema"),
                IconEntry("theatre",    Res.drawable.ic_maki_theatre,    "Theatre"),
                IconEntry("music",      Res.drawable.ic_maki_music,      "Music"),
                IconEntry("gaming",     Res.drawable.ic_maki_gaming,     "Gaming / Casino"),
                IconEntry("zoo",        Res.drawable.ic_maki_zoo,        "Zoo / Aquarium"),
                IconEntry("art-gallery",Res.drawable.ic_maki_art_gallery,"Gallery"),
                IconEntry("attraction", Res.drawable.ic_maki_attraction, "Attraction"),
                IconEntry("stadium",    Res.drawable.ic_maki_stadium,    "Stadium"),
            )
        ),
        IconCategory(
            "Activities", listOf(
                IconEntry("viewpoint",      Res.drawable.ic_maki_viewpoint,      "Viewpoint"),
                IconEntry("swimming",       Res.drawable.ic_maki_swimming,       "Swimming"),
                IconEntry("skiing",         Res.drawable.ic_maki_skiing,         "Skiing"),
                IconEntry("golf",           Res.drawable.ic_maki_golf,           "Golf"),
                IconEntry("tennis",         Res.drawable.ic_maki_tennis,         "Tennis"),
                IconEntry("fitness-centre", Res.drawable.ic_maki_fitness_centre, "Gym"),
                IconEntry("horse-riding",   Res.drawable.ic_maki_horse_riding,   "Horse Riding"),
                IconEntry("dog-park",       Res.drawable.ic_maki_dog_park,       "Dog Park"),
                IconEntry("picnic-site",    Res.drawable.ic_maki_picnic_site,    "Picnic"),
                IconEntry("farm",           Res.drawable.ic_maki_farm,           "Farm / Vineyard"),
                IconEntry("observation-tower", Res.drawable.ic_maki_observation_tower, "Tower"),
            )
        ),
        IconCategory(
            "Markers", listOf(
                IconEntry("marker",  Res.drawable.ic_maki_marker,  "Marker"),
                IconEntry("circle",  Res.drawable.ic_maki_circle,  "Circle"),
                IconEntry("village", Res.drawable.ic_maki_village, "Village"),
                IconEntry("town",    Res.drawable.ic_maki_town,    "Town"),
                IconEntry("danger",  Res.drawable.ic_maki_danger,  "Danger"),
                IconEntry("caution", Res.drawable.ic_maki_caution, "Caution"),
            )
        ),
    )

    private val allByKey: Map<String, DrawableResource> by lazy {
        categories.flatMap { it.icons }.associate { it.key to it.res }
    }

    fun iconRes(key: String?): DrawableResource = allByKey[key] ?: Res.drawable.ic_maki_marker
}
