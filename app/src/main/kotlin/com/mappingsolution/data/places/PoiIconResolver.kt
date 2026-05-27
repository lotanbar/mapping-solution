package com.mappingsolution.data.places

/**
 * Resolves a Maki icon key (e.g. "restaurant", "mountain") from POI data.
 *
 * Icon keys are Maki icon names (hyphen-separated, e.g. "fast-food", "place-of-worship").
 * Every public function returns a non-null key; the fallback is "marker".
 *
 * Three public entry points:
 *  - [resolveForGoogleType]  — Google Places type list
 *  - [resolveForOsmTags]     — full OSM tags map
 *  - [resolveForImported]    — GPX waypoint (type string + name + description)
 */
object PoiIconResolver {

    // ── OSM tag-value → Maki icon key ─────────────────────────────────────────

    /** amenity= values */
    private val AMENITY = mapOf(
        "restaurant"        to "restaurant",
        "cafe"              to "cafe",
        "bar"               to "bar",
        "pub"               to "bar",
        "biergarten"        to "beer",
        "fast_food"         to "fast-food",
        "food_court"        to "restaurant",
        "ice_cream"         to "ice-cream",
        "confectionery"     to "confectionery",
        "hospital"          to "hospital",
        "clinic"            to "hospital",
        "doctors"           to "hospital",
        "dentist"           to "hospital",
        "veterinary"        to "hospital",
        "pharmacy"          to "pharmacy",
        "school"            to "school",
        "university"        to "college",
        "college"           to "college",
        "kindergarten"      to "school",
        "bank"              to "bank",
        "atm"               to "bank",
        "bureau_de_change"  to "bank",
        "fuel"              to "fuel",
        "charging_station"  to "charging-station",
        "parking"           to "parking",
        "parking_entrance"  to "parking",
        "police"            to "police",
        "fire_station"      to "fire-station",
        "theatre"           to "theatre",
        "cinema"            to "cinema",
        "arts_centre"       to "museum",
        "museum"            to "museum",
        "library"           to "library",
        "nightclub"         to "bar",
        "casino"            to "gaming",
        "bus_station"       to "bus",
        "ferry_terminal"    to "ferry",
        "taxi"              to "taxi",
        "bicycle_rental"    to "bicycle",
        "car_rental"        to "car",
        "car_wash"          to "car",
        "supermarket"       to "grocery",
        "marketplace"       to "shop",
        "laundry"           to "laundry",
        "dry_cleaning"      to "laundry",
        "post_office"       to "information",
        "townhall"          to "information",
        "courthouse"        to "information",
        "embassy"           to "information",
        "prison"            to "information",
        "observatory"       to "observation-tower",
        "drinking_water"    to "water",
        "place_of_worship"  to null,  // handled below with religion sub-key
    )

    /** leisure= values */
    private val LEISURE = mapOf(
        "nature_reserve"    to "natural",
        "park"              to "park",
        "garden"            to "garden",
        "playground"        to "park",
        "dog_park"          to "dog-park",
        "sports_centre"     to "stadium",
        "swimming_pool"     to "water",
        "swimming_area"     to "water",
        "golf_course"       to "golf",
        "marina"            to "harbor",
        "beach_resort"      to "beach",
        "picnic_table"      to "picnic-site",
        "picnic_area"       to "picnic-site",
        "fishing"           to "water",
        "pitch"             to "stadium",
        "track"             to "stadium",
        "stadium"           to "stadium",
        "fitness_centre"    to "fitness-centre",
        "horse_riding"      to "horse-riding",
        "dance"             to "attraction",
        "bowling_alley"     to "attraction",
        "miniature_golf"    to "golf",
        "slipway"           to "harbor",
        "water_park"        to "water",
    )

    /** natural= values */
    private val NATURAL = mapOf(
        "peak"              to "mountain",
        "volcano"           to "volcano",
        "cave_entrance"     to "cave",
        "waterfall"         to "waterfall",
        "glacier"           to "natural",
        "hot_spring"        to "water",
        "geyser"            to "water",
        "spring"            to "water",
        "well"              to "water",
        "beach"             to "beach",
        "bay"               to "beach",
        "wetland"           to "wetland",
        "marsh"             to "wetland",
        "wood"              to "park",
        "tree"              to "park",
        "heath"             to "natural",
        "scrub"             to "natural",
        "cliff"             to "mountain",
        "ridge"             to "mountain",
        "saddle"            to "mountain",
        "water"             to "water",
        "coastline"         to "beach",
        "rock"              to "mountain",
    )

    /** historic= values */
    private val HISTORIC = mapOf(
        "monument"              to "monument",
        "memorial"              to "monument",
        "castle"                to "castle",
        "fort"                  to "castle",
        "fortification"         to "castle",
        "city_gate"             to "gate",
        "archaeological_site"   to "ruins",
        "ruins"                 to "ruins",
        "building"              to "ruins",
        "quarry"                to "quarry",
        "archway"               to "archway",
        "manor"                 to "home",
        "estate"                to "home",
        "place_of_worship"      to "place-of-worship",
        "wayside_shrine"        to "place-of-worship",
        "wayside_cross"         to "religious-christian",
        "tomb"                  to "cemetery",
        "milestone"             to "information",
        "boundary_stone"        to "information",
        "ship"                  to "harbor",
        "aircraft"              to "airport",
        "tank"                  to "caution",
        "cannon"                to "caution",
        "battlefield"           to "caution",
        "watermill"             to "watermill",
        "windmill"              to "windmill",
        "bridge"                to "bridge",
        "aqueduct"              to "bridge",
    )

    /** tourism= values */
    private val TOURISM = mapOf(
        "viewpoint"         to "viewpoint",
        "hotel"             to "lodging",
        "motel"             to "lodging",
        "hostel"            to "lodging",
        "guest_house"       to "lodging",
        "apartment"         to "lodging",
        "chalet"            to "lodging",
        "camp_site"         to "campsite",
        "caravan_site"      to "campsite",
        "wilderness_hut"    to "shelter",
        "alpine_hut"        to "shelter",
        "lean_to"           to "shelter",
        "attraction"        to "attraction",
        "gallery"           to "art-gallery",
        "museum"            to "museum",
        "zoo"               to "zoo",
        "aquarium"          to "zoo",
        "theme_park"        to "attraction",
        "information"       to "information",
        "picnic_site"       to "picnic-site",
        "vineyard"          to "farm",
        "winery"            to "farm",
    )

