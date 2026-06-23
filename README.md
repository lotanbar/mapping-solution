# MappingSolution

An Android app for recording GPS data and monitoring trips. Built with modern Kotlin / Jetpack Compose.

---

## Features

- **Map** — MapTiler satellite hybrid, takes 80% of the screen. Shows POIs and routes toggled from the library.
- **POIs** — Add at current location, assign to a group (inherits group icon + color), attach photos/videos/audio.
- **Route recording** — Foreground service with live polyline, pause/resume/stop. Kalman-filtered GPS refined by on-device HMM/Viterbi map-matching (live windowed matcher + full re-match on stop). Auto-saves on force-kill as an *incomplete* recording.
- **Library** — Browse/search groups, POIs, and routes. Toggle visibility, multi-select, delete, orphan, re-group.
- **Import** — GPX files only (see below).

---

## Data Models

### Group
| Field | Type | Notes |
|---|---|---|
| id | String (UUID) | |
| name | String | |
| description | String? | optional |
| iconKey | String | key into icon catalog |
| color | String | hex, e.g. `#FFFF5722` |
| isVisible | Boolean | |
| createdAt | Long | epoch ms |
| updatedAt | Long | epoch ms |

### POI
| Field | Type | Notes |
|---|---|---|
| id | String (UUID) | |
| groupId | String? | optional |
| name | String | |
| description | String? | optional |
| lat | Double | |
| lng | Double | |
| elevation | Double? | optional, metres |
| mediaPaths | List\<String\> | relative paths to local media files |
| isVisible | Boolean | |
| createdAt | Long | epoch ms |
| updatedAt | Long | epoch ms |

### Route
| Field | Type | Notes |
|---|---|---|
| id | String (UUID) | |
| name | String | |
| description | String? | optional |
| color | String | hex, e.g. `#FFFF5722` — routes do not belong to groups |
| isVisible | Boolean | |
| didUserTapStop | Boolean | `false` = incomplete/force-killed recording |
| startedAt | Long | epoch ms |
| stoppedAt | Long? | epoch ms |
| checkpointAt | Long | epoch ms |
| distanceMeters | Double | |
| durationSec | Long | |
| points | List\<RoutePoint\> | ordered GPS path |

### RoutePoint
| Field | Type |
|---|---|
| ts | Long (epoch ms) |
| lat | Double |
| lng | Double |

---

## File Storage

```
(internal storage)/
  media/
    pois/{poi-name}_{shortId}/   ← POI media files
  imports/                        ← temporary during import
  exports/
  temp/
```

Groups, POIs, and routes are stored as JSON files:
```
groups/{Name}.json
pois/{Name}_{shortId}/
recordings/{timestamp-distance}_{shortId}/
```

---

## Import

**Only GPX files are supported.**

### Folder structure
Pick a folder via the system file picker. The importer expects:
```
MyImport/
  data.gpx          ← one or more .gpx files
  images/           ← optional; image files referenced by waypoints
    photo1.jpg
    photo2.jpg
```
- All POIs and routes from the folder are assigned to a single group named after the folder.
- The group is visible by default.
- Non-`.gpx` files are silently skipped.

### GPX field mapping

#### Waypoints → POI
| GPX tag | POI field | Notes |
|---|---|---|
| `lat` attribute | `lat` | |
| `lon` attribute | `lng` | |
| `<name>` | `name` | defaults to `"Unnamed POI"` if absent |
| `<desc>` | `description` | |
| `<ele>` | `elevation` | metres |
| `<time>` | `createdAt` | ISO 8601 |
| `<extensions><images>` | `mediaPaths` | comma-separated filenames from the `images/` subfolder |

#### Tracks / Routes → Route
| GPX tag | Route field | Notes |
|---|---|---|
| `<name>` | `name` | |
| `<desc>` | `description` | |
| `<trkpt>` / `<rtept>` lat+lon | `points` | |
| `<trkpt><time>` | `RoutePoint.ts` | falls back to import time if absent |

### Example GPX with images
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="MappingSolution">
  <wpt lat="31.9975684" lon="35.0042664">
    <name>Cave at Horvat Dasra</name>
    <desc>Part of the burial complex. Water in winter.</desc>
    <ele>273</ele>
    <time>2021-04-10T15:00:41Z</time>
    <extensions>
      <images>photo1.jpg,photo2.jpg</images>
    </extensions>
  </wpt>

  <trk>
    <name>Morning hike</name>
    <desc>Optional route description</desc>
    <trkseg>
      <trkpt lat="31.997" lon="35.004"><time>2024-01-01T07:00:00Z</time></trkpt>
      <trkpt lat="31.998" lon="35.005"><time>2024-01-01T07:05:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>
```

---

## Smart GPS Track Processing

The earlier pipeline snapped each fix to the nearest road independently (greedy snapping). With no
notion of road-network topology or sequence, it structurally produced the artefacts a "perfect"
recording must avoid: square turns, lateral bumps, and jumps onto the wrong road at intersections.

It is replaced by on-device **HMM map-matching** (Newson & Krumm, 2009) solved with the **Viterbi**
algorithm — the whole path is matched against the connected road graph, so the most likely *route*
is chosen rather than the nearest segment per point. Everything runs offline; road shapes still come
from the Overpass API (cached in tiles), and no routing servers are used.

**Per fix (live):**

1. **Kalman filter** — pure-Kotlin 2D filter (state: lat, lng, dLat, dLng). Measurement noise `R`
   is set dynamically from `Location.accuracy`, so shakier fixes are trusted less.
2. **Jump guard** — discards fixes that teleport implausibly far (velocity-adaptive threshold).
3. **Online map-matcher** (`OnlineMapMatcher`) — a streaming Viterbi over a sliding window. Each
   observation scores candidate road projections by an **emission** term (Gaussian on snap distance)
   and a **transition** term (`|great-circle − on-road distance| / β`, with a soft bearing prior).
   A matched point is **committed** only once every surviving path agrees on it, or once it falls a
   few fixes behind the frontier. This few-second lag at the tip is what lets the live line follow
   the correct road *through* an intersection — the decision waits until later fixes reveal where
   you actually went. The latest smoothed position is shown as a provisional "head" so the on-screen
   line still reaches you.

The raw Kalman-smoothed positions are also written to `smoothed.jsonl` — together with each fix's
GPS heading, speed and accuracy — so the trip can be re-matched from clean input later (and to
survive a force-kill).

**On stop (full pass):**

The whole trip is re-matched with the batch `MapMatcher` (full-trajectory Viterbi) over
`smoothed.jsonl`, ensuring every tile the route crossed is loaded first. The persisted heading,
speed and accuracy are fed into the matcher as observation features, so the final pass uses the
same bearing prior and per-fix emission sigma as the live matcher — this is what picks the correct
road and turn at junctions rather than the merely-nearest segment. A light, curvature-/gap-aware
`TrackSmoother` finishing pass removes residual lateral jitter, `points.jsonl` is rewritten
atomically, and the route distance is recomputed from the final matched geometry.

The road graph is **undirected** (one-way restrictions are ignored — a safe simplification for the
short gaps between fixes). Above ~144 km/h map-matching is disabled (GPS is already accurate and
tile loading can't keep up); off-road stretches and long stationary gaps pass through unchanged and
matching restarts cleanly afterwards.

---

## Incomplete Recordings

A route sets `didUserTapStop = false` by default. It is only set to `true` when the user taps **Stop** and completes the finalize screen. On app launch, any route with `didUserTapStop = false` is flagged as incomplete in the library (broken icon). The user can choose to continue or discard it.
