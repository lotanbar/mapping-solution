package com.mappingsolution.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.FilterHdr
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.Houseboat
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ScubaDiving
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Villa
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.vector.ImageVector

object IconCatalog {

    data class IconEntry(val key: String, val vector: ImageVector, val label: String)
    data class IconCategory(val name: String, val icons: List<IconEntry>)

    val categories: List<IconCategory> = listOf(
        IconCategory(
            "Location", listOf(
                IconEntry("place", Icons.Default.Place, "Place"),
                IconEntry("location_on", Icons.Default.LocationOn, "Location"),
                IconEntry("my_location", Icons.Default.MyLocation, "My Location"),
                IconEntry("explore", Icons.Default.Explore, "Explore"),
                IconEntry("travel_explore", Icons.Default.TravelExplore, "Travel"),
                IconEntry("navigation", Icons.Default.Navigation, "Navigation"),
                IconEntry("near_me", Icons.Default.NearMe, "Near Me"),
                IconEntry("gps_fixed", Icons.Default.GpsFixed, "GPS"),
                IconEntry("flag", Icons.Default.Flag, "Flag"),
                IconEntry("tour", Icons.Default.Tour, "Tour"),
                IconEntry("fork_right", Icons.Default.ForkRight, "Fork"),
                IconEntry("map", Icons.Default.Map, "Map"),
                IconEntry("push_pin", Icons.Default.PushPin, "Pin"),
                IconEntry("satellite", Icons.Default.Satellite, "Satellite"),
                IconEntry("location_city", Icons.Default.LocationCity, "City"),
            )
        ),
        IconCategory(
            "Nature", listOf(
                IconEntry("park", Icons.Default.Park, "Park"),
                IconEntry("terrain", Icons.Default.Terrain, "Mountain"),
                IconEntry("filter_hdr", Icons.Default.FilterHdr, "Volcano"),
                IconEntry("waves", Icons.Default.Waves, "Waterfall"),
                IconEntry("water", Icons.Default.Water, "River"),
                IconEntry("water_drop", Icons.Default.WaterDrop, "Water"),
                IconEntry("local_drink", Icons.Default.LocalDrink, "Spring"),
                IconEntry("hot_tub", Icons.Default.HotTub, "Hot Spring"),
                IconEntry("landscape", Icons.Default.Landscape, "Landscape"),
                IconEntry("nature", Icons.Default.Nature, "Nature"),
                IconEntry("nature_people", Icons.Default.NaturePeople, "Wildlife"),
                IconEntry("grass", Icons.Default.Grass, "Grass"),
                IconEntry("forest", Icons.Default.Forest, "Forest"),
                IconEntry("yard", Icons.Default.Yard, "Garden"),
                IconEntry("spa", Icons.Default.Spa, "Spa"),
                IconEntry("filter_vintage", Icons.Default.FilterVintage, "Flower"),
                IconEntry("local_florist", Icons.Default.LocalFlorist, "Florist"),
                IconEntry("agriculture", Icons.Default.Agriculture, "Farm"),
                IconEntry("eco", Icons.Default.Eco, "Eco"),
                IconEntry("wind_power", Icons.Default.WindPower, "Wind"),
                IconEntry("ac_unit", Icons.Default.AcUnit, "Snow"),
                IconEntry("wb_sunny", Icons.Default.WbSunny, "Sunny"),
                IconEntry("cloud", Icons.Default.Cloud, "Cloud"),
            )
        ),
        IconCategory(
            "Food & Drink", listOf(
                IconEntry("restaurant", Icons.Default.Restaurant, "Restaurant"),
                IconEntry("local_cafe", Icons.Default.LocalCafe, "Cafe"),
                IconEntry("local_bar", Icons.Default.LocalBar, "Bar"),
                IconEntry("fastfood", Icons.Default.Fastfood, "Fast Food"),
                IconEntry("lunch_dining", Icons.Default.LunchDining, "Lunch"),
                IconEntry("dinner_dining", Icons.Default.DinnerDining, "Dinner"),
                IconEntry("brunch_dining", Icons.Default.BrunchDining, "Brunch"),
                IconEntry("bakery_dining", Icons.Default.BakeryDining, "Bakery"),
                IconEntry("ramen_dining", Icons.Default.RamenDining, "Ramen"),
                IconEntry("local_pizza", Icons.Default.LocalPizza, "Pizza"),
                IconEntry("icecream", Icons.Default.Icecream, "Ice Cream"),
                IconEntry("cake", Icons.Default.Cake, "Cake"),
                IconEntry("wine_bar", Icons.Default.WineBar, "Wine"),
                IconEntry("coffee", Icons.Default.Coffee, "Coffee"),
            )
        ),
        IconCategory(
            "Activities", listOf(
                IconEntry("directions_walk", Icons.AutoMirrored.Filled.DirectionsWalk, "Walking"),
                IconEntry("directions_run", Icons.AutoMirrored.Filled.DirectionsRun, "Running"),
                IconEntry("directions_bike", Icons.AutoMirrored.Filled.DirectionsBike, "Cycling"),
                IconEntry("hiking", Icons.Default.Hiking, "Hiking"),
                IconEntry("fitness_center", Icons.Default.FitnessCenter, "Gym"),
                IconEntry("pool", Icons.Default.Pool, "Swimming"),
                IconEntry("sailing", Icons.Default.Sailing, "Sailing"),
                IconEntry("kayaking", Icons.Default.Kayaking, "Kayaking"),
                IconEntry("scuba_diving", Icons.Default.ScubaDiving, "Diving"),
                IconEntry("snowboarding", Icons.Default.Snowboarding, "Snowboarding"),
                IconEntry("downhill_skiing", Icons.Default.DownhillSkiing, "Skiing"),
                IconEntry("surfing", Icons.Default.Surfing, "Surfing"),
                IconEntry("sports_soccer", Icons.Default.SportsSoccer, "Soccer"),
                IconEntry("sports_basketball", Icons.Default.SportsBasketball, "Basketball"),
                IconEntry("sports_tennis", Icons.Default.SportsTennis, "Tennis"),
                IconEntry("stadium", Icons.Default.Stadium, "Stadium"),
                IconEntry("golf_course", Icons.Default.GolfCourse, "Golf"),
                IconEntry("paragliding", Icons.Default.Paragliding, "Paragliding"),
                IconEntry("outdoor_grill", Icons.Default.OutdoorGrill, "BBQ"),
                IconEntry("rowing", Icons.Default.Rowing, "Fishing"),
            )
        ),
        IconCategory(
            "Accommodation", listOf(
                IconEntry("hotel", Icons.Default.Hotel, "Hotel"),
                IconEntry("home", Icons.Default.Home, "Home"),
                IconEntry("apartment", Icons.Default.Apartment, "Apartment"),
                IconEntry("house", Icons.Default.House, "House"),
                IconEntry("cottage", Icons.Default.Cottage, "Cottage"),
                IconEntry("houseboat", Icons.Default.Houseboat, "Houseboat"),
                IconEntry("holiday_village", Icons.Default.HolidayVillage, "Village"),
                IconEntry("night_shelter", Icons.Default.NightShelter, "Shelter"),
                IconEntry("beach_access", Icons.Default.BeachAccess, "Beach"),
                IconEntry("king_bed", Icons.Default.KingBed, "Bed"),
                IconEntry("single_bed", Icons.Default.SingleBed, "Single Bed"),
                IconEntry("meeting_room", Icons.Default.MeetingRoom, "Room"),
            )
        ),
        IconCategory(
            "Transport", listOf(
                IconEntry("directions_car", Icons.Default.DirectionsCar, "Car"),
                IconEntry("directions_bus", Icons.Default.DirectionsBus, "Bus"),
                IconEntry("train", Icons.Default.Train, "Train"),
                IconEntry("flight", Icons.Default.Flight, "Flight"),
                IconEntry("motorcycle", Icons.Default.Motorcycle, "Motorcycle"),
                IconEntry("two_wheeler", Icons.Default.TwoWheeler, "Two Wheeler"),
                IconEntry("electric_car", Icons.Default.ElectricCar, "Electric Car"),
                IconEntry("ev_station", Icons.Default.EvStation, "EV Charge"),
                IconEntry("directions_boat", Icons.Default.DirectionsBoat, "Boat"),
                IconEntry("anchor", Icons.Default.Anchor, "Anchor"),
                IconEntry("local_taxi", Icons.Default.LocalTaxi, "Taxi"),
                IconEntry("tram", Icons.Default.Tram, "Tram"),
            )
        ),
        IconCategory(
            "Services", listOf(
                IconEntry("local_hospital", Icons.Default.LocalHospital, "Hospital"),
                IconEntry("local_pharmacy", Icons.Default.LocalPharmacy, "Pharmacy"),
                IconEntry("local_gas_station", Icons.Default.LocalGasStation, "Gas Station"),
                IconEntry("local_parking", Icons.Default.LocalParking, "Parking"),
                IconEntry("shopping_cart", Icons.Default.ShoppingCart, "Shopping"),
                IconEntry("storefront", Icons.Default.Storefront, "Store"),
                IconEntry("local_atm", Icons.Default.LocalAtm, "ATM"),
                IconEntry("account_balance", Icons.Default.AccountBalance, "Bank"),
                IconEntry("school", Icons.Default.School, "School"),
                IconEntry("local_police", Icons.Default.LocalPolice, "Police"),
                IconEntry("local_fire_department", Icons.Default.LocalFireDepartment, "Fire Dept"),
                IconEntry("local_laundry", Icons.Default.LocalLaundryService, "Laundry"),
                IconEntry("biotech", Icons.Default.Biotech, "Lab"),
            )
        ),
        IconCategory(
            "Entertainment", listOf(
                IconEntry("museum", Icons.Default.Museum, "Museum"),
                IconEntry("local_library", Icons.Default.LocalLibrary, "Library"),
                IconEntry("science", Icons.Default.Science, "Science"),
                IconEntry("music_note", Icons.Default.MusicNote, "Music"),
                IconEntry("nightlife", Icons.Default.Nightlife, "Nightlife"),
                IconEntry("theaters", Icons.Default.Theaters, "Cinema"),
                IconEntry("theater_comedy", Icons.Default.TheaterComedy, "Theatre"),
                IconEntry("celebration", Icons.Default.Celebration, "Festival"),
                IconEntry("casino", Icons.Default.Casino, "Casino"),
                IconEntry("sports_bar", Icons.Default.SportsBar, "Sports Bar"),
                IconEntry("sports_esports", Icons.Default.SportsEsports, "Gaming"),
                IconEntry("photo_camera", Icons.Default.PhotoCamera, "Gallery"),
                IconEntry("attractions", Icons.Default.Attractions, "Attractions"),
            )
        ),
        IconCategory(
            "Heritage & Religion", listOf(
                IconEntry("castle", Icons.Default.Castle, "Castle"),
                IconEntry("sensor_door", Icons.Default.SensorDoor, "Gate"),
                IconEntry("architecture", Icons.Default.Architecture, "Ruins"),
                IconEntry("foundation", Icons.Default.Foundation, "Archaeological"),
                IconEntry("villa", Icons.Default.Villa, "Manor"),
                IconEntry("history_edu", Icons.Default.HistoryEdu, "Historical"),
                IconEntry("church", Icons.Default.Church, "Church"),
                IconEntry("mosque", Icons.Default.Mosque, "Mosque"),
                IconEntry("synagogue", Icons.Default.Synagogue, "Synagogue"),
                IconEntry("temple_hindu", Icons.Default.TempleHindu, "Temple"),
                IconEntry("temple_buddhist", Icons.Default.TempleBuddhist, "Buddhist Temple"),
                IconEntry("local_cemetery", Icons.Default.Fence, "Cemetery"),
                IconEntry("military_tech", Icons.Default.MilitaryTech, "Military"),
                IconEntry("local_post_office", Icons.Default.LocalPostOffice, "Post Office"),
            )
        ),
        IconCategory(
            "Markers", listOf(
                IconEntry("star", Icons.Default.Star, "Star"),
                IconEntry("favorite", Icons.Default.Favorite, "Favorite"),
                IconEntry("bookmark", Icons.Default.Bookmark, "Bookmark"),
                IconEntry("label", Icons.AutoMirrored.Filled.Label, "Label"),
                IconEntry("warning", Icons.Default.Warning, "Warning"),
                IconEntry("crisis_alert", Icons.Default.CrisisAlert, "Danger"),
                IconEntry("info", Icons.Default.Info, "Info"),
                IconEntry("emergency", Icons.Default.Emergency, "Emergency"),
                IconEntry("whatshot", Icons.Default.Whatshot, "Hot"),
                IconEntry("bolt", Icons.Default.Bolt, "Bolt"),
                IconEntry("visibility", Icons.Default.Visibility, "Visible"),
                IconEntry("work", Icons.Default.Work, "Work"),
                IconEntry("business_center", Icons.Default.BusinessCenter, "Business"),
            )
        ),
    )

    private val allIcons: Map<String, ImageVector> by lazy {
        categories.flatMap { it.icons }.associate { it.key to it.vector }
    }

    fun iconVector(key: String): ImageVector = allIcons[key] ?: Icons.Default.Place
}
