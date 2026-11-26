# :libs:desktop

The desktop `main`, done once: Koin start-up, data directory, macOS menu-bar name and appearance, dock icon,
About window, and remembered window size/position. Published to **Maven Central**.

**Targets:** Desktop (JVM)

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
        koinModules = { dataDir: File ->
            listOf(
                appModule,
                // Wire the other libs' directories here; this lib depends on none of them.
                module { single<Path>(DATA_DIR_QUALIFIER) { Path(dataDir.path) } },
                module { single<Path>(SETTINGS_DIR_QUALIFIER) { Path(dataDir.path) } },
            )
        },
        theme = { dark, content -> RoomsOnTheme(darkTheme = dark, content = content) },
    ),
) { AppNavigation() }
}
```

**Call `prepareMacOs(appName)` first in `main`.** `desktopApp` sets the same properties, but under Compose Hot Reload
the AWT toolkit is already running by the time `desktopApp` loads, so the menu bar would show `MainKt`. `prepareMacOs`
touches only system properties, so it is early enough.

- `unifiedTitleBar = true` draws the content under a transparent macOS title bar, leaving only the window buttons. Leave `unifiedTitleBarInset` free at the start of the app's top bar (0 on other platforms).
- The data directory is `~/<dataDirName>`; the lib creates no Koin bindings for it, so other libs' directories are registered by the app (above).
- The icon is read from `/icon.png` on the classpath (`iconResource` to change it).
- Window state is kept in `window.json` in the data directory (written by the lib itself); a saved position that is off every screen is ignored. It is plain file IO, with no dependency on the `settings` lib.

## Coordinates

```kotlin
implementation("com.coderwise.libs:desktop:<version>")
```
