# map-view

A hand-rolled tiled map for Compose Multiplatform — no map library (no MapLibre, osmdroid, Google
Maps SDK). Four modules, written to find out how much of a map engine you actually have to write,
and kept because the answer turned out to be: not much.

It replaces `:map-engine`, which stays published and unchanged for the apps still on it. The
coordinates and the packages differ, so an app can carry both and choose between them at runtime.

- **`:map-view`** — the component. Which tiles are wanted, and where they go on screen.
- **`:map-view-tiles`** — what a map needs regardless of what a tile holds: a fetch queue and
  the stand-in rule.
- **`:map-view-tiles-raster`** — image tiles. Depends on nothing but Compose.
- **`:map-view-tiles-vector`** — MVT tiles: a decoder, a redefinable style, and labels. Also
  depends on nothing but Compose.

Raster and vector share no code, so they are separate libraries: a map that only shows images
should not carry an MVT decoder, and the other way round. Neither knows what a tile key is, or
that there is a map — a slot is handed a tile and the fraction of it to show.

All four are Compose Multiplatform and compile for Android, iOS, desktop, JS and WasmJs. There is
no platform-specific code in any of them — not one `expect`. Decoding an image was the last
candidate, and Compose Multiplatform 1.12 ships `ByteArray.decodeToImageBitmap()` in common.

The gallery's "Tiled map (experiment)" screen drives them on live OpenStreetMap tiles; the
Android harness they were written against lives outside this repo, in `experiments/MapEngine`,
and is where the numbers below were measured.

## The engine is one thing

`MapView` positions one composable per visible tile. That is all it does.

```kotlin
MapView(camera, Modifier.fillMaxSize()) {
    layer(ground) { key -> /* draw whatever you like for this tile */ }
    overlay { /* and whatever you like at a coordinate */ }
}
```

It is not generic over tile content — it never sees any. It takes a `MapState<*>` per layer,
publishes the window of tiles that layer wants, and calls it with a key.

A map is layers: `layer` can be declared as many times as you like, and every tile of one is placed
before the first tile of the next — ground, then labels, then whatever you put over those. Nothing
is clipped to its tile either: staying inside the box is the layer's business, and a name
deliberately hangs past the edge of the tile that owns it.

**A layer brings its own source**, because a map is usually several pyramids rather than one: a
base map to zoom 19 under weather that stops at its own native level and is magnified from there,
or a hillshade under a street layer. Each is laid out on the level its own source has, each
publishes its own window, and each is asked for tiles for exactly as long as its layer is there —
turn a layer off and its queue goes quiet. The alternative was a `MapView` per source, stacked in
a `Box`, which is what the weather app was doing before this and what a rotating camera would have
made impossible to keep aligned.

### Overlays are the other half

A tile layer draws what a source shipped. An overlay draws what the app knows: a recorded track, a
route, the pins on a set of search results, a circle round the user. The difference is only what
positions it — a key, or a coordinate — so `overlay` takes its turn among the layers and gets the
map's projection as its scope.

```kotlin
overlay {
    Polyline(ride, color = Blue, width = 5.dp)          // a track, stroked in dp
    Pin(Modifier.at(finish, Alignment.BottomCenter))    // any composable, at a coordinate
    Marker(Modifier.at(result.point).clickable { open(result) })
}
```

`Modifier.at(point, anchor)` is the whole of it: the child is measured as it likes, sized in dp so
it is the same on screen at every zoom, and placed so that its [anchor] — the middle of a dot, the
tip of a pin — lands on the coordinate. Everything else about it is ordinary Compose, which is why
there is no marker type, no `onMarkerClick`, and no z-index: it is a composable in a layout.

`project`/`unproject` are there for anything the two do not cover — turning a tap into a `LatLon`,
drawing your own geometry. Anything in an `overlay` that is *not* hung off a coordinate is measured
to the whole map, so a plain `Canvas` inside one draws through the projection over the lot.