    /** man_made= values */
    private val MAN_MADE = mapOf(
        "lighthouse"        to "lighthouse",
        "windmill"          to "windmill",
        "water_tower"       to "water",
        "reservoir_covered" to "water",
        "tower"             to "observation-tower",
        "communications_tower" to "observation-tower",
        "dam"               to "dam",
        "weir"              to "dam",
        "bridge"            to "bridge",
        "pier"              to "harbor",
        "survey_point"      to "mountain",
    )

    /** shop= values */
    private val SHOP = mapOf(
        "supermarket"       to "grocery",
        "convenience"       to "convenience",
        "grocery"           to "grocery",
        "bakery"            to "bakery",
        "butcher"           to "shop",
        "deli"              to "shop",
        "coffee"            to "cafe",
        "tea"               to "cafe",
        "alcohol"           to "beer",
        "wine"              to "beer",
        "car"               to "car",
        "car_repair"        to "car",
        "bicycle"           to "bicycle",
        "fuel"              to "fuel",
        "clothes"           to "shop",
        "shoes"             to "shop",
        "jewelry"           to "shop",
        "books"             to "library",
        "sports"            to "stadium",
        "outdoor"           to "park",
    )

    /** religion= sub-key for amenity=place_of_worship */
    private val RELIGION = mapOf(
        "muslim"            to "religious-muslim",
        "christian"         to "religious-christian",
        "jewish"            to "religious-jewish",
        "buddhist"          to "religious-buddhist",
        "shinto"            to "religious-shinto",
        "hindu"             to "religious-shinto",   // closest available
        "sikh"              to "place-of-worship",
        "bahai"             to "place-of-worship",
    )

    // ── Google Places type → OSM tags ────────────────────────────────────────

    private val GOOGLE_TO_OSM: Map<String, Map<String, String>> = mapOf(
        "restaurant"                        to mapOf("amenity" to "restaurant"),
        "cafe"                              to mapOf("amenity" to "cafe"),
        "coffee_shop"                       to mapOf("amenity" to "cafe"),
        "tea_house"                         to mapOf("amenity" to "cafe"),
        "bar"                               to mapOf("amenity" to "bar"),
        "pub"                               to mapOf("amenity" to "pub"),
        "brewery"                           to mapOf("amenity" to "bar"),
        "wine_bar"                          to mapOf("amenity" to "bar"),
        "bakery"                            to mapOf("amenity" to "bakery"),
        "fast_food_restaurant"              to mapOf("amenity" to "fast_food"),
        "sandwich_shop"                     to mapOf("amenity" to "fast_food"),
        "pizza_restaurant"                  to mapOf("amenity" to "fast_food"),
        "ice_cream_shop"                    to mapOf("amenity" to "ice_cream"),
        "dessert_shop"                      to mapOf("amenity" to "confectionery"),
        "confectionery"                     to mapOf("amenity" to "confectionery"),
        "bank"                              to mapOf("amenity" to "bank"),
        "atm"                               to mapOf("amenity" to "atm"),
        "pharmacy"                          to mapOf("amenity" to "pharmacy"),
        "hospital"                          to mapOf("amenity" to "hospital"),
        "doctor"                            to mapOf("amenity" to "doctors"),
        "dentist"                           to mapOf("amenity" to "dentist"),
        "veterinary_care"                   to mapOf("amenity" to "veterinary"),
        "supermarket"                       to mapOf("amenity" to "supermarket"),
        "grocery_store"                     to mapOf("shop" to "grocery"),
        "convenience_store"                 to mapOf("shop" to "convenience"),
        "shopping_mall"                     to mapOf("shop" to "clothes"),
        "clothing_store"                    to mapOf("shop" to "clothes"),
        "book_store"                        to mapOf("shop" to "books"),
        "electronics_store"                 to mapOf("shop" to "electronics"),
        "hardware_store"                    to mapOf("shop" to "hardware"),
        "furniture_store"                   to mapOf("shop" to "furniture"),
        "florist"                           to mapOf("shop" to "florist"),
        "jewelry_store"                     to mapOf("shop" to "jewelry"),
        "hotel"                             to mapOf("tourism" to "hotel"),
        "lodging"                           to mapOf("tourism" to "hotel"),
        "hostel"                            to mapOf("tourism" to "hostel"),
        "campground"                        to mapOf("tourism" to "camp_site"),
        "gas_station"                       to mapOf("amenity" to "fuel"),
        "electric_vehicle_charging_station" to mapOf("amenity" to "charging_station"),
        "parking"                           to mapOf("amenity" to "parking"),
        "gym"                               to mapOf("leisure" to "fitness_centre"),
        "beauty_salon"                      to mapOf("shop" to "beauty"),
        "hair_salon"                        to mapOf("shop" to "hairdresser"),
        "spa"                               to mapOf("leisure" to "spa"),
        "movie_theater"                     to mapOf("amenity" to "cinema"),
        "night_club"                        to mapOf("amenity" to "nightclub"),
        "casino"                            to mapOf("amenity" to "casino"),
        "museum"                            to mapOf("tourism" to "museum"),
        "art_gallery"                       to mapOf("tourism" to "gallery"),
        "library"                           to mapOf("amenity" to "library"),
        "park"                              to mapOf("leisure" to "park"),
        "national_park"                     to mapOf("leisure" to "nature_reserve"),
        "zoo"                               to mapOf("tourism" to "zoo"),
        "aquarium"                          to mapOf("tourism" to "aquarium"),
        "amusement_park"                    to mapOf("tourism" to "theme_park"),
        "tourist_attraction"                to mapOf("tourism" to "attraction"),
        "stadium"                           to mapOf("leisure" to "stadium"),
        "sports_complex"                    to mapOf("leisure" to "stadium"),
        "sports_club"                       to mapOf("leisure" to "sports_centre"),
        "golf_course"                       to mapOf("leisure" to "golf_course"),
        "bowling_alley"                     to mapOf("leisure" to "bowling_alley"),
        "school"                            to mapOf("amenity" to "school"),
        "university"                        to mapOf("amenity" to "university"),
        "airport"                           to mapOf("aeroway" to "aerodrome"),
        "train_station"                     to mapOf("railway" to "station"),
        "subway_station"                    to mapOf("railway" to "station"),
        "bus_station"                       to mapOf("amenity" to "bus_station"),
        "ferry_terminal"                    to mapOf("amenity" to "ferry_terminal"),
        "marina"                            to mapOf("leisure" to "marina"),
        "police"                            to mapOf("amenity" to "police"),
        "fire_station"                      to mapOf("amenity" to "fire_station"),
        "church"                            to mapOf("amenity" to "place_of_worship", "religion" to "christian"),
        "mosque"                            to mapOf("amenity" to "place_of_worship", "religion" to "muslim"),
        "synagogue"                         to mapOf("amenity" to "place_of_worship", "religion" to "jewish"),
        "hindu_temple"                      to mapOf("amenity" to "place_of_worship", "religion" to "hindu"),
        "place_of_worship"                  to mapOf("amenity" to "place_of_worship"),
        "cemetery"                          to mapOf("historic" to "tomb"),
        "monument"                          to mapOf("historic" to "monument"),
        "historical_landmark"               to mapOf("historic" to "memorial"),
        "car_dealer"                        to mapOf("shop" to "car"),
        "car_rental"                        to mapOf("amenity" to "car_rental"),
        "car_repair"                        to mapOf("shop" to "car_repair"),
        "laundry"                           to mapOf("amenity" to "laundry"),
        "post_office"                       to mapOf("amenity" to "post_office"),
        "real_estate_agency"                to mapOf("shop" to "estate_agent"),
        "accounting"                        to mapOf("shop" to "financial"),
        "lawyer"                            to mapOf("shop" to "financial"),
        "insurance_agency"                  to mapOf("shop" to "financial"),
    )

