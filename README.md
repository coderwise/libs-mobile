# libs-mobile

Shared Kotlin Multiplatform libraries for Coderwise apps, published to **Maven Central**
under the `com.coderwise.libs` group. Consumers need only `mavenCentral()` — no
credentials.

## Modules

All modules target **Android · iOS (arm64, sim-arm64) · JS · Wasm/JS · Desktop** and
publish under `com.coderwise.libs`. "Latest" is the newest version on Maven Central.

> The Wasm/JS variants are new: only `:map-core`, `:map-engine` and the `:map-view*`
> modules publish them at the versions below. For every other module they ship with its
> next release.

| Module | Coordinates | Latest | Summary |
|---|---|---|---|
| [`:utils`](utils) | `com.coderwise.libs:utils` | `0.5.0` | Cross-platform utilities: file and text sharing, platform system-bar colors, generic `LruCache`. |
| [`:permissions`](permissions) | `com.coderwise.libs:permissions` | `0.6.0` | Runtime permission state (location, camera, Bluetooth, notifications) for Compose Multiplatform, plus `rememberAppSettingsLauncher` for refusals the OS no longer prompts for. |
| [`:database`](database) | `com.coderwise.libs:database` | `0.1.0` | SQLDelight driver factory + Koin DI. |
| [`:location`](location) | `com.coderwise.libs:location` | `0.3.0` | GPS location provider (current location + updates `Flow`), with opt-in background delivery on iOS. |
| [`:settings`](settings) | `com.coderwise.libs:settings` | `0.1.0` | Typed, serializable settings persistence (DataStore-backed). |
| [`:imagepicker`](imagepicker) | `com.coderwise.libs:imagepicker` | `0.1.0` | System image picker (`rememberImagePicker`) with automatic downscaling. |
| [`:filepicker`](filepicker) | `com.coderwise.libs:filepicker` | — | System document picker (`rememberTextFilePicker`) that reads the chosen file as text. Not yet released. |
| [`:logger`](logger) | `com.coderwise.libs:logger` | `0.1.0` | Kermit-backed `AppLogger` facade, plus `enableDeviceVisibleLogging` for iOS debug runs that must be diagnosed off-device. |
| [`:billing`](billing) | `com.coderwise.libs:billing` | `0.2.0` | One-time (non-consumable) purchases behind one API: Play Billing on Android, a Swift StoreKit 2 bridge on iOS, inert where there is no store. |
| [`:map-view`](map-view) | `com.coderwise.libs:map-view` | — | The map: a `MapView` of layered tile slots, the camera and gestures that move it, and overlays placed by coordinate. Plus the tile-grid projection the whole XYZ scheme rests on, for callers that have to cross between a coordinate and a tile. Not yet released; see its [README](map-view/README.md). |
| `:map-view-tiles` | `com.coderwise.libs:map-view-tiles` | — | Loading for the above: `tileState` joins slots to a queue that fills them nearest-the-centre first, and `shown` says what a slot draws until its own tile lands. |
| `:map-view-tiles-raster` | `com.coderwise.libs:map-view-tiles-raster` | — | Image tiles. |
| `:map-view-tiles-vector` | `com.coderwise.libs:map-view-tiles-vector` | — | MVT tiles: a hand-rolled decoder that reads only what you ask for, a redefinable style whose widths ramp with zoom and whose expensive passes can be gated by it, a renderer that paints in a canonical order whatever order the source emits, and labels as text composables. Also what a tile knows besides its picture: `featuresAt` for "what did I just press on", and `waterPath` for anything drawn over the sea. |
| [`:map-core`](map-core) | `com.coderwise.libs:map-core` | `0.1.8` | **Previous generation.** Dependency-free map primitives: slippy-map tile math + `TileId`. |
| [`:map-engine`](map-engine) | `com.coderwise.libs:map-engine` | `0.1.8` | **Previous generation.** Compose tiled-map engine (pannable/zoomable `TiledMap`), built on `:map-core`. |

`:map-core` and `:map-engine` are the map these replace. They are kept as they are —
maintained, no new features — so that an app can move to `:map-view` at its own pace, or
carry both at once behind a flag: the coordinates and packages are different, so nothing
stops them sharing a build.

An app finishing that move should not need `:map-core` either. It held two things a
`:map-view` consumer still wanted — the tile maths, and `TileId` — and the maths is now
`:map-view`'s own (`LatLon.onTileGrid`, `tileGridToLatLon`). `TileId` is not, deliberately:
its packed `Long` exists to be a database key, which is an app's concern rather than a
view's, and `:map-view` addresses a slot on screen with `TileKey` instead. An app that
persists tiles is better off owning that type.

