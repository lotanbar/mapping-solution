package com.mappingsolution.data.places

/** Thrown by [PlacesApiService.fetchNearby] when the Google Places API returns HTTP 429. */
class QuotaExceededException(message: String) : Exception(message)