`Polyline` is the one convenience, because tracks are big and the naive version is slow: the half
of projecting a point that is a logarithm and a tangent depends only on the point, so it is done
once per track rather than once per frame, and only the affine is left in the draw pass. The line
is stroked in dp and never simplified — clipping and tessellating it is the graphics layer's job.

### Which way is up

`camera.bearing` is what is at the top of the screen, in degrees clockwise from north: 0 is
north-up, 90 puts east up. `rotateTo` sets it — a compass button, a heading-up mode — and two
fingers twist it, with a slop of 7 degrees so that a pinch is not a twist until it is clearly one.
`mapGestures(camera, rotatable = false)` takes the gesture away without taking the bearing away.

Tiles turn; overlays do not. A tile layer is laid out on an unturned plane — big enough that the
turned viewport is full of it — and that whole plane is rotated in one layer, so neighbours keep
their seams. Overlays stay in the viewport's own axes and the bearing goes into `project` instead,
which is what keeps a pin upright while the line beside it follows the map. Nothing needs to
counter-rotate its markers.

### Being tapped is three different questions

```kotlin
Box(Modifier.mapGestures(camera, onTap = { there -> select(null) })) {   // a tap on the map
    MapView(camera) {
        overlay {
            Polyline(trail, color = Blue, onClick = { select(trail) })   // a tap on a line
            Pin(Modifier.at(stop).clickable { select(stop) })            // a tap on a marker
        }
    }
}
```

A marker is a composable with a shape, so Compose hit-tests it the way it hit-tests anything, and
`clickable` is all it needs. A line is not: it is drawn on a canvas the size of the whole map, and
without care it would be the only tappable thing on it. So a tappable line shares its pointer input
with its siblings and claims a tap only within `touchWidth` of the line itself — the geometry test
is a point-to-segment distance over the same projected coordinates it draws.

Where two lines are both within reach, **the nearest wins**, not the top one: every line hears the
touch go down on the initial pass and offers its distance before any of them acts on the main pass.
Declaration order is then only about drawing, which is one less thing to have to know.

`onTap` sits on the gestures modifier, outside everything the map draws, so it hears only what
nothing inside took: a tap on a marker or a line never reaches it. It waits out the double-tap
window first, because until that closes the tap might still be a zoom.

Only the lift is consumed, and only once the gesture is known to be a tap — a drag that starts on a
line still pans the map.

### The state is the interface

```kotlin
class MapState<T>(zoomRange: IntRange = 0..19) {
    var window: TileWindow          // what the view wants; written by the view
    fun slot(key: TileKey): T?      // a tracked read: only this tile's slot recomposes
    fun put(key: TileKey, content: T?)
    fun filled(): Set<TileKey>
    fun forget(key: TileKey)
}
```

`T` is whatever the filler puts there — a decoded `ImageBitmap`, say, or a `VectorTile`.
Loading happens outside the engine entirely: something watches `window` and calls `put`. In
`:map-view-tiles` that something is `TileQueue<T>`, which keeps a want-list, runs a few workers over it
nearest-the-centre first, drops tiles that leave the window before anyone starts them, and evicts
by age while never touching what is on screen. It keeps a second, lower list for the zoom level
either side of the window, so a pinch has something to show at once — never taken while a tile on
screen is outstanding, never on more than half the workers, and not started at all until the map
has held still for a quarter of a second. The engine never sees it: put the queue in a `ViewModel`
and the tiles outlive the composition, so a rotation redraws from memory instead of refetching.

**Decoding is part of loading, not of drawing.** `TileQueue` is generic in what a tile *is*, so
the fetch that feeds it is free to decode on the way in — the state then holds tiles ready to
draw, the queue's capacity is the only cache there is, and a slot is a pure function of the tile
it is given. Nothing in these libraries caches anything of its own.

```kotlin
val state = remember { MapState<VectorTile>(zoomRange = 0..14) }
remember {
    TileQueue(scope, state, capacity = 96) { key ->
        download(key)?.let { withContext(Dispatchers.Default) { decodeVectorTile(key.z, it) } }
    }
}
```

