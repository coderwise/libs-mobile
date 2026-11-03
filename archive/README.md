# archive

Modules that are no longer part of the build. They are kept here, as they were when they stopped,
so that the source of what is still on Maven Central can be read. Nothing under this directory is
built, tested or released: `settings.gradle.kts` only includes top-level directories.

| Module | Last release | What it was |
|---|---|---|
| [`map-core`](map-core) | `0.1.8` | Dependency-free map primitives: slippy-map tile maths and `TileId`. |
| [`map-engine`](map-engine) | `0.1.8` | A Compose tiled-map engine (`TiledMap`), built on `map-core`. |

Both are replaced by the `:map-view` modules. `map-engine`'s `api(project(":map-core"))` and the
rest of their Gradle files are as they were, so they will not build from here without being moved
back to the top level.
