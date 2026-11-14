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
| Vector map | `:map-view-tiles-vector` | The same `MapView` and queue with a slot that draws decoded geometry instead of a bitmap: tap the map and the tile says what is under your finger, and the water toggle shows the coastline a tile carries. Wires its slots with `rememberTileState`, where the raster example wires the same two by hand. On live OpenFreeMap vector tiles. |
| Map view | `:map-view` | Layered tile slots in one `MapView`, a `TileQueue` filling the state, and a magnified ancestor standing in until a tile lands — plus routes, search-result pins and a tap that drops one. On live OpenStreetMap tiles, fetched by the example with Ktor: the engine fetches nothing itself. |
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
happens. The planet build stops at zoom 14, which is why the vector example's tile state
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

## The icon

A 2x2 grid of rounded tiles on the baseline Material purple the sample's theme already uses —
the gallery, with one tile picked out in amber. Every icon is generated from that one
description by [`icon/generate.py`](icon/generate.py) (standard library, plus `rsvg-convert`,
`iconutil` and `sips`), and the outputs are committed, so it only needs to run when the mark
changes:

| Platform | Output |
|---|---|
| Android | adaptive icon (vector foreground + monochrome layer for themed icons) in `android/src/main/res` |
| iOS | 1024px `AppIcon` in `ios/Sources/Assets.xcassets` |
| Desktop | `desktop/src/main/resources/icon.png` for the window and Dock; `desktop/icons/` for the packaged `.icns`, `.ico` and `.png` |
| Web | `favicon.svg` + `favicon.png` in both web targets' resources |

## Adding an example

1. Write the demo as an `@Composable` in `common/src/commonMain`.
2. Add an `Example(...)` for it to `mapExamples()` or `libraryExamples()`. The gallery
   picks it up from there — nothing else to register.

## Code to copy

Two things used to be published modules and are now the sample's own code, because they are app code
rather than library code. Copy what you want; it is Apache-2.0, and each file is self-contained.

- **`sample/cache`** (was `:map-view-cache`, last published as 0.3.0): a cache for tile bytes that wraps the
  function a tile queue is given. `TileStore`, `MemoryTileStore`, `FileTileStore` (okio), `cached` with its four
  `CacheMode`s, and `tileUrl`. It needs `:map-view` for `TileKey` and okio, and has its tests beside it. The
  function you pass to `cached` does the fetching: client, headers, retries and the tile policy are yours.
- **`sample/controls`** (was `:map-view-controls`, last published as 0.3.0): `ZoomButtons`,
  `ScaleBar`, `NorthButton`, `CompassTape` and `Attribution` take what they show and are told what a press
  does, so none of them needs a `MapCameraState`; `CourseUp` is the one that turns it.