    // ── Keyword tables → OSM tag pairs ────────────────────────────────────────
    //
    // Each entry: (keywords: List<String>) to (tags: Map<String, String>)
    // First substring match wins for typeStr/name; for desc, most-hit category wins.

    private data class KwEntry(val keywords: List<String>, val tags: Map<String, String>)

    /**
     * Builds a Hebrew keyword list with automatic morphological expansion:
     *  - Words ending in ה  → construct form ending in ת  (גבעה→גבעת, אנדרטה→אנדרטת)
     *  - Words ending in ים → construct plural ending in י (שרידים→שרידי, קברים→קברי)
     *  - Two-word phrase "X Y" where Y lacks ה prefix → also "X הY" (definite article insertion:
     *    בית כנסת→בית הכנסת, בית קברות→בית הקברות, שמורת טבע→שמורת הטבע)
     */
    private fun heKw(vararg kws: String): List<String> {
        val out = mutableListOf<String>()
        for (kw in kws) {
            out += kw
            when {
                kw.endsWith('ה') -> out += kw.dropLast(1) + 'ת'
                kw.endsWith("ים") -> out += kw.dropLast(2) + 'י'
            }
            val parts = kw.split(' ')
            if (parts.size == 2 && parts[1].isNotEmpty() && !parts[1].startsWith('ה')) {
                out += "${parts[0]} ה${parts[1]}"
                // Also expand construct form of the two-word phrase
                if (kw.endsWith('ה')) out += "${parts[0]} ה${parts[1].dropLast(1)}ת"
            }
        }
        return out.distinct()
    }

    // Shared text utilities used by all three public resolvers

    private fun normalize(s: String) = s
        .replace("\u200F", "").replace("\u200E", "").replace("\u200B", "")

    /** First keyword match wins; checks Hebrew then English lists. */
    private fun matchKeywords(text: String): Map<String, String>? {
        for (entry in KEYWORD_HE) {
            if (entry.keywords.any { text.contains(it) }) return entry.tags
        }
        val lower = text.lowercase()
        for (entry in KEYWORD_EN) {
            if (entry.keywords.any { lower.contains(it) }) return entry.tags
        }
        return null
    }

