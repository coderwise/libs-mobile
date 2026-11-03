# :map-view-geojson

GeoJSON drawn as an overlay on `:map-view`: points, lines and polygons with holes, multi-geometries,
styled per layer, gated by zoom, and tappable. Compose only — no Material, no resources, no JSON
library, no networking.

```kotlin
val coverage = remember(json) { parseGeoJson(json) }       // fetching the text is yours

MapView(camera) {
    layer(ground) { key -> ground.shown(key)?.let { VectorSlot(it.content, it.src, key.z) } }
    overlay {
        GeoJson(coverage, style = GeoJsonStyle(fill = Color(0x330066FF), stroke = Color.Blue))
        GeoJson(trails, minZoom = 12f, onFeatureClick = { feature -> select(feature) })
    }
}
```

One `GeoJson` is one layer: one style, one `minZoom`, one click listener. A map that wants two looks
has two, and they are drawn in the order they are declared.

- **The model is this module's own** — `FeatureCollection`, `Feature` and `Geometry`, with the seven
  geometries nested in it. Coordinates are `LatLon`, latitude first, not GeoJSON's order. A feature's
  `properties` are plain values (`String`, `Double`, `Boolean`, `null`, `List`, `Map`) for the app to
  read its own fields from. There is no shared GeoJSON library to pull in; an app that has one writes
  an adapter, a few lines for each geometry.
- **`parseGeoJson(json)`** reads a FeatureCollection, a Feature or a bare geometry, and throws
  `IllegalArgumentException` — with the offset, where it can say — for text that is not GeoJSON. A
  feature with a `null` geometry is allowed and draws nothing. Altitude, `bbox` and foreign members
  are dropped.
- **Projected once.** `GeoJson` turns the data into world coordinates when it first sees it, and
  keeps them for as long as the data is the same, so a frame is only the camera's affine. The map pans
  and pinches without recomposing it.
- **Holes and multi-geometries.** Every ring of a polygon goes into one path that is filled by the
  even-odd rule, so a hole is a hole; a multi-polygon is its polygons the same way. Each *feature* has
  a path of its own, so two features that overlap are both filled where they do.
- **The antimeridian** needs nothing of its own: every shape is traced the short way round, so a
  polygon from 179° to −179° is one polygon, and a track that crosses carries on.
- **Order within a layer** is polygons, then lines, then points, so a point is never lost under the
  shape it is on. A point is a dot of `pointRadius`.
- **`minZoom`** gates both drawing and tapping: under it a layer draws nothing and cannot be tapped,
  and it comes back as the camera zooms in, without recomposition.

## Taps

`onFeatureClick` gets the feature nearest the tap, and takes part in the same arbitration as
`Polyline` and any other shape built on `Modifier.mapTap`:

- A touch **inside a polygon** is 0 away from it — holes respected, so a touch in a hole is the
  distance to the hole's edge. Otherwise it is the distance to the nearest part of a line, an outline
  or a point, and it counts if that is within `touchWidth / 2`.
- **The nearest shape wins**, not the top one: a polygon you are inside beats a line drawn over it that
  you are beside. Where two are equally near — inside both — the one drawn later wins within a layer;
  across layers a tie is the map's to break.
- A tap it misses goes on to whatever lies under it, the map's own `onTap` included, and a drag that
  starts on a shape still pans the map.

The test is done in the world's coordinates, not the screen's, so a turned or zoomed map needs
nothing special and no vertex is projected for a tap.

## Without a composition

```kotlin
DrawScope.drawGeoJson(data, style, projection, minZoom = 0f)
```

draws the same thing through a `GeoJsonProjection` — a `zoom` and a `trace(path, world)`, the part of
`MapOverlayScope` a map not on screen can have too. It is what lets a static image draw a track
without a composition. It projects all of `data` every call, which one image does not mind; for a
live map use `GeoJson`.

## Not here

Fetching, caching or watching a file: the text is the app's, through `:map-view-cache` if it likes.
Styling by a feature's own properties — a layer has one style; split the data for two. A ring wider
than half the world, or one that winds round a pole, which GeoJSON itself asks to be cut at the
antimeridian.
