# Sample app

A gallery of the libraries in this repo: one screen per module, exercised through its
published API, running on **every supported platform** — Android, iOS, Desktop (JVM), JS,
and Wasm/JS.

These modules are **not published**; they exist to exercise the libraries and serve as
copy-paste integration reference. The shell around the examples is deliberately plain —
a list, a detail screen, one back affordance — with no navigation library, no DI
container and no resources, so the only thing to understand when copying an example is
the library it demonstrates.

## What is in the gallery

| Example | Module | What it shows |
|---|---|---|
| Vector map | `:map-view-tiles-vector` | The same `MapView` and queue with a slot that draws decoded geometry instead of a bitmap: tap the map and the tile says what is under your finger, and the water toggle shows the coastline a tile carries. On live OpenFreeMap vector tiles. |
| Map view | `:map-view` | Layered tile slots in one `MapView`, a `TileQueue` filling the state, and a magnified ancestor standing in until a tile lands — plus routes, search-result pins and a tap that drops one. On live OpenStreetMap tiles, fetched by the example with Ktor: the engine fetches nothing itself. |
| Tiled map (previous) | `:map-engine` | Pan/zoom/rotate, markers anchored to coordinates, and `animateLocationTo` driven from the app's own UI. Tiles are a locally generated checkerboard — no network, tile server or API key. |
| Tile math | `:map-core` | `MapMath` conversions both ways, and the packed `TileId` that keys every cache. No Compose involved. |
| Text file picker | `:filepicker` | `rememberTextFilePicker`, with the picked file's contents shown. |
| Image picker | `:imagepicker` | `rememberImagePicker`, with the downscaled bytes decoded and drawn. |
| Permissions | `:permissions` | Camera and notification status, and the refusal the OS stops prompting for — the case `rememberAppSettingsLauncher` exists for. |
| Share text | `:utils` | `rememberShareTextLauncher`: share sheet on Android and iOS, clipboard elsewhere. |
| Logging | `:logger` | `AppLogger` at each level, and where the lines come out per platform. |

Every module builds for every target, so the list above is the list on all five platforms —
what differs between them is what each platform does with the call, which is what the note
at the bottom of each example screen is for.

The two map-view examples are the only things here that talk to the network. The raster one
fetches OpenStreetMap tiles with Ktor (one engine per platform, in `:sample:common`) and
renders the `© OpenStreetMap contributors` credit the tile policy requires; the vector one
fetches OpenFreeMap's planet build, which serves OpenStreetMap data and asks for both to be
credited. Both are demos panned by hand, which is well inside those policies; anything
heavier wants your own tile server.

OpenFreeMap's planet path carries the date of the build it came from and the build is
replaced periodically — a stale one answers `200` with an empty body, which reads as a map
that never loads. Read a current path from <https://tiles.openfreemap.org/planet> when that
happens. The planet build stops at zoom 14, which is why the vector example's `MapState`
does too: past it the grid lays the deepest level out bigger rather than asking for tiles
that do not exist.

The Android app declares `INTERNET` for it, and `CAMERA` and `POST_NOTIFICATIONS`, and the iOS app declares
`NSCameraUsageDescription`, for the permissions example — the libraries declare no
permission of their own (see the root [README](../README.md#no-module-declares-an-android-permission)).

| Module | Role |
|---|---|
| `:sample:common` | Shared Compose UI (`SampleApp()`), plus the iOS framework + `MainViewController()` entry point |
| `:sample:android` | Android app (`com.coderwise.libs.sample`) |
| `:sample:desktop` | Desktop (JVM) app |
| `:sample:web` | Browser app, built twice: Kotlin/JS and Kotlin/Wasm |
| `ios/` | XcodeGen project hosting the shared framework |

## Running

```sh
# Android (device/emulator attached)
./gradlew :sample:android:installDebug

# Desktop
./gradlew :sample:desktop:run

# Browser — Kotlin/JS
./gradlew :sample:web:jsBrowserDevelopmentRun

# Browser — Kotlin/Wasm (needs a browser with wasm-gc; any current Chrome/Firefox/Safari)
./gradlew :sample:web:wasmJsBrowserDevelopmentRun
```

### iOS

The Xcode project is generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(the `.xcodeproj` is not checked in). It is still named `MapSample`, from when the map
was all there was:

```sh
cd sample/ios
brew install xcodegen   # once
xcodegen generate
open MapSample.xcodeproj
```

Building the app in Xcode compiles the `:sample:common` framework via a
pre-build Gradle step (`embedAndSignAppleFrameworkForXcode`).

## Adding an example

1. Write the demo as an `@Composable` in `common/src/commonMain`.
2. Add an `Example(...)` for it to `mapExamples()` or `libraryExamples()`. The gallery
   picks it up from there — nothing else to register.