Because each slot reads its own `MutableState`, a tile arriving recomposes that tile and no other.

### What to show while a tile is missing

Not the engine's call. `:map-view-tiles` offers the usual answer as plain, testable logic:

```kotlin
fun <T> MapState<T>.shown(key: TileKey, levels: Int = 5): Shown<T>?
```

The tile itself if it has arrived, else the nearest filled ancestor with the fraction of it that
covers this slot, else null — where a placeholder or a spinner would go instead.

```kotlin
MapView(camera, Modifier.fillMaxSize()) {
    layer(state) { key -> state.shown(key)?.let { VectorSlot(it.content, it.src) } }
}
```

`shown` lives in `:map-view-tiles`, which is why that module knows nothing about either tile format.

`RasterSlot` and `VectorSlot` take the same shape — a decoded tile, the `src` fraction to show,
and a modifier — and do nothing but draw it. Neither is privileged and the engine cannot tell them
apart; a switch in the corner swaps them under a live camera.

## The whole public API

```
:map-view
  LatLon, TileKey, TileWindow
  MapState (window, slot, put, filled, forget)
  MapCameraState (center, zoom, bearing, isInteracting, moveTo, rotateTo), flyTo
  rememberMapCameraState, ZOOM_LIMITS
  MapView(camera, modifier) { layer(state) { key -> … }; overlay { … } }
  MapScope.layer(state) { key -> … }, MapScope.overlay { … }
  MapOverlayScope (project, unproject, Modifier.at(point, anchor))
  MapOverlayScope.Polyline(points, color, modifier, width, touchWidth, onClick)
  Modifier.mapGestures(camera, rotatable, onTap)

:map-view-tiles
  TileQueue(scope, state, workers, capacity) { key -> … }
  MapState<T>.shown(key, levels) -> Shown(key, content, src)

:map-view-tiles-raster
  RasterSlot(tile: ImageBitmap, src, modifier)

:map-view-tiles-vector
  decodeVectorTile(zoom, bytes, style) -> VectorTile
  VectorStyle(land, water, ink, halo, landcover, waterway, road, place, roadName)
  VectorStyle.Road(color, casing, width)
  VectorSlot(tile: VectorTile, src, modifier)
  VectorLabels(tile: VectorTile, src, modifier, bearing)
  decodeMvt(data, keep) -> List<MvtLayer>
```

Tile maths, tile layout, the gesture detector and the fling curve are all `internal`.

## What it does under the hood

- Three numbers are the whole view: normalised Web Mercator x, y and a **fractional** zoom. Only
  the integer part picks a tile level; the fraction scales the tile, so pinch stays continuous
  while the network only sees whole levels.
- **A tile is always 256 dp wide**, so it is `256 · density` physical pixels — the same real size
  on every screen, and text stays as legible as the tile author intended. The world is 256·2^zoom
  dp across and the level to read is `floor(zoom)`. Density is not in that: it is already in the
  world size.
- That is a display size, not an image resolution. An @2x source shipping 512 px images for the
  ground of a 256 dp tile needs nothing said about it — same layout, twice the pixels, sharp
  instead of soft on a 2x screen.
- Nothing in the draw pass blocks on IO, because nothing in the draw pass loads anything. Tile
  rectangles are snapped to whole pixels on both edges, so neighbours never leave a seam.
- **Persistent caching belongs to the fetcher.** The harness fetches with plain
  `HttpURLConnection`, so installing an `HttpResponseCache` in the activity gives it a disk cache
  in one line. None of these libraries does any IO at all.
- **One finger can zoom**: double-tap and hold, then drag — a screen height is four levels, down
  to zoom in. It is detected innermost, so what starts as a second tap and then moves is claimed
  as a zoom before panning sees it, and it is anchored on the first tap rather than on the moving
  finger, so the place being zoomed into stays where it was.
