package com.coderwise.libs.sample.ui

/** The examples that run everywhere, Wasm included — the map modules. */
internal fun mapExamples(): List<Example> = listOf(
    Example(
        title = "Map view",
        module = ":map-view",
        summary = "A MapView of layered tile slots, a queue that fills them, and a magnified " +
            "ancestor standing in until a tile lands — with two routes, four search results " +
            "and a pin dropped wherever the map is tapped over the top. On real OpenStreetMap " +
            "tiles, because the engine fetches nothing itself: supplying the tiles is the " +
            "part worth showing.",
        fillsScreen = true
    ) { MapViewExample() },
    Example(
        title = "Vector map",
        module = ":map-view-tiles-vector",
        summary = "The same MapView and the same queue, with a slot that draws decoded geometry " +
            "instead of a bitmap — so the map knows what it is drawing. Tap it and the tile says " +
            "what is under your finger, and the water toggle shows the coastline a tile carries " +
            "for anything drawn over the sea.",
        fillsScreen = true
    ) { VectorMapExample() }
)
