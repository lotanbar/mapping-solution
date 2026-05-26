package com.mappingsolution.data.places

object PoiIconResolver {

    // ── Google Places direct map ──────────────────────────────────────────────

    private val GOOGLE_TYPE_TO_ICON = mapOf(
        "restaurant"           to "restaurant",
        "cafe"                 to "local_cafe",
        "bar"                  to "local_bar",
        "bakery"               to "bakery_dining",
        "fast_food_restaurant" to "fastfood",
        "coffee_shop"          to "coffee",
        "pizza_restaurant"     to "local_pizza",
        "sandwich_shop"        to "fastfood",
        "ice_cream_shop"       to "icecream",
        "dessert_shop"         to "cake",
        "wine_bar"             to "wine_bar",
        "brewery"              to "local_bar",
        "bank"                 to "account_balance",
        "atm"                  to "local_atm",
        "pharmacy"             to "local_pharmacy",
        "hospital"             to "local_hospital",
        "doctor"               to "local_hospital",
        "dentist"              to "local_hospital",
        "veterinary_care"      to "local_hospital",
        "supermarket"          to "shopping_cart",
        "grocery_store"        to "shopping_cart",
        "shopping_mall"        to "shopping_cart",
        "clothing_store"       to "storefront",
        "convenience_store"    to "storefront",
        "book_store"           to "storefront",
        "electronics_store"    to "storefront",
        "hardware_store"       to "storefront",
        "furniture_store"      to "storefront",
        "hotel"                to "hotel",
        "lodging"              to "hotel",
        "hostel"               to "night_shelter",
        "campground"           to "night_shelter",
        "gas_station"          to "local_gas_station",
        "electric_vehicle_charging_station" to "ev_station",
        "parking"              to "local_parking",
        "gym"                  to "fitness_center",
        "beauty_salon"         to "spa",
        "hair_salon"           to "spa",
        "spa"                  to "spa",
        "movie_theater"        to "theaters",
        "night_club"           to "nightlife",
        "casino"               to "casino",
        "museum"               to "museum",
        "art_gallery"          to "photo_camera",
        "library"              to "local_library",
        "park"                 to "park",
        "national_park"        to "nature",
        "zoo"                  to "nature_people",
        "aquarium"             to "pool",
        "amusement_park"       to "attractions",
        "tourist_attraction"   to "attractions",
        "stadium"              to "stadium",
        "sports_complex"       to "stadium",
        "sports_club"          to "sports_soccer",
        "golf_course"          to "golf_course",
        "bowling_alley"        to "sports_soccer",
        "school"               to "school",
        "university"           to "school",
        "airport"              to "flight",
        "train_station"        to "train",
        "subway_station"       to "train",
        "bus_station"          to "directions_bus",
        "ferry_terminal"       to "anchor",
        "marina"               to "anchor",
        "police"               to "local_police",
        "fire_station"         to "local_fire_department",
        "church"               to "church",
        "mosque"               to "mosque",
        "synagogue"            to "synagogue",
        "hindu_temple"         to "temple_hindu",
        "place_of_worship"     to "bookmark",
        "cemetery"             to "local_cemetery",
        "monument"             to "history_edu",
        "historical_landmark"  to "history_edu",
        "car_dealer"           to "directions_car",
        "car_rental"           to "directions_car",
        "car_repair"           to "directions_car",
        "laundry"              to "local_laundry",
        "post_office"          to "local_post_office",
        "real_estate_agency"   to "apartment",
        "accounting"           to "business_center",
        "lawyer"               to "business_center",
        "insurance_agency"     to "business_center",
    )

    // ── OSM tag-value direct map ───────────────────────────────────────────────

    private val OSM_NATURAL_TO_ICON = mapOf(
        "peak"           to "filter_hdr",
        "volcano"        to "filter_hdr",
        "cave_entrance"  to "explore",
        "waterfall"      to "waves",
        "glacier"        to "ac_unit",
        "hot_spring"     to "hot_tub",
        "geyser"         to "hot_tub",
    )

    private val OSM_HISTORIC_TO_ICON = mapOf(
        "monument"            to "history_edu",
        "memorial"            to "history_edu",
        "castle"              to "castle",
        "fort"                to "castle",
        "fortification"       to "castle",
        "city_gate"           to "castle",
        "archaeological_site" to "foundation",
        "ruins"               to "foundation",
        "building"            to "architecture",
        "manor"               to "villa",
        "place_of_worship"    to "bookmark",
        "wayside_shrine"      to "bookmark",
        "wayside_cross"       to "church",
        "tomb"                to "local_cemetery",
        "milestone"           to "push_pin",
        "boundary_stone"      to "push_pin",
        "ship"                to "anchor",
        "aircraft"            to "flight",
        "tank"                to "military_tech",
        "cannon"              to "military_tech",
        "battlefield"         to "military_tech",
    )