The [`sample/`](sample) directory holds an unpublished demo app — a gallery with one
screen per library, running the same list on all five platforms (Android, iOS, Desktop,
JS, Wasm/JS); see its [README](sample/README.md) for what is in the gallery and how to
run it.

### No module declares an Android permission

A library manifest merges into every consumer, so a permission declared here is a
permission declared for every app that depends on the module — including apps that
never ask the user for it, but must still justify it on their store listing. An app
using only `rememberCameraPermissionState` would ship `ACCESS_FINE_LOCATION`; an app
that registers `locationModule` in DI without resolving a `LocationProvider` would ship
both location permissions.

So `:permissions` and `:location` declare none. **The consuming app declares what it
actually uses** — the KDoc on each `remember*PermissionState` and on `LocationProvider`
names the Android permission and any Info.plist key it needs.

Migration: `:permissions` before `0.4.0` and `:location` before `0.2.0` declared
`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. Apps that relied on that merge must
add them to their own manifest when upgrading — without the declaration Android denies
the request without prompting, and `LocationProvider` returns
`Result.failure(SecurityException)`.

### The two web targets are not one target

Every module builds for both `js` and `wasmJs`, but Kotlin/Wasm has no `dynamic`, and the
DOM bindings it does have are typed differently (`JsAny`, `JsString`, `JsArray` where the
JS target has `dynamic`). So each module's web half is split three ways:

- `webMain` — everything that is not JS interop, written once: the Koin modules, the
  Compose no-ops, `localStorage`, the states a browser has no separate concept of.
- `jsMain` — the `dynamic` implementations.
- `wasmJsMain` — the same behaviour reached through `js("…")` functions that take Kotlin
  callbacks and pass primitives, so nothing needs a typed binding for a JS result object
  (see `:permissions` `PermissionQuery.wasmJs.kt` and `:location` `WasmJsLocationProvider`).

`webMain` comes from the default hierarchy template — it does not need declaring, only
using.

### `:billing` needs a Swift half on iOS

StoreKit 2 is Swift-only — `Product` and `Transaction` are Swift types built on Swift
concurrency, which Kotlin/Native's Objective-C interop cannot see — so the module ships
no iOS store logic. It exposes a `StoreKitBridge` protocol that the **consuming iOS app
implements in Swift** and hands over with `installStoreKitBridge(bridge)` before anything
resolves `Billing`.

An app that never calls it still runs: `Billing.isAvailable` reports false and every call
is inert, which is what keeps the simulator — where there is nothing to buy — working
without one. Android needs no such step; `androidContext()` is the only thing Play
Billing takes from the app.

## Publishing

Each module is versioned and released **independently**, via the
[vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin (Central Portal upload, GPG signing, sources/javadoc jars).

**The release tag is the single source of truth for the version.** Tag a module
release as `<module>-v<version>`; the [`publish`](.github/workflows/publish.yml)
workflow parses it, passes `-PlibVersion`, and publishes **only that module**:

```bash
git tag utils-v0.4.0    && git push origin utils-v0.4.0     # → com.coderwise.libs:utils:0.4.0
git tag map-core-v0.1.8 && git push origin map-core-v0.1.8  # → com.coderwise.libs:map-core:0.1.8
```

**The four `:map-view*` modules are the exception: release them in lockstep**, at one
version, `:map-view` first. `:map-view-tiles` records the version it was built against in
its POM, so a mixed release would have it asking for a `:map-view` that does not exist:

```bash
for m in map-view map-view-tiles map-view-tiles-raster map-view-tiles-vector; do
  git tag $m-v0.1.0 && git push origin $m-v0.1.0
done
```

> **Note:** the publish workflow runs on a **macOS** runner (required for the iOS
> Kotlin/Native targets), which bills GitHub Actions minutes at **10×**. Validate
> locally with `publishToMavenLocal` first; publish deliberately.

The same workflow also accepts a manual `workflow_dispatch` (module + version inputs).
It needs these repo secrets (same values as the other coderwise repos —
`com.coderwise` namespace is already verified): `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, `GPG_KEY_CONTENTS`.

Local, keyless (publishes as `0.0.0-LOCAL` unless `-PlibVersion` is given):

```bash
./gradlew :utils:publishToMavenLocal
```

### Adding a new module

Drop it in as a top-level folder with a `build.gradle.kts` (settings.gradle.kts
auto-includes it). Mirror `:utils`: apply the vanniktech plugin, read the version
from `libVersion` (default `0.0.0-LOCAL`), set `coordinates("com.coderwise.libs", "<name>", …)`.
Release it with a `<name>-v<version>` tag — no workflow changes needed.