    private val KEYWORD_EN: List<KwEntry> = listOf(
        // Danger — highest priority
        KwEntry(listOf("accident", "crash site", "danger", "hazard"), mapOf("hazard" to "yes")),
        // Food (specific before generic)
        KwEntry(listOf("sushi", "ramen", "noodle", "pho", "udon", "dumpling"), mapOf("amenity" to "restaurant", "cuisine" to "japanese")),
        KwEntry(listOf("pizza"), mapOf("amenity" to "fast_food", "cuisine" to "pizza")),
        KwEntry(listOf("ice cream", "icecream", "gelato", "frozen yogurt"), mapOf("amenity" to "ice_cream")),
        KwEntry(listOf("pastry", "patisserie", "confection", "cake", "dessert"), mapOf("amenity" to "confectionery")),
        KwEntry(listOf("bakery", "boulangerie", "bread shop"), mapOf("shop" to "bakery")),
        KwEntry(listOf("winery", "vineyard", "cellar"), mapOf("tourism" to "winery")),
        KwEntry(listOf("coffee", "espresso", "cappuccino", "barista"), mapOf("amenity" to "cafe", "cuisine" to "coffee")),
        KwEntry(listOf("cafe", "bistro cafe", "tea house", "tearoom"), mapOf("amenity" to "cafe")),
        KwEntry(listOf("bar", "pub", "tavern", "brewery", "saloon", "taproom"), mapOf("amenity" to "bar")),
        KwEntry(listOf("fast food", "burger", "sandwich", "kebab", "shawarma", "falafel", "taco", "hot dog"), mapOf("amenity" to "fast_food")),
        KwEntry(listOf("steakhouse", "bbq", "barbecue", "grill house", "seafood restaurant"), mapOf("amenity" to "restaurant")),
        KwEntry(listOf("restaurant", "diner", "eatery", "bistro", "buffet", "brasserie", "trattoria"), mapOf("amenity" to "restaurant")),
        // Beach / coast
        KwEntry(listOf("beach", "shore", "seaside", "coastline", "bay"), mapOf("natural" to "beach")),
        // Terrain
        KwEntry(listOf("volcano", "volcanic", "lava", "caldera"), mapOf("natural" to "volcano")),
        KwEntry(listOf("summit", "mountain top", "mountaintop"), mapOf("natural" to "peak")),
        KwEntry(listOf("mountain", "peak", "hill", "ridge", "cliff", "highland", "bluff", "butte"), mapOf("natural" to "peak")),
        KwEntry(listOf("canyon", "gorge", "ravine", "gully", "chasm"), mapOf("natural" to "cliff")),
        KwEntry(listOf("valley", "vale", "dale", "glen"), mapOf("natural" to "cliff")),
        // Cave
        KwEntry(listOf("cave", "cavern", "grotto", "cave entrance"), mapOf("natural" to "cave_entrance")),
        // Water
        KwEntry(listOf("waterfall", "cascade", "cataract"), mapOf("natural" to "waterfall")),
        KwEntry(listOf("river", "stream", "creek", "brook", "wadi", "canal"), mapOf("waterway" to "river")),
        KwEntry(listOf("lake", "pond", "lagoon", "reservoir", "tarn"), mapOf("natural" to "water")),
        KwEntry(listOf("glacier", "icefield"), mapOf("natural" to "glacier")),
        KwEntry(listOf("hot spring", "thermal spring", "thermal bath", "geyser", "geothermal"), mapOf("natural" to "hot_spring")),
        KwEntry(listOf("spring", "well", "cistern", "water source", "drinking water", "fountain"), mapOf("natural" to "spring")),        KwEntry(listOf("wetland", "marsh", "swamp", "bog", "fen", "mangrove"), mapOf("natural" to "wetland")),
        // Viewpoint
        KwEntry(listOf("viewpoint", "overlook", "lookout", "vista", "panorama", "belvedere", "observation deck"), mapOf("tourism" to "viewpoint")),
        // Lighthouse
        KwEntry(listOf("lighthouse", "light house"), mapOf("man_made" to "lighthouse")),
        // Windmill
        KwEntry(listOf("windmill", "wind turbine", "wind farm"), mapOf("man_made" to "windmill")),
        // Heritage
        KwEntry(listOf("castle", "fort", "fortress", "citadel", "stronghold", "rampart", "battlement"), mapOf("historic" to "castle")),
        KwEntry(listOf("manor", "mansion", "chateau", "stately home", "palace"), mapOf("historic" to "manor")),
        KwEntry(listOf("ruin", "ruins", "ancient", "archaeological", "excavation", "dig site", "heritage site", "byzantine", "roman"), mapOf("historic" to "archaeological_site")),
        KwEntry(listOf("aqueduct", "watermill", "bridge historic", "roman road", "ancient road"), mapOf("historic" to "bridge")),
        KwEntry(listOf("monument", "obelisk", "landmark", "statue", "sculpture", "memorial gate", "arch"), mapOf("historic" to "monument")),
        KwEntry(listOf("memorial", "grave", "cemetery", "tombstone", "mausoleum", "burial", "catacomb", "necropolis"), mapOf("historic" to "tomb")),
        // Religion
        KwEntry(listOf("church", "cathedral", "chapel", "basilica"), mapOf("amenity" to "place_of_worship", "religion" to "christian")),
        KwEntry(listOf("mosque", "minaret"), mapOf("amenity" to "place_of_worship", "religion" to "muslim")),
        KwEntry(listOf("synagogue"), mapOf("amenity" to "place_of_worship", "religion" to "jewish")),
        KwEntry(listOf("buddhist temple", "buddhist monastery", "pagoda", "stupa", "wat"), mapOf("amenity" to "place_of_worship", "religion" to "buddhist")),
        KwEntry(listOf("temple", "shrine", "monastery", "abbey", "convent"), mapOf("amenity" to "place_of_worship")),
        KwEntry(listOf("battlefield", "battle site", "war memorial"), mapOf("historic" to "battlefield")),
        // Culture
        KwEntry(listOf("planetarium", "science museum", "science center"), mapOf("tourism" to "museum")),
        KwEntry(listOf("art gallery", "photo gallery", "exhibition hall"), mapOf("tourism" to "gallery")),
        KwEntry(listOf("library"), mapOf("amenity" to "library")),
        KwEntry(listOf("museum"), mapOf("tourism" to "museum")),
        // Accommodation
        KwEntry(listOf("hotel", "motel", "inn", "resort", "lodge", "guesthouse", "b&b", "bed and breakfast"), mapOf("tourism" to "hotel")),
        KwEntry(listOf("hostel", "campsite", "camping", "caravan", "glamping", "tent", "alpine hut", "wilderness hut"), mapOf("tourism" to "camp_site")),
        // Health
        KwEntry(listOf("hospital", "clinic", "medical center", "urgent care", "emergency room"), mapOf("amenity" to "hospital")),
        KwEntry(listOf("pharmacy", "drugstore", "chemist"), mapOf("amenity" to "pharmacy")),
        // Education
        KwEntry(listOf("school", "university", "college", "academy", "campus"), mapOf("amenity" to "school")),
        // Wildlife
        KwEntry(listOf("zoo", "zoological garden", "wildlife park", "safari park", "aquarium"), mapOf("tourism" to "zoo")),
        KwEntry(listOf("national park", "nature reserve", "wildlife reserve", "ecological"), mapOf("leisure" to "nature_reserve")),
        KwEntry(listOf("forest", "woods", "woodland", "jungle"), mapOf("natural" to "wood")),
        KwEntry(listOf("park", "city park", "garden", "botanical garden", "playground"), mapOf("leisure" to "park")),
        // Observatory
        KwEntry(listOf("observatory", "telescope", "astronomy"), mapOf("man_made" to "tower")),
        // Services
        KwEntry(listOf("gas station", "petrol station", "fuel station", "filling station"), mapOf("amenity" to "fuel")),
        KwEntry(listOf("ev charging", "electric vehicle charging", "supercharger"), mapOf("amenity" to "charging_station")),
        KwEntry(listOf("parking", "car park", "parking lot"), mapOf("amenity" to "parking")),
        KwEntry(listOf("atm", "cash machine"), mapOf("amenity" to "atm")),
        KwEntry(listOf("bank"), mapOf("amenity" to "bank")),
        KwEntry(listOf("supermarket", "grocery", "market", "shopping center"), mapOf("shop" to "supermarket")),
        KwEntry(listOf("laundry", "dry cleaner", "laundromat"), mapOf("amenity" to "laundry")),
        // Transport
        KwEntry(listOf("airport", "terminal", "airfield", "airstrip"), mapOf("aeroway" to "aerodrome")),
        KwEntry(listOf("train station", "railway station", "metro station", "subway"), mapOf("railway" to "station")),
        KwEntry(listOf("bus station", "bus stop", "bus terminal"), mapOf("amenity" to "bus_station")),
        KwEntry(listOf("marina", "port", "harbor", "harbour", "dock", "pier", "jetty", "ferry"), mapOf("leisure" to "marina")),
        // Activities
        KwEntry(listOf("skiing", "ski resort", "ski slope", "piste", "snowboard"), mapOf("leisure" to "skiing")),
        KwEntry(listOf("golf course", "golf club"), mapOf("leisure" to "golf_course")),
        KwEntry(listOf("swimming pool", "aquatic center", "lido"), mapOf("leisure" to "swimming_pool")),        KwEntry(listOf("surfing", "surf spot"), mapOf("leisure" to "beach_resort")),
        KwEntry(listOf("kayak", "kayaking", "canoe", "rafting", "white water"), mapOf("leisure" to "water_park")),
        KwEntry(listOf("sailing", "yacht", "yachting"), mapOf("leisure" to "marina")),
        KwEntry(listOf("paragliding", "hang gliding"), mapOf("tourism" to "attraction")),
        KwEntry(listOf("gym", "fitness", "crossfit", "yoga", "pilates"), mapOf("leisure" to "fitness_centre")),
        KwEntry(listOf("climbing", "bouldering", "rock climbing", "via ferrata"), mapOf("natural" to "cliff")),
        KwEntry(listOf("tennis"), mapOf("leisure" to "pitch", "sport" to "tennis")),
        KwEntry(listOf("stadium", "arena", "sports complex"), mapOf("leisure" to "stadium")),
        KwEntry(listOf("fishing", "angling"), mapOf("leisure" to "fishing")),
        // Trails
        KwEntry(listOf("trailhead", "trail start", "trail head"), mapOf("tourism" to "information", "information" to "route_marker")),
        KwEntry(listOf("trail junction", "trail fork", "junction"), mapOf("tourism" to "information")),
        KwEntry(listOf("hiking", "trail", "trekking", "trek", "footpath", "walking route"), mapOf("leisure" to "nature_reserve")),
        KwEntry(listOf("cycling route", "bike path", "bicycle route"), mapOf("leisure" to "nature_reserve", "transport" to "bicycle")),
        // Emergency
        KwEntry(listOf("fire station", "firehouse"), mapOf("amenity" to "fire_station")),
        KwEntry(listOf("police station", "law enforcement"), mapOf("amenity" to "police")),
        // Entertainment
        KwEntry(listOf("concert hall", "music venue", "music hall"), mapOf("amenity" to "theatre")),
        KwEntry(listOf("cinema", "movie theater", "multiplex"), mapOf("amenity" to "cinema")),
        KwEntry(listOf("theater", "theatre", "opera house", "comedy club"), mapOf("amenity" to "theatre")),
        KwEntry(listOf("nightclub", "night club", "disco", "lounge"), mapOf("amenity" to "nightclub")),
        // Farm / agriculture
        KwEntry(listOf("vineyard", "wine estate", "farm", "orchard", "agritourism"), mapOf("tourism" to "vineyard")),
        // Diving
        KwEntry(listOf("scuba", "dive site", "diving", "snorkeling", "underwater"), mapOf("leisure" to "beach_resort")),
        // Village / settlement
        KwEntry(listOf("kibbutz", "moshav", "village", "hamlet", "rural settlement"), mapOf("place" to "village")),
    )

