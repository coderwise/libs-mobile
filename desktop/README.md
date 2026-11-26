# :libs:desktop

The desktop `main`, done once: Koin start-up, data directory, macOS menu-bar name and appearance, dock icon,
About window, and remembered window size/position. Published to **Maven Central**.

**Targets:** Desktop (JVM), macOS (native, Arm64; experimental Compose target)

## Usage

```kotlin
fun main() {
    prepareMacOs("RoomsOn") // first line, see below
    desktopApp(
    DesktopAppConfig(
        appName = "RoomsOn",
        dataDirName = ".roomsOn",
        appVersion = appVersion,
        about = AboutInfo(description = "…", copyright = "…"),
        koinModules = { dataDir: Path ->
            listOf(
                appModule,
                // Wire the other libs' directories here; this lib depends on none of them.
                module { single<Path>(DATA_DIR_QUALIFIER) { dataDir } },
                module { single<Path>(SETTINGS_DIR_QUALIFIER) { dataDir } },
            )
        },
    ),
) { dark -> RoomsOnTheme(darkTheme = dark) { AppNavigation() } }
}
```

**Call `prepareMacOs(appName)` first in `main`.** `desktopApp` sets the same properties, but under Compose Hot Reload
the AWT toolkit is already running by the time `desktopApp` loads, so the menu bar would show `MainKt`. `prepareMacOs`
touches only system properties, so it is early enough.

- `unifiedTitleBar = true` draws the content under a transparent macOS title bar, leaving only the window buttons. Leave `unifiedTitleBarInset` free at the start of the app's top bar (0 on other platforms).
- `content` gets the system's dark setting and applies the app's own theme. The About window uses default Material colors unless the app supplies `aboutContent`, which draws itself.
- The data directory is `~/<dataDirName>`; the lib creates no Koin bindings for it, so other libs' directories are registered by the app (above).
- The icon is read from `/icon.png` on the classpath (`iconResource` to change it). On native macOS it is a resource in the app bundle, or else a file path.
- `about = null` drops the app's own About window. On native macOS the menu then has no About item; on the JVM macOS cannot hide it, so it shows the system's default About panel.
- Window state is kept in `window.json` in the data directory (written by the lib itself); a saved position that is off every screen is ignored. It is plain file IO, with no dependency on the `settings` lib.

## Coordinates

```kotlin
implementation("com.coderwise.libs:desktop:<version>")
```

## Native macOS

Same `desktopApp` call; the shared `DesktopAppConfig` is in `commonMain`. On native macOS `prepareMacOs` does nothing, the About window has no icon, `aboutWindowSize` is ignored, and the menu has About and Quit only. Window size and position are kept in the same `window.json`.

## Migrating from 0.1.x

`theme` is gone: apply your theme in `content`, which now takes the dark flag (`{ dark -> MyTheme(dark) { ... } }`). `aboutContent` also gets the dark flag and draws its own theme.

`DesktopAppConfig.koinModules` and `dataDir` now use `kotlinx.io.files.Path` instead of `java.io.File`: `{ dataDir: File -> ... Path(dataDir.path) }` becomes `{ dataDir: Path -> ... dataDir }`.