    private val OSM_OTHER_TO_ICON = mapOf(
        // leisure
        "nature_reserve"  to "nature",
        "park"            to "park",
        "garden"          to "park",
        "playground"      to "park",
        "sports_centre"   to "sports_soccer",
        "swimming_pool"   to "pool",
        "golf_course"     to "golf_course",
        "marina"          to "anchor",
        "beach"           to "beach_access",
        "picnic_table"    to "park",
        "dog_park"        to "park",
        "fishing"         to "rowing",
        "tennis"          to "sports_tennis",
        // amenity
        "observatory"     to "satellite",
        "restaurant"      to "restaurant",
        "cafe"            to "local_cafe",
        "bar"             to "local_bar",
        "pub"             to "local_bar",
        "fast_food"       to "fastfood",
        "food_court"      to "restaurant",
        "hospital"        to "local_hospital",
        "clinic"          to "local_hospital",
        "doctors"         to "local_hospital",
        "dentist"         to "local_hospital",
        "pharmacy"        to "local_pharmacy",
        "school"          to "school",
        "university"      to "school",
        "college"         to "school",
        "bank"            to "account_balance",
        "atm"             to "local_atm",
        "fuel"            to "local_gas_station",
        "charging_station" to "ev_station",
        "parking"         to "local_parking",
        "police"          to "local_police",
        "fire_station"    to "local_fire_department",
        "theatre"         to "theater_comedy",
        "cinema"          to "theaters",
        "museum"          to "museum",
        "library"         to "local_library",
        "arts_centre"     to "museum",
        "place_of_worship" to "bookmark",
        "bus_station"     to "directions_bus",
        "taxi"            to "local_taxi",
        "ferry_terminal"  to "anchor",
        "bicycle_rental"  to "directions_bike",
        "car_rental"      to "directions_car",
        "supermarket"     to "shopping_cart",
        "marketplace"     to "storefront",
        "laundry"         to "local_laundry",
        // tourism
        "viewpoint"       to "visibility",
        "hotel"           to "hotel",
        "hostel"          to "night_shelter",
        "camp_site"       to "night_shelter",
        "caravan_site"    to "night_shelter",
        "attraction"      to "attractions",
        "gallery"         to "photo_camera",
        "zoo"             to "nature_people",
        "information"     to "info",
        "picnic_site"     to "park",
        "wilderness_hut"  to "night_shelter",
        "alpine_hut"      to "night_shelter",
        "vineyard"        to "agriculture",
        "winery"          to "agriculture",
        // man_made
        "lighthouse"      to "anchor",
        "windmill"        to "wind_power",
        "water_tower"     to "local_drink",
        "tower"           to "visibility",
        // leisure
        "stadium"         to "stadium",
    )

    // ── Imported GPX fuzzy keyword table (priority-ordered) ───────────────────
    // Each entry: list of keywords → iconKey. First substring match wins.

