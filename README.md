# libs-mobile

Shared Kotlin Multiplatform libraries for Coderwise apps, published to **Maven Central**
under the `com.coderwise.libs` group. Consumers need only `mavenCentral()` — no
credentials.

## Modules

All modules target **Android · iOS (arm64, sim-arm64) · JS · Desktop** and publish
under `com.coderwise.libs`. "Latest" is the newest version on Maven Central; modules
without one are consumed via `mavenLocal` until first released.

| Module | Coordinates | Latest | Summary |
|---|---|---|---|
| [`:utils`](utils) | `com.coderwise.libs:utils` | `0.4.0` | Cross-platform utilities: file sharing, platform system-bar colors, generic `LruCache`. |
| [`:permissions`](permissions) | `com.coderwise.libs:permissions` | `0.1.0` | Runtime permission state (location) for Compose Multiplatform. |
| [`:database`](database) | `com.coderwise.libs:database` | — | SQLDelight driver factory + Koin DI. |
| [`:location`](location) | `com.coderwise.libs:location` | — | GPS location provider (current location + updates `Flow`). |
| [`:settings`](settings) | `com.coderwise.libs:settings` | — | Typed, serializable settings persistence (DataStore-backed). |
| [`:map-core`](map-core) | `com.coderwise.libs:map-core` | — | Dependency-free map primitives: slippy-map tile math + `TileId`. |
| [`:map-engine`](map-engine) | `com.coderwise.libs:map-engine` | — | Compose tiled-map engine (pannable/zoomable `TiledMap`), built on `:map-core`. |

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
to share infrastructure across the `*.mobile` apps.