- Fling velocity is measured across the tail of the drag — displacement over elapsed time, from the
  samples taken while the pointer was down. The release is deliberately not one of them: it lands
  whenever the system gets round to it, hundreds of milliseconds late and a few pixels off wherever
  the pointer settled, and a throw measured across it reads as a slow drift in an arbitrary
  direction. Compose's own `VelocityTracker` is not used because it looks back only 100 ms and
  wants several samples inside that window, which a stalled frame does not leave it.
- The decay is `exponentialDecay(frictionMultiplier = 0.45f)`, **not** Android's scroll-fling
  spline: the spline stopped the map dead — a 2250 px/s flick coasted 390 px, about a third of a
  screen. A map should drift when you throw it. Now that flick travels ~1170 px in ~1.9 s, and a
  hard throw (clamped at 3000 dp/s) ~4200 px in ~2.6 s.

## What the numbers said

Measured on the emulator, panning a fixed route at zoom 14, median frame time from `gfxinfo`:

| | median frame |
|---|---|
| vector, before | 550 ms |
| vector, after | 150-250 ms |
| raster, same route | 21 ms |

The one large win was grouping roads by value: `Road` was a plain class, so `groupBy` compared by
identity, every road got a group of its own, and a tile issued **10,205 `drawPath` calls instead of
about 20**. Recording a tile's display list fell from 25 ms to 186 us.

Nothing else moved the frame time, and the runs are noisy enough — the same build measured 250 ms
once and 150 ms twice — that only large effects are worth believing here:

- **512 dp tiles rather than 256.** A screen costs a quarter of the tiles, which cut decode from
  3.2 s of CPU over the benchmark to 0.9 s and quietened the collector. Frame time did not care.
  Not kept: paying for it in the API — every caller declaring a tile size, every reader working out
  which level that fetches — bought nothing the frame time could see. A tile is 256 dp, always.
- **Flat geometry.** Rings are `FloatArray` rather than `List<Offset>`, which boxed every point.
  MVT parsing dropped about 30%, path building 25%.
- **Thinning the geometry.** Dropping a quarter of the points did nothing measurable, so it is not
  in the code. Point count is not the constraint at this scale.
- **An offscreen compositing layer per tile.** Worse: Compose does not cache one between frames, so
  it only adds a buffer.

## Things worth pushing on

- **Rasterise a vector tile once instead of every frame.** The per-frame breakdown says where the
  remaining time goes: 10 ms on the UI thread, 60-90 ms on the render thread. Recording a display
  list costs 186 us and decoding never touches the frame, so what is left is Skia tessellating the
  same paths again for every frame the tile sits on screen — about 30,000 points a tile. Drawing
  each tile into an ImageBitmap once and blitting it afterwards is what the raster source does at
  21 ms a frame. It costs the crispness that vector tiles are for, and about 1 MB a tile of memory,
  so it is a trade rather than a win — and worth checking on real hardware first, since the
  emulator translates GL to Metal and may be flattering the raster path.


- **Tilt** — not implemented. Bearing is (see above); a pitched camera is a different projection
  and a different tile walk, and nothing here needs one.
- **A turned map costs tiles.** The plane laid out is the bounding box of the turned viewport, so
  at 45 degrees it is about twice the area and twice the tiles. That is inherent — those tiles are
  on screen — but it means a map left at 45 degrees is a heavier map.
- **Anything else drawn inside a tile layer turns with it.** `VectorLabels` takes a `bearing` and
  undoes it, but a layer of your own that draws text has to do the same — the layer is one turned
  plane, and it is not told which way it faces.
- **Fling tile churn** — a hard flick still asks for every screenful of ground it races over.
  Priority handles the standing case (what is on screen goes first, and nothing is prefetched
  while the map moves), but a request already in flight is never cancelled, so the tiles of a
  place the flick has left still arrive and still cost their bytes.
- **Overlays are placement, one line style and three taps, nothing more.** No polygons, no
  clustering of pins that land on top of each other, and nothing that culls: a thousand markers
  are a thousand children, measured and placed every time the map moves. Each of those wants a
  real use before it is written — the shapes of them differ too much to guess.
