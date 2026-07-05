# Map sample app

A small demo of `:map-core` + `:map-engine` that runs on **every supported
platform**: Android, iOS, Desktop (JVM), JS, and Wasm/JS. Tiles are generated
locally (a labelled checkerboard), so the sample needs no network access, image
loading, or API keys — the whole app lives in `common/src/commonMain`.

These modules are **not published**; they exist to exercise the libraries and
serve as copy-paste integration reference.

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
(the `.xcodeproj` is not checked in):

```sh
cd sample/ios
brew install xcodegen   # once
xcodegen generate
open MapSample.xcodeproj
```

Building the app in Xcode compiles the `:sample:common` framework via a
pre-build Gradle step (`embedAndSignAppleFrameworkForXcode`).