    private val KEYWORD_HE: List<KwEntry> = listOf(
        // ── Danger / hazard (highest priority) ───────────────────────────────
        KwEntry(heKw("תאונה", "תאונות", "סכנה", "מסוכן", "זהירות", "אזהרה"), mapOf("hazard" to "yes")),
        KwEntry(heKw("מוקש", "שדה מוקשים", "אין מעבר", "חסימה", "מעקה שבור"), mapOf("hazard" to "yes")),

        // ── Burial caves — must come before generic cave ──────────────────────
        KwEntry(heKw("מערת קבורה", "מערת קבר", "מערות קבורה"), mapOf("historic" to "tomb")),

        // ── Terrain ───────────────────────────────────────────────────────────
        KwEntry(heKw("פסגה", "מצוק", "גבעה", "ראש ההר", "ראש הר", "רכס", "גב הר", "כיפה"), mapOf("natural" to "peak")),
        KwEntry(listOf("הר "), mapOf("natural" to "peak")),         // standalone הר  (e.g. "הר הרמון")
        KwEntry(heKw("מעבר מים"), mapOf("historic" to "aqueduct")),              // water crossing — before generic "מעבר " saddle
        KwEntry(listOf("מעבר ", "צוואר", "אוכף"), mapOf("natural" to "saddle")),
        KwEntry(heKw("עמק", "גיא", "קניון", "ערוץ", "תהום"), mapOf("natural" to "cliff")),

        // ── Waterfalls ────────────────────────────────────────────────────────
        KwEntry(heKw("מפל", "מפלים", "מפלון"), mapOf("natural" to "waterfall")),

        // ── Streams / rivers / canals ─────────────────────────────────────────
        KwEntry(heKw("נחל"), mapOf("waterway" to "stream")),
        KwEntry(heKw("ואדי"), mapOf("waterway" to "river")),
        KwEntry(heKw("תעלה", "ערוץ מים"), mapOf("waterway" to "stream")),   // canal / water channel

        // ── Dams ──────────────────────────────────────────────────────────────
        KwEntry(heKw("סכר"), mapOf("man_made" to "dam")),

        // ── Beaches / coast ───────────────────────────────────────────────────
        KwEntry(heKw("חוף ים", "חוף הים", "חוף", "ים המלח", "כנרת", "ים סוף"), mapOf("natural" to "beach")),

        // ── Springs / wells / cisterns ────────────────────────────────────────
        KwEntry(heKw("מעיין", "מעין", "עין", "גבים", "נביעה", "בור מים", "בריכת מים", "בור", "באר", "מאגר מים"), mapOf("natural" to "spring")),
        KwEntry(heKw("מאגר"), mapOf("natural" to "water")),           // standalone reservoir
        KwEntry(listOf("גב "), mapOf("natural" to "water")),          // natural cistern pool (גב = rock basin)
        KwEntry(listOf("ביר ", "בירת "), mapOf("natural" to "well")), // Arabic بئر (well) in Israeli toponym names
        KwEntry(heKw("ברז", "ברז מים"), mapOf("amenity" to "drinking_water")),  // water tap → water

        // ── Hot springs ───────────────────────────────────────────────────────
        KwEntry(heKw("חמת", "חמי", "מעיין חם", "מעיינות חמים"), mapOf("natural" to "hot_spring")),  // → water

        // ── Swimming pools ────────────────────────────────────────────────────
        KwEntry(heKw("בריכת שחייה", "בריכה ציבורית", "בריכת ספורט"), mapOf("leisure" to "swimming_pool")),  // → water

        // ── Lakes / ponds ─────────────────────────────────────────────────────
        KwEntry(heKw("אגם", "בריכת חורף", "שלולית", "בריכה"), mapOf("natural" to "water")),

        // ── Wetlands ──────────────────────────────────────────────────────────
        KwEntry(heKw("ביצה", "אזור לח"), mapOf("natural" to "wetland")),

        // ── Caves ─────────────────────────────────────────────────────────────
        KwEntry(heKw("מערה", "מחילה", "נקיק", "מערות", "מערת", "פתח מערה"), mapOf("natural" to "cave_entrance")),

        // ── Rock formations / geological landmarks ────────────────────────────
        KwEntry(heKw("מסלעה", "מצוק", "חלמיש"), mapOf("natural" to "rock")),   // rocky area, cliff face, flint
        KwEntry(listOf("סלע "), mapOf("natural" to "rock")),                     // named rock/boulder ("סלע X")

        // ── Notable trees (very common POI type in Israeli hiking culture) ────
        KwEntry(listOf("עץ "), mapOf("natural" to "tree")),           // "עץ X" (named/notable individual trees)

        // ── Shrubs / plants / vegetation ──────────────────────────────────────
        KwEntry(heKw("שיח", "צמח", "עשב"), mapOf("natural" to "scrub")),

        // ── Forests / woodland ────────────────────────────────────────────────
        KwEntry(heKw("יער", "חורש", "שיטה", "אורן", "שיזף", "אלה", "חרוב", "אלון", "אקליפטוס", "זית", "דקל", "חורשת"), mapOf("natural" to "wood")),

        // ── Nature reserves / national parks ─────────────────────────────────
        KwEntry(heKw("שמורה", "גן לאומי", "גן טבע", "שמורת טבע"), mapOf("leisure" to "nature_reserve")),

        // ── Wildflowers / blooming ────────────────────────────────────────────
        KwEntry(heKw("פרח", "פרחים", "פריחה", "פריחת", "איריס", "אירוס", "כלנית", "נרקיס", "צבעון"), mapOf("leisure" to "garden")),

        // ── Viewpoints ────────────────────────────────────────────────────────
        KwEntry(heKw("תצפית", "נקודת תצפית", "מצפה", "מצפור"), mapOf("tourism" to "viewpoint")),
        KwEntry(heKw("מגדל תצפית", "מגדל שמירה"), mapOf("man_made" to "tower")),
        KwEntry(heKw("מגדל"), mapOf("historic" to "castle")),

        // ── Military ──────────────────────────────────────────────────────────
        KwEntry(heKw("בונקר", "מוצב", "מחפורת", "עמדת שמירה"), mapOf("historic" to "battlefield")),
        KwEntry(heKw("מצודה", "מבצר", "חומה"), mapOf("historic" to "castle")),

        // ── Heritage / archaeology ────────────────────────────────────────────
        KwEntry(heKw("הרוס", "חורבת", "חורבה", "חרבה", "חירבה", "חרבת", "שרידים", "עתיקות", "אתר עתיקות", "עתיק"), mapOf("historic" to "ruins")),
        KwEntry(listOf("ח'רבת", "חירבת", "ח'ירבת", "ח'רבה"), mapOf("historic" to "ruins")), // Arabic: خربة (khirbet/ruin)
        KwEntry(heKw("ציורי סלע", "פסיפס"), mapOf("tourism" to "gallery")),                  // rock art / mosaic → art-gallery
        KwEntry(heKw("ארכיאולוג", "ממצאים", "מאובנים", "כתובת", "גרן", "מבנה עתיק"), mapOf("historic" to "ruins")),
        KwEntry(listOf("תל "), mapOf("historic" to "ruins")),                                 // tel/tell (mound) → ruins
        KwEntry(heKw("גת", "בית בד"), mapOf("tourism" to "vineyard")),                       // winepress / oil press → farm
        KwEntry(heKw("כבשן"), mapOf("historic" to "ruins")),                                // kiln (lime/pottery) → ruins (historic, not agricultural)
        KwEntry(heKw("מטמורה", "קולומבריום", "כוך"), mapOf("natural" to "cave_entrance")),   // underground chambers → cave
        KwEntry(heKw("מבנה עתיק"), mapOf("historic" to "ruins")),
        KwEntry(heKw("דרך רומית", "דרך עתיקה", "דרך ביזנטית"), mapOf("historic" to "ruins")),
        KwEntry(heKw("אמת מים", "מעבר מים"), mapOf("historic" to "aqueduct")),               // ancient aqueduct / water channel
        KwEntry(heKw("עמוד", "עמודים"), mapOf("historic" to "monument")),                    // column / pillar (Roman, Byzantine)
        KwEntry(heKw("חאן"), mapOf("historic" to "archway")),                                 // caravanserai → archway/domed tower
        KwEntry(listOf("מבנה "), mapOf("historic" to "ruins")),                              // generic old building → ruins
        KwEntry(listOf("נ.ג", "ב.מ", "ר.ד"), mapOf("historic" to "monument")),               // geodetic survey benchmarks
        KwEntry(heKw("מחצבה"), mapOf("historic" to "quarry")),                               // quarry → quarry icon

        // ── Windmills / watermills / bridges ─────────────────────────────────
        KwEntry(heKw("טחנת רוח", "טחנות רוח"), mapOf("man_made" to "windmill")),
        KwEntry(heKw("טחנת", "גשר", "גשרון", "טראסות"), mapOf("historic" to "bridge")),

        // ── Religion ──────────────────────────────────────────────────────────
        KwEntry(heKw("מזבח", "פולחן", "בית מקדש"), mapOf("amenity" to "place_of_worship")),
        KwEntry(heKw("כנסייה", "קתדרלה", "כנסיה"), mapOf("amenity" to "place_of_worship", "religion" to "christian")),
        KwEntry(heKw("מנזר", "מנזרת", "קונבנט"), mapOf("amenity" to "place_of_worship", "religion" to "christian")),
        KwEntry(heKw("מסגד"), mapOf("amenity" to "place_of_worship", "religion" to "muslim")),
        KwEntry(heKw("מקדש"), mapOf("amenity" to "place_of_worship")),
        KwEntry(heKw("בית כנסת", "בית-כנסת"), mapOf("amenity" to "place_of_worship", "religion" to "jewish")),

        // ── Tombs / cemeteries ────────────────────────────────────────────────
        KwEntry(heKw("טומולי", "טומולוס", "קבר ארגז"), mapOf("historic" to "tomb")),
        KwEntry(heKw("קבר", "מצבה", "קברים", "בית קברות", "עלמין", "בית עלמין"), mapOf("historic" to "tomb")),
        KwEntry(heKw("אנדרטה", "הנצחה", "שלט הנצחה", "יד לחיילים"), mapOf("historic" to "memorial")),
        KwEntry(heKw("שואה", "יד ושם", "קורבן", "קורבנות"), mapOf("historic" to "memorial")),

        // ── Sculptures / artwork ──────────────────────────────────────────────
        KwEntry(heKw("פסל"), mapOf("historic" to "monument")),
        KwEntry(listOf("מונומנט"), mapOf("historic" to "monument")),   // loanword monument

        // ── City gates ────────────────────────────────────────────────────────
        KwEntry(heKw("שער עיר", "שער מבצר", "שער עתיק", "שער הכניסה", "שער", "מחסום"), mapOf("historic" to "city_gate")),

        // ── Emergency services ────────────────────────────────────────────────
        KwEntry(heKw("משטרה", "משטרת"), mapOf("amenity" to "police")),
        KwEntry(heKw("תחנת כיבוי", "מכבי אש"), mapOf("amenity" to "fire_station")),

        // ── Transport stations ────────────────────────────────────────────────
        KwEntry(listOf("תחנת רכבת", "תחנת הרכבת"), mapOf("railway" to "station")),
        KwEntry(listOf("תחנת אוטובוס", "תחנת האוטובוס"), mapOf("amenity" to "bus_station")),
        KwEntry(listOf("תחנת רכבל", "רכבל"), mapOf("tourism" to "attraction")),  // cable car

        // ── Trails / navigation ───────────────────────────────────────────────
        KwEntry(heKw("מרכז מבקרים", "מרכז מידע", "נקודת מידע"), mapOf("tourism" to "information")),  // visitor center
        KwEntry(heKw("נקודת התחלה", "נקודת סיום", "ניווט", "נקודת חובה"), mapOf("tourism" to "information")),
        KwEntry(heKw("פיצול שבילים", "צומת שבילים", "מפגש שבילים", "פיצול", "צומת", "מסעף"), mapOf("tourism" to "information")),
        KwEntry(listOf("סימן דרך", "סימן "), mapOf("tourism" to "information")),
        KwEntry(listOf("מפגש "), mapOf("tourism" to "information")),         // meeting point / junction (standalone)
        KwEntry(listOf("כניסה"), mapOf("tourism" to "information")),         // entrance
        KwEntry(listOf("כיכר"), mapOf("tourism" to "information")),          // square / roundabout
        KwEntry(listOf("אבן "), mapOf("historic" to "milestone")),           // stone marker / boundary stone
        KwEntry(listOf("ציר "), mapOf("leisure" to "nature_reserve")),       // hiking route axis
        KwEntry(heKw("שביל", "מסלול", "תוואי", "מעלה", "מורד"), mapOf("leisure" to "nature_reserve")),
        KwEntry(listOf("סינגל"), mapOf("leisure" to "nature_reserve", "transport" to "bicycle")),  // MTB trail

        // ── Picnic / outdoor recreation ───────────────────────────────────────
        KwEntry(heKw("פיקניק", "שולחן פיקניק", "אזור פיקניק", "פינת ישיבה", "בוסתן"), mapOf("tourism" to "picnic_site")),
        KwEntry(heKw("גן בוטני", "גן נוי", "גן ציבורי"), mapOf("leisure" to "garden")),
        KwEntry(heKw("גן", "פארק", "גינה"), mapOf("leisure" to "park")),
        KwEntry(heKw("טיילת"), mapOf("leisure" to "park")),                // promenade (very common in Israeli cities)

        // ── Zoo / wildlife ────────────────────────────────────────────────────
        KwEntry(heKw("גן חיות", "גן זאולוגי", "חי-בר", "חיבר", "אקווריום"), mapOf("tourism" to "zoo")),

        // ── Museum ────────────────────────────────────────────────────────────
        KwEntry(heKw("מוזיאון"), mapOf("tourism" to "museum")),
        KwEntry(heKw("גלריה"), mapOf("tourism" to "gallery")),

        // ── Winery / farm ─────────────────────────────────────────────────────
        KwEntry(heKw("יקב", "כרם"), mapOf("tourism" to "winery")),
        KwEntry(heKw("מטע", "חווה", "חוות", "לול", "רפת"), mapOf("tourism" to "vineyard")),

        // ── Sports / stadium ──────────────────────────────────────────────────
        KwEntry(heKw("אצטדיון", "מגרש", "היכל ספורט", "מרכז ספורט", "ספורט"), mapOf("leisure" to "stadium")),

        // ── Diving ────────────────────────────────────────────────────────────
        KwEntry(heKw("צלילה", "מצלול", "אתר צלילה", "שנורקלינג"), mapOf("leisure" to "beach_resort")),

        // ── Science / observatory ─────────────────────────────────────────────
        KwEntry(heKw("מרכז מדע", "פלנטריום", "מצפה כוכבים"), mapOf("tourism" to "museum")),

        // ── Library ───────────────────────────────────────────────────────────
        KwEntry(heKw("ספריה", "ספרייה"), mapOf("amenity" to "library")),

        // ── Cinema ────────────────────────────────────────────────────────────
        KwEntry(heKw("קולנוע"), mapOf("amenity" to "cinema")),

        // ── Theatre / performing arts ─────────────────────────────────────────
        KwEntry(heKw("תיאטרון"), mapOf("amenity" to "theatre")),
        KwEntry(heKw("אולם מופעים", "בית אופרה", "אולם קונצרטים"), mapOf("amenity" to "theatre")),

        // ── Tennis / fishing ──────────────────────────────────────────────────
        KwEntry(heKw("טניס", "מגרש טניס"), mapOf("leisure" to "pitch", "sport" to "tennis")),
        KwEntry(heKw("דיג", "מקום דיג", "אתר דיג"), mapOf("leisure" to "fishing")),

        // ── Camping ───────────────────────────────────────────────────────────
        KwEntry(heKw("קמפינג", "אוהל", "קמפ"), mapOf("tourism" to "camp_site")),
        KwEntry(heKw("מחנה"), mapOf("tourism" to "camp_site")),  // camp (Roman, IDF, hiking camp)
        KwEntry(heKw("מחסה", "מקלט"), mapOf("tourism" to "wilderness_hut")),  // hiking shelter / refuge

        // ── Village / settlement ──────────────────────────────────────────────
        KwEntry(heKw("כפר", "מושב", "קיבוץ", "ישוב", "מושבה"), mapOf("place" to "village")),
        KwEntry(heKw("שכונה"), mapOf("place" to "neighbourhood")),  // neighbourhood

        // ── Food & drink ──────────────────────────────────────────────────────
        KwEntry(heKw("עוגות", "עוגה", "מאפה", "פטיסרי"), mapOf("amenity" to "confectionery")),
        KwEntry(heKw("מסעדה"), mapOf("amenity" to "restaurant")),
        KwEntry(heKw("קפה", "בית קפה"), mapOf("amenity" to "cafe")),
        KwEntry(heKw("מאפייה", "פלאפל", "שווארמה", "המבורגר"), mapOf("amenity" to "fast_food")),

        // ── Services ──────────────────────────────────────────────────────────
        KwEntry(heKw("תחנת דלק", "תדלוק"), mapOf("amenity" to "fuel")),
        KwEntry(heKw("חניה", "חניון"), mapOf("amenity" to "parking")),
        KwEntry(heKw("בית חולים", "קופת חולים", "מרפאה"), mapOf("amenity" to "hospital")),
        KwEntry(heKw("בית ספר", "אוניברסיטה", "מכללה"), mapOf("amenity" to "school")),
        KwEntry(heKw("בנק", "כספומט"), mapOf("amenity" to "bank")),
        KwEntry(heKw("מרכול", "סופרמרקט", "קניון", "חנות"), mapOf("shop" to "supermarket")),

        // ── Accommodation ─────────────────────────────────────────────────────
        KwEntry(heKw("מלון", "בית הארחה", "אכסניה"), mapOf("tourism" to "hotel")),

        // ── Catch-all: "בית X" not matched by any specific compound above ─────
        // In Israeli hiking context, unclassified "בית X" are almost always ruins.
        KwEntry(listOf("בית "), mapOf("historic" to "ruins")),
    )