- **A tap on a line is O(points).** Every segment of every tappable line is measured against every
  touch that goes down, which is fine for a few tracks and would not be for a thousand. A bounding
  box per line would settle most of it in four comparisons; nothing here needs it yet.
- **`onTap` is a double-tap timeout late** (~300 ms), because a tap is only a tap once no second
  one arrives. Firing it at once and undoing it on the second tap would feel quicker and is what
  a map that dropped pins would want.
- **A `Polyline` is rebuilt whenever its list changes**, which a track being recorded does on every
  fix. Appending to a path instead of rebuilding it is easy; whether it matters is not measured.
- **Labels collide only within a tile** — a name never covers another name off the same tile, and
  may cover one off the next tile along. Worse for roads than for places: a street crossing a seam
  is named once by each tile it runs through, and those two names are invisible to each other. Real
  collision wants every label on screen in one list, which wants a layer that knows the viewport
  rather than one slot of it — and would let a road spanning four tiles be named once.
- **Road names are straight** — text is turned to the chord of its straightest run, not bent along
  the road, so a name on a curve sits at an angle that only approximates it. Bending it means
  placing each glyph along the line by arc length, which stops being a text composable and goes
  back to being a drawing.
- **The vector style is five layers deep** — land use, water, roads, place names and road names; no
  buildings, no boundaries, and no points of interest.
- **Raster ceiling** — no styling, no dark map, no @2x tiles. This is the argument for vector
  tiles, and the reason to compare against MapLibre.
- **Attribution** — nothing renders it. OSM requires the "© OpenStreetMap contributors" credit on
  screen, so it has to come back before this is shown to anyone outside the spike.

## The vector source

`https://tiles.openfreemap.org/planet/<release>/{z}/{x}/{y}.pbf` — OpenMapTiles schema, free, no
key. The release in the URL is dated; read a current one from `https://tiles.openfreemap.org/planet`
when it goes stale.

Decoding is hand-rolled: MVT is protobuf, but it uses a handful of fields, so a reader for varints
and length-delimited chunks covers it. Geometry is a command stream (move-to, line-to, close-path)
with zigzag-encoded deltas. `MvtTest` runs the decoder over a real tile committed as a fixture,
checking layers, extent, road classes and closed rings.

The style is a value, not a constant. `VectorStyle` holds every colour and size the drawing uses,
with a default for each, so a caller changes what it wants and leaves the rest:

```kotlin
val night = VectorStyle(
    land = Color(0xFF1B1F24),
    water = Color(0xFF16324A),
    ink = Color(0xFFE8E6E1),
    halo = Color(0xC0000000),
    road = { kind -> VectorStyle().road(kind)?.copy(color = Color(0xFF4A5560)) }
)
decodeVectorTile(key.z, bytes, night)
```

The lookups are by the feature's `class` tag — "motorway", "wood", "city" — and null is how a
style says *don't draw this*. It is read **at decode**, not at draw, because that is when features
are grouped into one path per colour; changing the style means decoding the tiles again. That is
the price of a frame being twenty draw calls, and of a slot being a pure function of its tile.

Styling is deliberately thin: paths are built **once at decode**, in tile coordinates, so a frame
is a transform plus about twenty `drawPath` calls no matter how far the tile is magnified — one
path per style, not one per feature. Line widths are applied in dp at draw time, so a road is the
same width on screen however far its tile is stretched, and thins as the map zooms out. Casings are
drawn for every road before any road surface, so junctions read as junctions.

Geometry is flat: a ring is a `FloatArray` of `x0, y0, x1, y1, …` rather than a list of points,
because a list boxes every one of the tens of thousands a tile holds.

### Labels are placed, not drawn

A label is a marker with words. So `VectorLabels` is a `Layout` of ordinary text composables, not a
canvas: it measures each name, centres it on its point, and drops any that would land on a name
already placed. What text can do — fonts, ellipsis, a modifier of its own — comes with it.