    private val IMPORT_KEYWORD_TABLE: List<Pair<List<String>, String>> = listOf(
        // ── Precise food sub-types (before generic restaurant) ───────────────
        listOf("sushi", "japanese restaurant", "ramen", "noodle", "dumpling", "dim sum", "udon", "pho") to "ramen_dining",
        listOf("pizza")                                                              to "local_pizza",
        listOf("ice cream", "icecream", "gelato", "frozen yogurt", "sorbet")        to "icecream",
        listOf("cake", "dessert", "pastry", "patisserie", "sweets", "confection")   to "cake",
        listOf("bakery", "boulangerie", "bread")                                    to "bakery_dining",
        listOf("wine bar", "winery", "vineyard", "cellar")                          to "wine_bar",
        listOf("coffee", "espresso", "cappuccino", "latte", "barista")              to "coffee",
        listOf("cafe", "bistro cafe", "tea house", "tea room", "tearoom")           to "local_cafe",
        listOf("bar", "pub", "tavern", "brewery", "saloon", "taproom", "alehouse")  to "local_bar",
        listOf("fast food", "fastfood", "burger", "sandwich", "kebab", "shawarma",
               "falafel", "taco", "burrito", "wrap", "hot dog", "chips")            to "fastfood",
        listOf("steakhouse", "steak", "bbq", "barbeque", "barbecue", "grill",
               "seafood", "fish restaurant", "sushi restaurant")                    to "restaurant",
        listOf("restaurant", "diner", "eatery", "bistro", "buffet", "brasserie",
               "trattoria", "osteria", "cantina", "taverna", "tapas", "mezze")      to "restaurant",
        // ── Beach / coast ────────────────────────────────────────────────────
        listOf("beach", "shore", "coast", "seaside", "coastline", "bay")            to "beach_access",
        // ── Danger / accident spot ───────────────────────────────────────────
        listOf("accident", "accident spot", "crash site", "danger", "dangerous",
               "hazard", "hazardous")                                               to "crisis_alert",
        // ── Mountain / terrain (volcano gets its own icon now) ───────────────
        listOf("volcano", "volcanic", "lava field", "lava flow", "caldera") to "filter_hdr",
        listOf("mountain peak", "summit", "mountain top", "mountaintop")    to "terrain",
        listOf("mountain", "peak", "hill", "ridge", "cliff", "highland",
               "escarpment", "slope", "bluff", "butte", "mesa")                    to "terrain",
        // ── Canyon / gorge / valley (landscape icon = terrain layers) ────────
        listOf("canyon", "gorge", "ravine", "gully", "chasm")                       to "landscape",
        listOf("valley", "vale", "dale", "glen")                                    to "landscape",
        // ── Cave / cavern (explore = discovery/exploration) ──────────────────
        listOf("cave", "cavern", "grotto", "spelunking", "cave entrance")           to "explore",
        // ── Water features — split by type ────────────────────────────────────
        listOf("waterfall", "cascade", "rapids", "cataract")                        to "waves",
        listOf("river", "stream", "creek", "brook", "wadi", "torrent",
               "canal", "irrigation channel")                               to "water",
        listOf("lake", "pond", "lagoon", "reservoir", "tarn")               to "water",
        listOf("glacier", "icefield", "ice cap", "snowfield")               to "ac_unit",
        listOf("hot spring", "thermal spring", "thermal bath", "geyser",
               "hot pool", "geothermal")                                    to "hot_tub",
        listOf("spring", "well", "cistern", "water source", "fountain",
               "drinking water", "water tap", "water point")                to "local_drink",
        // ── Wetland / meadow / open land ─────────────────────────────────────
        listOf("wetland", "marsh", "swamp", "bog", "fen", "mangrove")               to "nature",
        listOf("meadow", "prairie", "steppe", "savanna", "heath", "moor")           to "nature",
        // ── Viewpoint ────────────────────────────────────────────────────────
        listOf("viewpoint", "overlook", "lookout", "vista", "panorama", "scenic",
               "belvedere", "mirador", "observation deck", "observation point",
               "observation tower", "panoramic point")                              to "visibility",
        // ── Lighthouse ───────────────────────────────────────────────────────
        listOf("lighthouse", "light house")                                          to "anchor",
        // ── Windmill / wind power (before ruins section, which catches "mill") ──
        listOf("windmill", "wind turbine", "wind farm", "wind power") to "wind_power",
        // ── Historical / archaeological ───────────────────────────────────────
        listOf("castle", "fort", "fortress", "citadel", "stronghold", "rampart",
               "battlement", "bastion", "keep", "tower house")                     to "castle",
        listOf("manor", "mansion", "estate", "chateau", "country house",
               "stately home", "palace")                                    to "villa",
        listOf("ruin", "ruins", "ancient", "archaeological", "archaeology",
               "excavation", "dig site", "historic site", "heritage site",
               "byzantine", "roman villa", "ancient city", "ancient town")  to "foundation",
        listOf("aqueduct", "watermill", "mill", "bridge historic",
               "roman road", "ancient road")                                to "architecture",
        listOf("monument", "obelisk", "landmark", "pillar", "column", "statue",
               "sculpture", "memorial gate", "arch", "triumphal arch")             to "history_edu",
        listOf("memorial", "grave", "cemetery", "tombstone", "mausoleum",
               "burial", "tomb", "catacomb", "necropolis", "cenotaph")             to "local_cemetery",
        // ── Religious sites ───────────────────────────────────────────────────
        listOf("church", "cathedral", "chapel", "basilica")                        to "church",
        listOf("mosque", "minaret")                                                 to "mosque",
        listOf("synagogue")                                                         to "synagogue",
        listOf("buddhist temple", "buddhist monastery", "pagoda",
               "zen temple", "shinto shrine", "wat", "stupa")              to "temple_buddhist",
        listOf("temple", "shrine",
               "monastery", "abbey", "convent", "priory")                  to "temple_hindu",
        listOf("pilgrimage", "holy site", "place of worship")                      to "bookmark",
        listOf("battlefield", "battle site", "war memorial")                       to "military_tech",
        // ── Museum / gallery / library / science (most-specific first) ──────────
        listOf("planetarium", "science museum", "natural history museum",
               "science center", "science centre")                              to "science",
        listOf("art gallery", "art museum", "photo gallery", "photography gallery",
               "gallery", "exhibit", "exhibition hall")                         to "photo_camera",
        listOf("public library", "national library", "city library",
               "library")                                                        to "local_library",
        listOf("museum")                                                          to "museum",
        // ── Accommodation ────────────────────────────────────────────────────
        listOf("hotel", "motel", "inn", "resort", "lodge", "accommodation",
               "guesthouse", "guest house", "pension", "chalet", "cottage",
               "b&b", "bed and breakfast", "hostal", "pousada")                    to "hotel",
        listOf("hostel", "campsite", "camp site", "camp ground", "camping",
               "caravan", "glamping", "tent", "bivouac", "hut", "alpine hut",
               "wilderness hut", "refuge", "bothy")                                to "night_shelter",
        listOf("home", "house", "residence", "villa", "farmhouse")                 to "home",
        listOf("apartment", "flat", "condominium")                                  to "apartment",
        // ── Health / Medical ──────────────────────────────────────────────────
        listOf("hospital", "clinic", "medical center", "health center",
               "urgent care", "emergency room")                                     to "local_hospital",
        listOf("pharmacy", "drugstore", "chemist", "apothecary")                   to "local_pharmacy",
        // ── Education ────────────────────────────────────────────────────────
        listOf("school", "university", "college", "academy", "campus",
               "kindergarten", "high school", "elementary school")                 to "school",
        // ── Nature / parks (zoo before park/nature to avoid wrong match) ────────
        listOf("zoo", "zoological garden", "wildlife park", "safari park",
               "animal park", "wildlife sanctuary", "aquarium")                 to "nature_people",
        listOf("national park", "nature reserve", "wildlife reserve",
               "nature sanctuary", "wildlife sanctuary", "habitat",
               "nature area", "ecological")                                         to "nature",
        listOf("forest", "woods", "woodland", "jungle", "rainforest", "grove")     to "forest",
        listOf("park", "city park", "garden", "botanical garden", "playground",
               "recreation area", "picnic")                                         to "park",
        // ── Observatory ──────────────────────────────────────────────────────
        listOf("observatory", "telescope", "astronomy")                             to "satellite",
        // ── Services ─────────────────────────────────────────────────────────
        listOf("gas station", "petrol station", "fuel station", "gas", "fuel",
               "petrol", "diesel", "refuel", "filling station")                    to "local_gas_station",
        listOf("parking", "car park", "parking lot", "garage parking")             to "local_parking",
        listOf("atm", "cash machine", "cash point")                                to "local_atm",
        listOf("bank", "finance", "credit union", "savings bank")                  to "account_balance",
        listOf("supermarket", "grocery", "market", "mall", "shopping center",
               "shop", "store", "boutique", "shopping")                            to "shopping_cart",
        listOf("laundry", "dry clean", "dry cleaner", "laundromat")                to "local_laundry",
        // ── Transport ────────────────────────────────────────────────────────
        listOf("airport", "terminal", "aviation", "airfield", "airstrip")          to "flight",
        listOf("train station", "railway station", "rail station",
               "train", "railway", "rail", "metro station", "subway station")      to "train",
        listOf("bus station", "bus stop", "bus terminal", "transit hub",
               "bus", "transit")                                                    to "directions_bus",
        listOf("taxi", "cab", "rideshare")                                          to "local_taxi",
        listOf("marina", "port", "harbor", "harbour", "dock", "pier",
               "jetty", "wharf", "ferry terminal", "boat dock")                    to "anchor",
        // ── Activities / sport ────────────────────────────────────────────────
        listOf("skiing", "ski resort", "ski slope", "piste", "ski run",
               "ski lift", "chairlift", "snowboard")                               to "downhill_skiing",
        listOf("golf course", "golf club", "golf")                                  to "golf_course",
        listOf("swimming", "swimming pool", "aquatic center", "lido")               to "pool",
        listOf("surfing", "surf spot", "surf break")                                to "surfing",
        listOf("kayak", "kayaking", "canoe", "canoeing", "rafting",
               "white water", "whitewater")                                         to "kayaking",
        listOf("sailing", "yacht", "yachting", "regatta")                          to "sailing",
        listOf("paragliding", "hang gliding", "gliding", "paraglider launch")      to "paragliding",
        listOf("gym", "fitness", "fitness center", "workout", "crossfit",
               "yoga", "pilates", "aerobics", "spin", "weightlifting")             to "fitness_center",
        listOf("climbing", "bouldering", "rock climbing", "via ferrata",
               "climbing wall", "crag")                                             to "hiking",
        listOf("stadium", "arena", "amphitheater", "sports complex",
               "sports hall")                                                    to "stadium",
        listOf("tennis", "squash", "badminton", "table tennis", "racquetball",
               "racquet sports", "paddle tennis", "pickleball")            to "sports_tennis",
        listOf("tennis", "volleyball", "basketball court", "football pitch",
               "baseball", "rugby", "cricket", "badminton", "squash")      to "sports_soccer",
        listOf("stadium", "arena", "sports complex", "sports center",
               "sports hall")                                                       to "stadium",
        listOf("fishing", "angling", "fish")                               to "rowing",
        // ── Trail navigation ──────────────────────────────────────────────────
        listOf("trailhead", "trail start", "trail end", "trail head",
               "start point", "end point", "navigation point")                     to "navigation",
        listOf("trail junction", "trail fork", "trail split", "intersection",
               "crossroads", "junction")                                            to "near_me",
        listOf("hiking", "trail", "trekking", "trek", "walk path",
               "footpath", "walking route", "long distance trail",
               "waymark", "waymarked")                                              to "hiking",
        listOf("cycling route", "bike path", "bicycle route",
               "cycling", "bicycle", "bike")                                       to "directions_bike",
        // ── Emergency services ────────────────────────────────────────────────
        listOf("fire station", "fire department", "firehouse")                      to "local_fire_department",
        listOf("police", "police station", "law enforcement", "security post")      to "local_police",
        // ── Entertainment ─────────────────────────────────────────────────────
        listOf("concert", "concert hall", "music venue", "music hall",
               "music")                                                         to "music_note",
        listOf("cinema", "movie theater", "movie theatre", "multiplex",
               "film", "drive-in")                                              to "theaters",
        listOf("theater", "theatre", "playhouse", "opera house", "opera",
               "comedy club", "comedy show", "stand-up", "improv")             to "theater_comedy",
        listOf("nightclub", "night club", "club", "disco", "lounge",
               "cocktail bar")                                                      to "nightlife",
        listOf("casino", "gambling")                                                to "casino",
        // ── Farm / vineyard / agriculture ─────────────────────────────────────
        listOf("vineyard", "winery", "wine estate", "wine farm",
               "farm", "farmhouse", "ranch", "orchard", "olive grove",
               "agricultural", "agritourism")                                   to "agriculture",
        // ── Scuba / diving ─────────────────────────────────────────────────────
        listOf("scuba", "scuba diving", "dive site", "dive spot", "diving",
               "snorkeling", "snorkelling", "underwater")                       to "scuba_diving",
        // ── Festival / celebration ──────────────────────────────────────────────
        listOf("festival", "carnival", "fair", "celebration", "event venue",
               "events", "party venue")                                         to "celebration",
        // ── EV charging ────────────────────────────────────────────────────────
        listOf("ev charging", "ev station", "electric vehicle charging",
               "charging station", "supercharger", "fast charger")         to "ev_station",
        // ── Village / rural settlement ─────────────────────────────────────────
        listOf("kibbutz", "moshav", "village", "hamlet", "rural settlement",
               "rural community", "agricultural settlement")               to "holiday_village",
        // ── Courtyard / ornamental garden ──────────────────────────────────────
        listOf("courtyard", "patio garden", "ornamental garden",
               "walled garden", "zen garden", "japanese garden")           to "yard",
        // ── Building / office ─────────────────────────────────────────────────
        listOf("building", "office", "office building")                    to "apartment",
    )

