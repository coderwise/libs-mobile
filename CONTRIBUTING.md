# Working on libs-mobile

Notes for maintainers and contributors. For what the libraries do, see the [README](README.md).

The `:map-view*` modules live together in [`map/`](map). Where a module sits on disk does not
change its name: it is still `:map-view-core`, `com.coderwise.libs:map-view-core`, and released
with a `map-view-core-v<version>` tag.

`:map-view-controls` is deprecated and frozen: copy it from the
[sample](sample/common/src/commonMain/kotlin/com/coderwise/libs/sample/controls) instead of
depending on it.

## Design notes

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
git tag settings-v0.3.0 && git push origin settings-v0.3.0  # → com.coderwise.libs:settings:0.3.0
```

**The `:map-view*` modules are the exception: release them in lockstep**, at one
version, `:map-view-core` and then `:map-view` first. `:map-view-tiles` and the modules after it record the version they
were built against in their POMs, so a mixed release would have one asking for a `:map-view`
that does not exist:

```bash
for m in map-view-core map-view map-view-tiles map-view-tiles-raster map-view-tiles-vector map-view-controls map-view-geojson; do
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