It is a layer of its own, over the layer that draws the ground:

```kotlin
MapView(camera, Modifier.fillMaxSize()) {
    layer(state) { key -> state.shown(key)?.let { VectorSlot(it.content, it.src) } }
    layer(state) { key ->
        state.shown(key)?.let { VectorLabels(it.content, it.src, bearing = camera.bearing) }
    }
}
```

Above every tile, because a name sits where its town is, which is as often as not across a seam: a
label laid out inside its own slot is cut off at the edge, or covered by the next tile's ground.

A tile layer turns as one plane, so a label inside it would turn with the ground. Hand it the
`bearing` and a place name takes that back out and stays level, however the map is turned. A road
name does not: it belongs to its road and turns with it, and only swaps end for end — never
mirrored — when that is what keeps it reading left to right.
Each name is placed by the one tile whose box holds its point, so nothing is placed twice, and it
is free to hang past that box. Text is in sp, so a name is the size it is however far its tile is
magnified.

**Road names lie along their road.** Decoding finds, for each name in `transportation_name`, the
longest run of it that never strays more than 17° from the direction it set out in — that run's
middle to sit on, its angle to turn by, and its length. The layout turns the text with
`graphicsLayer`, which rotates it about the point it was placed on, and drops any name longer than
the straight run it was given, so a name never bends and never runs off the end of its road. A road
arrives in pieces, split wherever a tag changes, so the pieces are grouped by name first: one label
per name per tile.

The cost of turning it is a rectangle that no longer matches what the text covers, so collision
uses the turned box — `|w·cos| + |h·sin|` across, the same the other way — rather than the layout's
own.

Free, as far as the frame budget goes: the same pan measured 125–150 ms a frame with the labels and
150 ms without, which is noise next to Skia re-tessellating the roads.

## Tile policy

None of these libraries touches the network — the app hands `TileQueue` a `suspend (TileKey) -> T?`
and that is the whole contract. Which server you point at, and living within its terms, is the
caller's business; most require an identifying User-Agent or an API key and forbid bulk
downloading. The harness points at `tile.openstreetmap.org` under
[OSM's tile usage policy](https://operations.osmfoundation.org/policies/tiles/), and logs every
fetch: `adb logcat -s TileHttp` prints status, time, size and whether the bytes came from the
disk cache.

## Layout

```
:map-view            commonMain only
  Mercator.kt        LatLon and the Web Mercator maths
  MapState.kt        TileKey, TileWindow, the slots and the window the view wants
  MapCameraState.kt  the camera and its saver
  MapView.kt         the component: which tiles, and where they go
  MapGestures.kt     Modifier.mapGestures: pan, pinch, twist, double-tap, one-finger zoom, fling

:map-view-tiles             commonMain only
  TileQueue.kt       the want-list, the level either side, its workers and its eviction
  StandIn.kt         MapState<T>.shown: the tile, an ancestor, or nothing

:map-view-tiles-raster      commonMain only
  RasterSlot.kt      blit the src fraction of an ImageBitmap

:map-view-tiles-vector      commonMain only
  VectorSlot.kt      decodeVectorTile, and drawing land use, water and roads
  VectorLabels.kt    place names, as text composables, over every tile
  mvt/Mvt.kt         a hand-rolled MVT decoder (~180 lines, no protobuf library)

```

## Run

```bash
./gradlew :map-view:desktopTest :map-view-tiles:desktopTest
./gradlew :map-view:iosSimulatorArm64Test :map-view-tiles:iosSimulatorArm64Test
./gradlew :map-view:testAndroidHostTest :map-view-tiles:testAndroidHostTest \
          :map-view-tiles-vector:testAndroidHostTest

# that the libraries really are multiplatform
./gradlew :map-view-tiles-vector:compileKotlinJs :map-view-tiles-vector:compileKotlinWasmJs \
          :map-view-tiles-raster:compileKotlinIosArm64
```