    // ── Imported GPX fuzzy keyword table — Hebrew ────────────────────────────
    // Covers common hiking/trail POI terminology in Israeli GPX exports.
    // Words are matched as substrings (case doesn't apply to Hebrew).

    private val IMPORT_HEBREW_KEYWORD_TABLE: List<Pair<List<String>, String>> = listOf(
        // ── DANGER / ACCIDENT — highest priority so e.g. "צומת מועד לתאונות" wins ──
        listOf("תאונה", "תאונות", "סכנה", "סכנות", "סכנת", "מסוכן", "מסוכנת",
               "זהירות", "אזהרה", "הזהר") to "crisis_alert",

        // Burial caves — must be before generic cave/water so they win
        listOf("מערת קבורה", "מערת קבר", "מערות קבורה", "מערת קבורות") to "local_cemetery",

        // Terrain / topography
        // NOTE: standalone "ראש" EXCLUDED — it is too generic (appears in "ראש העין" etc.)
        // NOTE: standalone "גב" EXCLUDED — too generic (suffix/prefix in many unrelated words)
        // NOTE: "מעלה"/"מורד" moved to hiking (trail ascent/descent markers, not mountain tops)
        listOf("פסגה", "מצוק", "גבעה", "ראש ההר", "ראש הר", "רכס", "גב הר", "כיפה") to "terrain",
        listOf("מעבר ", "צוואר", "אוכף") to "landscape",  // mountain pass/col — trailing space avoids "מעברות"

        // Canyon / gorge / valley
        listOf("עמק", "גיא", "קניון", "ערוץ", "תהום") to "landscape",

        // Water features — rivers/streams get "water" icon; springs/wells get "local_drink"
        listOf("מפל", "מפלים", "מפלון") to "waves",
        listOf("נחל") to "water",
        listOf("ואדי") to "water",
        listOf("סכר") to "water",  // dam / weir
        // NOTE: "ים" alone EXCLUDED — it is the Hebrew plural suffix (שרידים, קברים, etc.)
        listOf("חוף ים", "חוף הים", "חוף", "ים המלח", "כנרת", "ים סוף") to "beach_access",
        listOf("מעיין", "מעין", "עין", "גבים", "נביעה",
               "בור מים", "בריכת מים", "בורות מים", "בורות",
               "בור", "באר", "באר מים", "מאגר מים", "ברז מים", "ציר מים",
               "מגדל מים", "מגדל המים", "מעביר מים", "מעבר מים") to "local_drink",
        listOf("חמת", "חמי", "מעיין חם", "מעיינות חמים", "בריכת חמים") to "hot_tub",
        listOf("בריכת שחייה", "בריכה ציבורית", "בריכה עירונית", "בריכת ספורט",
               "בריכת אולימפי") to "pool",     // explicit swimming pools → swimming person
        listOf("אגם", "בריכת חורף", "שלולית", "בריכה") to "water",  // natural water bodies → water icon
        listOf("ביצה", "אזור לח") to "nature",

        // Caves — after burial cave entry; now mapped to explore (not landscape)
        listOf("מערה", "מחילה", "נקיק", "מערות", "מערת", "מערונת", "פתח מערה") to "explore",

        // Flora / trees / flowers — eucalyptus and common grove types first
        listOf("יער", "חורש", "שיטה", "שיטים", "אורן", "שיזף", "אלה", "חרוב",
               "אלון", "אשל", "עץ שיטה", "עץ שיזף", "עץ חרוב", "חורשת",
               "אקליפטוס", "אקליפטוסים", "זית", "תאנה", "דקל", "עצים",
               "זולת") to "forest",
        listOf("שמורה", "גן לאומי", "גן טבע", "שמורת טבע") to "nature",
        // Flowers — "אירוס" catches אירוסים, אירוס שחום, אירוס הסרגל; "חלמון" catches חלמוניות
        listOf("פרח", "פרחים", "פריחה", "איריס", "אירוס", "כלנית", "נרקיס",
               "חלמון", "חצב", "צבעון", "בולבוס") to "filter_vintage",

        // Viewpoints / observation towers
        listOf("תצפית", "נקודת תצפית", "מצפה", "מצפור") to "visibility",
        listOf("מגדל תצפית", "מגדל שמירה", "מגדל שדה") to "visibility",
        listOf("שומרה", "מגדל") to "castle",  // standalone watchtower/tower → castle shape

        // Danger / warnings
        listOf("מוקש", "מוקשי", "שדה מוקשים", "אין מעבר", "חסימה", "בורות פתוחים",
               "מחסום", "דרך חסומה", "דרך משובשת",
               "מעקה שבור", "מזבלה") to "warning",

        // Military sites
        listOf("בונקר", "מוצב", "מחפורת", "עמדת שמירה") to "military_tech",
        listOf("מצודה", "מבצר", "חומה") to "castle",

        // Historical / archaeological — ruins/sites get "foundation"; artifacts/finds stay "architecture"
        // "הרוס" is here explicitly (not under terrain) to beat "הר" false-match concern
        listOf("הרוס", "חורבת", "חורבה", "חרבה", "חירבה", "חרבת", "שרידים", "שרידי",
               "עתיקות", "אתר עתיקות", "עתיק", "עתיקה") to "foundation",
        listOf("ארכיאולוג", "ממצאים", "מאובנים", "מחצבה",
               "ציורי סלע", "כתובת", "פסיפס", "קו העתק") to "architecture",
        listOf("תל ") to "foundation",  // Tel (archaeological mound) — trailing space avoids mid-word
        listOf("גת", "כבשן", "בית בד") to "architecture",  // wine press, lime kiln, olive press
        listOf("מטמורה", "מטמורות", "קולומבריום", "כוך", "ספלול", "מאגורה",
               "מבנה", "מבנה נטוש", "מבנה אבן", "מבנה עתיק") to "architecture",
        listOf("טחנת רוח", "טחנות רוח") to "wind_power",      // windmill — BEFORE generic טחנת
        listOf("טחנת", "גשר", "גשרון", "טראסות", "מדרגה", "מלכודת") to "architecture",  // mill, bridge, terraces
        listOf("דרך רומית", "דרך עתיקה", "דרך ביזנטית") to "architecture",

        // Religious sites — specific icons per faith
        listOf("מזבח", "פולחן", "בית מקדש") to "bookmark",
        listOf("כנסייה", "קתדרלה", "כנסיה") to "church",
        listOf("מנזר", "מנזרת", "קונבנט") to "church",  // monastery/convent — typically Christian in Israel
        listOf("מסגד") to "mosque",
        listOf("מקדש") to "temple_hindu",
        listOf("בית כנסת", "בית-כנסת", "כנסת") to "synagogue",  // "כנסת" catches "בית הכנסת"

        // Cemeteries / burial mounds / memorials
        listOf("טומולי", "טומולוס", "קבר ארגז") to "local_cemetery",  // burial mounds — before generic קבר
        listOf("קבר", "מצבה", "קברים", "בית קברות", "עלמין", "בית עלמין") to "local_cemetery",
        listOf("אנדרט", "הנצחה", "שלט הנצחה", "יד לחיילים") to "history_edu",  // war memorials/monuments

        // Gates / entrances — specific phrases first, then bare שער
        listOf("שער עיר", "שער מבצר", "שער עתיק", "שער הכניסה", "שער") to "sensor_door",

        // Emergency services
        listOf("משטרה", "משטרת") to "local_police",
        listOf("תחנת כיבוי", "מכבי אש") to "local_fire_department",

        // Trails / navigation — "מעלה"/"מורד" moved here (trail ascent/descent, not mountain tops)
        listOf("נקודת התחלה", "נקודת סיום", "ניווט", "נקודה") to "hiking",
        listOf("פיצול שבילים", "צומת שבילים", "מפגש שבילים", "מפגש",
               "פיצול", "צומת", "מסעף") to "fork_right",
        listOf("סימן דרך", "סימן ") to "hiking",  // trail marker/sign
        listOf("שביל", "מסלול", "תוואי", "נקודת חובה", "מעלה", "מורד") to "hiking",

        // Parks / recreation areas
        listOf("פיקניק", "שולחן פיקניק", "שולחנות", "אזור פיקניק", "פינת ישיבה", "בוסתן") to "park",
        listOf("גן בוטני", "גן נוי", "גן ציבורי") to "local_florist",  // botanical/ornamental garden — before generic גן
        listOf("גן", "פארק", "גינה") to "park",

        // Zoo / wildlife
        listOf("גן חיות", "גן זאולוגי", "חי-בר", "חיבר", "אקווריום") to "nature_people",

        // Farm / vineyard — יקב/כרם explicitly go to wine, not farm tractor
        listOf("יקב", "כרם") to "wine_bar",
        listOf("מטע", "חווה", "חוות", "לול", "רפת") to "agriculture",

        // Stadium / arena
        listOf("אצטדיון", "מגרש", "היכל ספורט") to "stadium",

        // Diving
        listOf("צלילה", "מצלול", "אתר צלילה", "שנורקלינג") to "scuba_diving",

        // Festival / celebration
        listOf("פסטיבל", "חגיגה", "אירוע", "כנס") to "celebration",

        // Science / education
        listOf("מרכז מדע", "פלנטריום", "מצפה כוכבים") to "science",
        listOf("ספריה", "ספרייה", "בית ספר לאמנות") to "local_library",

        // Tennis / racquet sports
        listOf("טניס", "מגרש טניס", "ספורט מחבט") to "sports_tennis",

        // Fishing
        listOf("דיג", "מקום דיג", "אתר דיג") to "rowing",

        // Village / rural settlement
        listOf("כפר", "מושב", "קיבוץ", "ישוב", "מושבה") to "holiday_village",

        // Food & drink
        listOf("עוגות", "עוגה", "מאפה", "פטיסרי") to "cake",
        listOf("מסעדה") to "restaurant",
        listOf("קפה", "בית קפה") to "local_cafe",
        listOf("מאפייה", "פלאפל", "שווארמה", "המבורגר") to "fastfood",

        // Services
        listOf("תחנת דלק", "תדלוק") to "local_gas_station",
        listOf("חניה", "חניון") to "local_parking",
        listOf("בית חולים", "קופת חולים", "מרפאה") to "local_hospital",
        listOf("בית ספר", "אוניברסיטה", "מכללה") to "school",
        listOf("בנק", "כספומט") to "account_balance",
        listOf("מרכול", "סופרמרקט", "קניון", "חנות") to "shopping_cart",

        // Accommodation
        listOf("מלון", "בית הארחה", "אכסניה") to "hotel",
        listOf("קמפינג", "אוהל", "קמפ") to "night_shelter",
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves an icon key from a Google Places type string.
     * Iterates the types array (caller passes each element) until a known type is matched.
     * Returns "place" as fallback.
     */
    fun resolveForGoogleType(types: List<String>): String {
        for (type in types) {
            val icon = GOOGLE_TYPE_TO_ICON[type]
            if (icon != null) return icon
        }
        return "place"
    }

    /**
     * Resolves an icon key from an OSM tags map.
     * Checks natural → historic → leisure/amenity/tourism/man_made keys in order.
     * Returns "place" as fallback.
     */
    fun resolveForOsmTags(tags: Map<String, String>): String {
        tags["natural"]?.let { OSM_NATURAL_TO_ICON[it] }?.let { return it }
        tags["historic"]?.let { OSM_HISTORIC_TO_ICON[it] }?.let { return it }
        tags["leisure"]?.let { OSM_OTHER_TO_ICON[it] }?.let { return it }
        tags["amenity"]?.let { OSM_OTHER_TO_ICON[it] }?.let { return it }
        tags["tourism"]?.let { OSM_OTHER_TO_ICON[it] }?.let { return it }
        tags["man_made"]?.let { OSM_OTHER_TO_ICON[it] }?.let { return it }
        // Additional OSM keys
        tags["shop"]?.let { v ->
            when (v) {
                "supermarket", "convenience", "grocery" -> return "shopping_cart"
                "clothes", "shoes", "jewelry"           -> return "storefront"
                "bakery"                                -> return "bakery_dining"
                "coffee"                                -> return "local_cafe"
                "alcohol", "wine"                       -> return "wine_bar"
                "car", "car_repair"                     -> return "directions_car"
                "bicycle"                               -> return "directions_bike"
                "fuel"                                  -> return "local_gas_station"
                else                                    -> return "storefront"
            }
        }
        tags["railway"]?.let { v ->
            when (v) {
                "station", "halt", "stop" -> return "train"
                "tram_stop"               -> return "tram"
                else                      -> null
            }
        }
        tags["aeroway"]?.let { v ->
            when (v) {
                "aerodrome", "terminal" -> return "flight"
                else                    -> null
            }
        }
        tags["waterway"]?.let { v ->
            when (v) {
                "river", "stream", "canal", "drain", "ditch" -> return "water"
                "waterfall"                                   -> return "waves"
                "dam", "weir", "reservoir"                    -> return "local_drink"
                else                                          -> null
            }
        }
        return "place"
    }

    /**
     * Resolves an icon key from imported GPX data.
     * Priority: typeStr > name > desc. Both Hebrew and English tables are checked per field.
     * Description is only consulted as a last resort after typeStr and name both fail.
     * Returns "place" as fallback.
     */
    fun resolveForImported(typeStr: String, name: String, desc: String): String {
        fun normalize(s: String) = s.replace("\u200F", "").replace("\u200E", "").replace("\u200B", "")

        fun matchHebrew(text: String): String? {
            for ((keywords, iconKey) in IMPORT_HEBREW_KEYWORD_TABLE) {
                if (keywords.any { text.contains(it) }) return iconKey
            }
            return null
        }

        fun matchEnglish(text: String): String? {
            val lower = text.lowercase()
            for ((keywords, iconKey) in IMPORT_KEYWORD_TABLE) {
                if (keywords.any { lower.contains(it) }) return iconKey
            }
            return null
        }

        // Priority: typeStr > name > desc — check both tables per field.
        // Description is only consulted after typeStr and name both fail.
        for (raw in listOfNotNull(typeStr.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })) {
            val text = normalize(raw)
            matchHebrew(text)?.let { return it }
            matchEnglish(text)?.let { return it }
        }

        // Desc as last resort
        desc.takeIf { it.isNotBlank() }?.let { raw ->
            val text = normalize(raw)
            matchHebrew(text)?.let { return it }
            matchEnglish(text)?.let { return it }
        }

        return "place"
    }

    // ── Legacy single-source helpers (kept for any direct callers) ────────────

    /**
     * Resolves an icon key from a freeform imported GPX <type> string only.
     * Prefer [resolveForImported] which also considers name/desc as fallback.
     */
    fun resolveForImportedType(typeStr: String): String =
        resolveForImported(typeStr, "", "")
}
