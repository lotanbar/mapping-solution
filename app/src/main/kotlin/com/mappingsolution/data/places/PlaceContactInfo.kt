package com.mappingsolution.data.places

data class PlaceContactInfo(
    val website: String?,
    val phone: String?,
)

/** Combined detail fetched from Google Places in a single API call. */
data class PlaceDetail(
    val photoUrls: List<String> = emptyList(),
    val contact: PlaceContactInfo? = null,
)
