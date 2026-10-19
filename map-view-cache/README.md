# :map-view-cache

A cache for tile bytes that wraps the function a tile queue is given, so the queue never knows it is
there. No Compose, no networking: it holds what it is handed and asks for what it does not have.

```kotlin
val store = FileTileStore(FileSystem.SYSTEM, cacheDir.toPath() / "tiles", maxBytes = 200L * 1024 * 1024)
val bytes = cached(store, source = "streets", mode = CacheMode.CacheFirst) { key ->
    http.get(tileUrl("https://tile.openstreetmap.org/{z}/{x}/{y}.png", key)).readRawBytes()
}
val ground = rememberTileState(key = "streets") { key -> bytes(key)?.let { decodeVectorTile(key.z, it) } }
```

What is cached is the bytes, before they are decoded, so a restyle redraws without fetching again and
one cache serves any way of turning a tile into something to draw.

- **`TileStore`** is `get`, `put`, `remove` and `clear`, addressed by source and `TileKey` — never by
  a hash, so two tiles cannot collide. `source` is what keeps a street map's 14/4824/6159 from being
  answered with a satellite one.
- **`FileTileStore`** keeps one file per tile at `source/z/x/y.tile`, bounded by size, least recently
  used first. It takes the okio `FileSystem` and the directory, which keeps it in common code: hand it
  `FileSystem.SYSTEM` over the app's cache directory on Android, iOS and the desktop. A tile is written
  beside its place and moved there, so one half-written when the process died is never read back.
  Recency lives in memory; after a restart the order tiles were written in stands in for it.
- **`MemoryTileStore`** is the same bounded and least-recently-used, in memory. It is what a browser
  build uses — it has no file system, and its own HTTP cache already does a disk's job — and what
  tests use.
- **`CacheMode`**: `NoCache` (network alone), `CacheFirst` (the usual), `NetworkFirst` (fresh tiles,
  with what is held as the fallback when the network fails), `OfflineOnly` (never the network).
- **`tileUrl`** fills a `{z}/{x}/{y}` template, flipping the row for TMS servers.

It depends on `:map-view-core` for `TileKey` and on okio, and on nothing else: no Compose, no
Skiko. A server or a downloader can use it as it is.

A cache is never the reason a tile does not load: a store that cannot be read is a miss, and one that
cannot be written is skipped.

## Not here

Fetching. The function you pass to `cached` is yours — client, headers, retries, the tile policy of
whoever serves the tiles.
