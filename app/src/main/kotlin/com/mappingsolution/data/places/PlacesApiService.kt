package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.BuildConfig
import com.mappingsolution.data.model.Poi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesApiService @Inject constructor(private val httpClient: OkHttpClient) {

    private val baseUrl = "https://places.googleapis.com/v1/places:searchNearby"
    private val detailBaseUrl = "https://places.googleapis.com/v1/places"
    private val v1BaseUrl = "https://places.googleapis.com/v1"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches nearby places centered on [lat]/[lng] within [radiusMeters].
     * Returns at most [maxCount] results (capped by the API at 20).
     */
    suspend fun fetchNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        maxCount: Int,
    ): List<Poi> {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isBlank()) {
            Log.w("PlacesApiService", "GOOGLE_PLACES_API_KEY is not set; skipping fetch")
            return emptyList()
        }
        Log.d("PlacesApiService", "fetchNearby: key=${apiKey.take(8)}… lat=$lat lng=$lng radius=$radiusMeters max=$maxCount")
        val effectiveMax = maxCount
        val body = JSONObject().apply {
            put("includedTypes", org.json.JSONArray(GOOGLE_PLACES_INCLUDED_TYPES))
            put("maxResultCount", effectiveMax)
            put("locationRestriction", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lng)
                    })
                    put("radius", radiusMeters)
                })
            })
        }.toString()
        Log.d("PlacesApiService", "fetchNearby request body: $body")

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", GOOGLE_PLACES_FIELD_MASK)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d("PlacesApiService", "fetchNearby response: HTTP ${response.code}, body=$responseBody")
                if (response.code == 429) throw QuotaExceededException("Google Places daily quota exhausted")
                if (!response.isSuccessful) {
                    Log.e("PlacesApiService", "fetchNearby HTTP ${response.code}: $responseBody")
                    return@runCatching emptyList()
                }
                val json = JSONObject(responseBody)
                val placesArray = json.optJSONArray("places") ?: return@runCatching emptyList()
                val now = System.currentTimeMillis()
                (0 until placesArray.length()).mapNotNull { i ->
                    runCatching {
                        val place = placesArray.getJSONObject(i)
                        val location = place.getJSONObject("location")
                        val name = place.getJSONObject("displayName").getString("text")
                        val typesArray = place.optJSONArray("types")
                        val types = if (typesArray != null) {
                            (0 until typesArray.length()).map { typesArray.getString(it) }
                        } else emptyList()
                        val resolvedIconKey = PoiIconResolver.resolveForGoogleType(types, name)
                        Poi(
                            id = place.getString("id"),
                            groupId = GOOGLE_PLACES_GROUP_ID,
                            name = name,
                            lat = location.getDouble("latitude"),
                            lng = location.getDouble("longitude"),
                            iconKey = resolvedIconKey,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }.getOrNull()
                }
            }
        }.getOrElse { e ->
            if (e is QuotaExceededException) throw e
            Log.e("PlacesApiService", "fetchNearby failed", e)
            emptyList()
        }
    }

    /**
     * Searches for places matching [query] using the Places Text Search API,
     * biased toward [biasLat]/[biasLng] within [radiusMeters].
     * Returns at most 5 results.
     */
    suspend fun searchText(
        query: String,
        biasLat: Double,
        biasLng: Double,
        radiusMeters: Double = 5000.0,
    ): List<Poi> {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isBlank()) {
            Log.w("PlacesApiService", "GOOGLE_PLACES_API_KEY is not set; skipping search")
            return emptyList()
        }
        val body = JSONObject().apply {
            put("textQuery", query)
            put("maxResultCount", 5)
            put("locationBias", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", biasLat)
                        put("longitude", biasLng)
                    })
                    put("radius", radiusMeters)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places:searchText")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", GOOGLE_PLACES_FIELD_MASK)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PlacesApiService", "searchText HTTP ${response.code}: ${response.body?.string()}")
                    return@runCatching emptyList()
                }
                val json = JSONObject(response.body!!.string())
                val placesArray = json.optJSONArray("places") ?: return@runCatching emptyList()
                val now = System.currentTimeMillis()
                (0 until placesArray.length()).mapNotNull { i ->
                    runCatching {
                        val place = placesArray.getJSONObject(i)
                        val location = place.getJSONObject("location")
                        val name = place.getJSONObject("displayName").getString("text")
                        val typesArray = place.optJSONArray("types")
                        val types = if (typesArray != null) {
                            (0 until typesArray.length()).map { typesArray.getString(it) }
                        } else emptyList()
                        val resolvedIconKey = PoiIconResolver.resolveForGoogleType(types, name)
                        Poi(
                            id = place.getString("id"),
                            groupId = GOOGLE_PLACES_GROUP_ID,
                            name = name,
                            lat = location.getDouble("latitude"),
                            lng = location.getDouble("longitude"),
                            iconKey = resolvedIconKey,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }.getOrNull()
                }
            }
        }.getOrElse { e ->
            Log.e("PlacesApiService", "searchText failed", e)
            emptyList()
        }
    }

    /**
     * Fetches photo URLs and contact info for a specific place in a single API call.
     */
    suspend fun fetchPlaceDetail(placeId: String): PlaceDetail {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isBlank()) return PlaceDetail()

        val request = Request.Builder()
            .url("$detailBaseUrl/$placeId")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", "photos,websiteUri,nationalPhoneNumber")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PlacesApiService", "fetchPlaceDetail HTTP ${response.code}")
                    return@runCatching PlaceDetail()
                }
                val json = JSONObject(response.body!!.string())
                val photos = json.optJSONArray("photos")
                val photoUrls = if (photos != null) {
                    (0 until photos.length()).mapNotNull { i ->
                        runCatching {
                            val photoName = photos.getJSONObject(i).getString("name")
                            "$v1BaseUrl/$photoName/media?maxWidthPx=800&key=$apiKey"
                        }.getOrNull()
                    }
                } else emptyList()
                val website = json.optString("websiteUri").ifEmpty { null }
                val phone = json.optString("nationalPhoneNumber").ifEmpty { null }
                val contact = if (website != null || phone != null) PlaceContactInfo(website, phone) else null
                PlaceDetail(photoUrls = photoUrls, contact = contact)
            }
        }.getOrElse { e ->
            Log.e("PlacesApiService", "fetchPlaceDetail failed", e)
            PlaceDetail()
        }
    }

    /**
     * Fetches website and phone number for a specific place by ID.
     * Returns null if the place has neither field or the API call fails.
     */
    suspend fun fetchPlaceContactInfo(placeId: String): PlaceContactInfo? {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isBlank()) return null

        val request = Request.Builder()
            .url("$detailBaseUrl/$placeId")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", "websiteUri,nationalPhoneNumber")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PlacesApiService", "fetchPlaceContactInfo HTTP ${response.code}")
                    return@runCatching null
                }
                val json = JSONObject(response.body!!.string())
                val website = json.optString("websiteUri").ifEmpty { null }
                val phone = json.optString("nationalPhoneNumber").ifEmpty { null }
                if (website == null && phone == null) null else PlaceContactInfo(website, phone)
            }
        }.getOrElse { e ->
            Log.e("PlacesApiService", "fetchPlaceContactInfo failed", e)
            null
        }
    }

    /**
     * Fetches all available photo URLs for a specific place by ID.
     * Photo URL format: https://places.googleapis.com/v1/{photoName}/media?maxWidthPx=800&key={key}
     */
    suspend fun fetchPlacePhotoUrls(placeId: String): List<String> {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isBlank()) return emptyList()

        val request = Request.Builder()
            .url("$detailBaseUrl/$placeId")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", "photos")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PlacesApiService", "fetchPlacePhotoUrls HTTP ${response.code}")
                    return@runCatching emptyList()
                }
                val json = JSONObject(response.body!!.string())
                val photos = json.optJSONArray("photos") ?: return@runCatching emptyList()
                (0 until photos.length()).mapNotNull { i ->
                    runCatching {
                        val photoName = photos.getJSONObject(i).getString("name")
                        // photoName = "places/{id}/photos/{ref}" — base is v1, not v1/places
                        "$v1BaseUrl/$photoName/media?maxWidthPx=800&key=$apiKey"
                    }.getOrNull()
                }
            }
        }.getOrElse { e ->
            Log.e("PlacesApiService", "fetchPlacePhotoUrls failed", e)
            emptyList()
        }
    }
}
