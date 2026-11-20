# Coderwise Libs

Pragmatic Kotlin Multiplatform libraries: the pieces we kept needing across Android, iOS,
Desktop and the web, packaged so we — and you — can stop rewriting them.

GPS, a map, in-app purchases, the camera, the photo picker, permissions, storage: small,
independent libraries you pick one at a time. Open source under Apache 2.0 and published
to **Maven Central** under `com.coderwise.libs`: add `mavenCentral()` and a dependency,
no credentials needed.

> **Battle tested.** These grew out of real Coderwise apps and run in them today. They are
> built to solve the problem in front of us rather than every case, and versions are `0.x`,
> so APIs can still change between releases.

```kotlin
// build.gradle.kts, in commonMain
implementation("com.coderwise.libs:location:0.3.2")
implementation("com.coderwise.libs:map-view:0.4.0")
```

## Why use them

- **One codebase, five targets.** Every module builds for Android, iOS (device and
  simulator), Desktop, JS and Wasm/JS, with a Compose Multiplatform API.
- **Pick only what you need.** Each library is versioned and released on its own, so an
  app takes a camera without taking a map.
- **Honest about permissions.** No module adds an Android permission to your manifest
  behind your back. Your app declares what it actually uses, so your store listing only
  has to justify what you really ask users for.
- **Degrades gracefully.** Where a platform has no camera or no store, the API is still
  there and reports it is unavailable, so shared code needs no platform checks.
- **A live gallery.** The [`sample/`](sample) app shows every library running on all five
  platforms; see its [README](sample/README.md).

## What is in the box

### Maps

A tile map built from small parts, so you take the pieces you need.

| Library | Version | What it gives you |
|---|---|---|
| [`map-view`](map/map-view) | `0.4.0` | The map itself: layered tiles, camera and gestures, and overlays placed by coordinate. See its [README](map/map-view/README.md). |
| `map-view-tiles` | `0.4.0` | Tile loading that fills the screen from the centre outward, and keeps something on screen while a tile arrives. |
| `map-view-tiles-raster` | `0.4.0` | Image tiles. |
| `map-view-tiles-vector` | `0.4.0` | Vector (MVT) tiles with a restyleable look, widths that scale with zoom, labels, and "what did I just tap on". |
| [`map-view-geojson`](map/map-view-geojson) | `0.4.0` | GeoJSON on the map: points, lines and polygons, styled per layer, tappable. |
| [`map-view-core`](map/map-view-core) | `0.4.0` | The tile address type, with no UI dependency — for tile caches and other code that handles tiles without drawing them. |

### Device features

| Library | Version | What it gives you |
|---|---|---|
| [`location`](location) | `0.3.2` | Current position and a live stream of updates, with opt-in background delivery on iOS. |
| [`camera`](camera) | `0.1.0` | A live viewfinder and still capture, back or front lens: CameraX on Android, AVFoundation on iOS. |
| [`imagepicker`](imagepicker) | `0.3.0` | The system photo picker: one picture or several, with the date each was taken, scaled and turned upright, or the original file. |
| [`filepicker`](filepicker) | `0.2.0` | The system document picker, reading the chosen file as text. |
| [`permissions`](permissions) | `0.6.0` | Runtime permission state for location, camera, Bluetooth and notifications, plus a shortcut to app settings for refusals the OS will not prompt for again. |
| [`locale`](locale) | `0.2.0` | The device's region and country, and the measurement system they imply — for sensible first-launch defaults such as units. |

### App foundations

| Library | Version | What it gives you |
|---|---|---|
| [`billing`](billing) | `0.2.0` | One-time purchases behind one API: Play Billing on Android, StoreKit 2 on iOS, inert where there is no store. |
| [`settings`](settings) | `0.2.1` | Typed, serializable settings persistence. |
| [`database`](database) | `0.3.0` | A SQLDelight driver factory with Koin wiring. |
| [`logger`](logger) | `0.3.0` | A logging facade, with recent log lines you can hand to a share sheet and logging that is readable off-device on iOS. |
| [`utils`](utils) | `0.6.1` | File and text sharing, system-bar colors and a generic LRU cache. |

### App architecture

| Library | Version | What it gives you |
|---|---|---|
| [`navigation`](navigation) | unreleased | Navigation 3 back stacks for apps with several roots (tabs, auth vs. main): one child stack per root, typed navigation events, and back stacks that survive process death. |
| [`navigation-ui`](navigation-ui) | unreleased | Navigation 3 scene strategies: an entry shown as a bottom sheet drawn by the app's own sheet container, or drawn over the screen beneath it. |
| [`arch`](arch) | unreleased | Use case base classes (suspend, Flow and synchronous) that run on a dispatcher you choose. |
| [`uistate`](uistate) | unreleased | A shared loading and error contract for ViewModel UI states, and the `StateFlow` sharing timeout. |

## Contributing

Building, releasing and the design notes behind these libraries are in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
