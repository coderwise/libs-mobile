# libs-mobile

Shared Kotlin Multiplatform libraries for Coderwise apps, published to **Maven Central**
under the `com.coderwise.libs` group. Consumers need only `mavenCentral()` — no
credentials.

## Modules

All modules target **Android · iOS (arm64, sim-arm64) · JS · Desktop** (the map
modules additionally target **Wasm/JS**) and publish
under `com.coderwise.libs`. "Latest" is the newest version on Maven Central.

| Module | Coordinates | Latest | Summary |
|---|---|---|---|
| [`:utils`](utils) | `com.coderwise.libs:utils` | `0.4.0` | Cross-platform utilities: file sharing, platform system-bar colors, generic `LruCache`. |
| [`:permissions`](permissions) | `com.coderwise.libs:permissions` | `0.4.0` | Runtime permission state (location, camera, Bluetooth, notifications) for Compose Multiplatform, plus `rememberAppSettingsLauncher` for refusals the OS no longer prompts for. |
| [`:database`](database) | `com.coderwise.libs:database` | `0.1.0` | SQLDelight driver factory + Koin DI. |
| [`:location`](location) | `com.coderwise.libs:location` | `0.2.0` | GPS location provider (current location + updates `Flow`). |
| [`:settings`](settings) | `com.coderwise.libs:settings` | `0.1.0` | Typed, serializable settings persistence (DataStore-backed). |
| [`:imagepicker`](imagepicker) | `com.coderwise.libs:imagepicker` | `0.1.0` | System image picker (`rememberImagePicker`) with automatic downscaling. |
| [`:map-core`](map-core) | `com.coderwise.libs:map-core` | `0.1.6` | Dependency-free map primitives: slippy-map tile math + `TileId`. |
| [`:map-engine`](map-engine) | `com.coderwise.libs:map-engine` | `0.1.6` | Compose tiled-map engine (pannable/zoomable `TiledMap`), built on `:map-core`. |

The [`sample/`](sample) directory holds an unpublished demo app for the map
libraries that runs on all five platforms (Android, iOS, Desktop, JS, Wasm/JS) —
see its [README](sample/README.md) for run commands.

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

## Publishing

Each module is versioned and released **independently**, via the
[vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin (Central Portal upload, GPG signing, sources/javadoc jars).

**The release tag is the single source of truth for the version.** Tag a module
release as `<module>-v<version>`; the [`publish`](.github/workflows/publish.yml)
workflow parses it, passes `-PlibVersion`, and publishes **only that module**:

```bash
git tag utils-v0.4.0    && git push origin utils-v0.4.0     # → com.coderwise.libs:utils:0.4.0
git tag map-core-v0.1.6 && git push origin map-core-v0.1.6  # → com.coderwise.libs:map-core:0.1.6
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

## History

`:utils` originated in `coderwise/maps-mobile` (published through `0.2.0`) and was
extracted here; `com.coderwise.libs:utils` coordinates are unchanged, so consumers
required no edits beyond the version bump. The `:database`, `:settings`,
`:location`, `:permissions`, `:map-core`, and `:map-engine` modules were added here
to share infrastructure across the `*.mobile` apps. `:imagepicker` was extracted from
`coderwise/cards-mobile` (where it lived as `:libs:imagepicker`); its package moved from
`com.coderwise.cards.libs.imagepicker` to `com.coderwise.libs.imagepicker`.
