# Coderwise Libs

Pragmatic Kotlin Multiplatform libraries: the pieces we kept needing across Android, iOS,
Desktop and the web, packaged so we — and you — can stop rewriting them.

GPS, in-app purchases, the camera, the photo picker, permissions, storage: small,
independent libraries you pick one at a time. Open source under Apache 2.0 and published
to **Maven Central** under `com.coderwise.libs`: add `mavenCentral()` and a dependency,
no credentials needed.

> **Battle tested.** These grew out of real Coderwise apps and run in them today. They are
> built to solve the problem in front of us rather than every case, and versions are `0.x`,
> so APIs can still change between releases.

```kotlin
// build.gradle.kts, in commonMain
implementation("com.coderwise.libs:location:0.3.2")
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
| [`desktop`](desktop) | unreleased | The desktop `main` in one call: Koin, data directory, dock icon, About window and remembered window state. |
| [`network`](network) | unreleased | A Ktor `HttpClient` for JSON APIs with the right engine on every platform, an optional base URL, logging that keeps credentials out, and backoff on 429. |
| [`shell`](shell) | unreleased | Host access in common code: run a process with a timeout and cancellation, and read, write and list files. |
| [`ollama`](ollama) | unreleased | A client for the Ollama REST API: model list, chat with tool calling, streaming with reasoning, and context length. |

### App architecture

| Library | Version | What it gives you |
|---|---|---|
| [`navigation`](navigation) | `0.1.0` | Navigation 3 back stacks for apps with several roots (tabs, auth vs. main): one child stack per root, typed navigation events, and back stacks that survive process death. |
| [`navigation-ui`](navigation-ui) | `0.1.0` | Navigation 3 scene strategies: an entry shown as a bottom sheet drawn by the app's own sheet container, or drawn over the screen beneath it. |
| [`arch`](arch) | `0.1.0` | Use case base classes, suspend and `Flow`, that run on a dispatcher you choose. |
| [`uistate`](uistate) | `0.1.0` | A shared loading and error contract for ViewModel UI states, and the `StateFlow` sharing timeout. |
| [`viewmodel`](viewmodel) | unreleased | State-holding ViewModel base classes: `StateViewModel` (one `uiState`, one `onEvent`, atomic `update`, a safe `launch`) and `ModelViewModel` (the same, over a separate model type mapped to the `uiState`). Separate from `arch` so domain modules don't pick up AndroidX lifecycle. |
| [`architecture-rules`](architecture-rules) | unreleased | Konsist-backed architecture test for app projects: ViewModel size and dependencies, platform-free core and feature modules, `StateViewModel` base, onEvent-only content composables, with a baseline for grandfathered violations. JVM only. |

## Looking for the map?

The `map-view` modules moved to [mapview-mobile](https://github.com/coderwise/mapview-mobile). Coordinates (`com.coderwise.libs:map-view*`), module names and release tags are unchanged.

## Contributing

Building, releasing and the design notes behind these libraries are in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
