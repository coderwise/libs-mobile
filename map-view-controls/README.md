# :map-view-controls

The parts of a map screen that are not the map. Compose only — no Material, no resources — so they
look like what you give them and sit over `MapView` without bringing a theme along.

```kotlin
Box(Modifier.mapGestures(camera, rotatable = !courseUp)) {
    MapView(camera) { /* layers, overlays */ }

    ZoomButtons(camera, Modifier.align(Alignment.CenterEnd).padding(12.dp))
    NorthButton(camera, Modifier.align(Alignment.TopEnd).padding(12.dp))      // only while turned
    ScaleBar(camera, Modifier.align(Alignment.BottomStart).padding(12.dp), units = ScaleUnits.Metric)
    Attribution("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd))
    CourseUp(camera, heading = gpsHeading, enabled = courseUp)                 // draws nothing
}
```

Each control takes the `MapCameraState` and nothing else, and reads or moves it through its public
surface — the same one an app has. None of them know about tiles, sources or networking.

- **`ZoomButtons`** animate with `flyTo`; a second press carries on from where the first was headed.
  Pass the source's own `zoomRange` and a button turns itself off where its tiles stop.
- **`ScaleBar`** draws a bar a round number of metres (or feet and miles) long, from
  `camera.metersPerDp`. True at the middle of the map: Mercator stretches towards the poles.
- **`NorthButton`** and **`CompassTape`** show which way the top of the map faces. `turnTo(0f)`
  — the short way round, animated — is what puts it back.
- **`CourseUp`** keeps `camera.bearing` on a heading. It waits while the user has hold of the map,
  and ignores a heading within `deadband` degrees of where the map already points, because a GPS
  heading wobbles that much.

## Not here

A compass *sensor*. `CourseUp` takes a heading and does not care where it came from — `:location`
for a GPS course, a platform heading API for a compass.