    // ── Hazard tags (not in OSM standard, used internally) ───────────────────

    private fun resolveHazard() = "danger"

    // ── Place= tag ────────────────────────────────────────────────────────────

    private fun resolvePlace(value: String) = when (value) {
        "city", "town" -> "town"
        "village", "hamlet" -> "village"
        else -> "information"
    }

    // ── Transport sub-tags ────────────────────────────────────────────────────

    private fun resolveTransportIcon(kw: Map<String, String>): String? {
        return when {
            kw["transport"] == "bicycle" -> "bicycle"
            else -> null
        }
    }

    // ── Core tag resolver — OSM tags → Maki icon key ─────────────────────────

    private fun resolveTagMap(tags: Map<String, String>): String {
        // Special: hazard (internal tag from keyword tables)
        if (tags["hazard"] == "yes") return resolveHazard()
        // Special: place tag
        tags["place"]?.let { return resolvePlace(it) }

        // amenity — check place_of_worship + religion compound first
        tags["amenity"]?.let { amenity ->
            if (amenity == "place_of_worship") {
                val religion = tags["religion"]
                return if (religion != null) RELIGION[religion] ?: "place-of-worship"
                else "place-of-worship"
            }
            AMENITY[amenity]?.let { return it }
        }
        tags["leisure"]?.let { LEISURE[it]?.let { icon -> return icon } }
        tags["natural"]?.let { NATURAL[it]?.let { icon -> return icon } }
        tags["historic"]?.let { HISTORIC[it]?.let { icon -> return icon } }
        tags["tourism"]?.let { TOURISM[it]?.let { icon -> return icon } }
        tags["man_made"]?.let { MAN_MADE[it]?.let { icon -> return icon } }
        tags["shop"]?.let { SHOP[it]?.let { icon -> return icon } }
        tags["railway"]?.let { v ->
            return when (v) {
                "station", "halt", "stop" -> "rail"
                "tram_stop" -> "rail-light"
                else -> "rail"
            }
        }
        tags["aeroway"]?.let { return "airport" }
        tags["waterway"]?.let { v ->
            return when (v) {
                "waterfall" -> "waterfall"
                "dam", "weir" -> "dam"
                else -> "water"
            }
        }
        // sport sub-tag (e.g. from keyword tables)
        if (tags["sport"] == "tennis") return "tennis"

        return "marker"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves an icon key from a Google Places type list.
     *
     * [name] is checked against the keyword tables first (highest confidence);
     * Google's type list is used as fallback only.
     */
    fun resolveForGoogleType(types: List<String>, name: String = ""): String {
        if (name.isNotBlank()) matchKeywords(normalize(name))?.let { return resolveTagMap(it) }
        for (type in types) {
            GOOGLE_TO_OSM[type]?.let { return resolveTagMap(it) }
        }
        return "marker"
    }

    /**
     * Resolves an icon key from a full OSM tags map.
     *
     * [name] and [desc] are checked against the keyword tables first (highest confidence);
     * OSM tags are used as fallback only. This prevents bad or generic tags (e.g.
     * tourism=attraction on a fortress, natural=tree on a Holocaust memorial) from
     * overriding clear signals in the POI name.
     */
    fun resolveForOsmTags(tags: Map<String, String>, name: String = "", desc: String = ""): String {
        if (name.isNotBlank()) matchKeywords(normalize(name))?.let { return resolveTagMap(it) }
        if (desc.isNotBlank()) matchKeywords(normalize(desc))?.let { return resolveTagMap(it) }
        return resolveTagMap(tags)
    }

    /**
     * Resolves an icon key from imported GPX data.
     *
     * Priority: typeStr > name (first keyword match wins).
     * For desc, counts hits per unique tag-set and picks the majority winner.
     */
    fun resolveForImported(typeStr: String, name: String, desc: String): String {
        // typeStr and name: first-match, priority order
        for (raw in listOfNotNull(
            typeStr.takeIf { it.isNotBlank() },
            name.takeIf { it.isNotBlank() }
        )) {
            matchKeywords(normalize(raw))?.let { return resolveTagMap(it) }
        }

        // desc: frequency-based — pick the tag-set with the most keyword hits
        desc.takeIf { it.isNotBlank() }?.let { rawDesc ->
            val text = normalize(rawDesc)
            val lower = text.lowercase()
            val hits = mutableMapOf<Map<String, String>, Int>()

            for (entry in KEYWORD_HE) {
                if (entry.keywords.any { text.contains(it) })
                    hits[entry.tags] = (hits[entry.tags] ?: 0) + 1
            }
            for (entry in KEYWORD_EN) {
                if (entry.keywords.any { lower.contains(it) })
                    hits[entry.tags] = (hits[entry.tags] ?: 0) + 1
            }

            hits.maxByOrNull { it.value }?.key?.let { return resolveTagMap(it) }
        }

        return "marker"
    }
}
