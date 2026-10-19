package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import kotlin.math.abs
import kotlin.math.floor

/**
 * Reads GeoJSON out of [json]: a FeatureCollection, a single Feature, or a bare geometry, which
 * comes back as a collection of one feature without properties.
 *
 * Fetching the text is the caller's. Throws [IllegalArgumentException], with the offset in the text
 * where it can say, for JSON that is not JSON and for JSON that is not GeoJSON — an unknown
 * geometry type, a position without a latitude. A feature whose `geometry` is `null` is allowed,
 * as the specification allows it, and draws nothing.
 */
fun parseGeoJson(json: String): FeatureCollection {
    val root = Json(json).document()
    val document = root.asObject("the document")
    return when (val type = document["type"]) {
        "FeatureCollection" -> FeatureCollection(
            document["features"].asList("features").map { feature(it.asObject("a feature")) }
        )
        "Feature" -> FeatureCollection(listOf(feature(document)))
        is String -> FeatureCollection(listOf(Feature(geometry(document))))
        else -> bad("the document has no type, found $type")
    }
}

private fun feature(source: Map<String, Any?>): Feature {
    val type = source["type"]
    if (type != "Feature") bad("expected a Feature, found $type")
    val geometry = source["geometry"]?.let { geometry(it.asObject("a geometry")) }
    @Suppress("UNCHECKED_CAST")
    val properties = (source["properties"] as? Map<String, Any?>) ?: emptyMap()
    val id = when (val id = source["id"]) {
        is String -> id
        is Double -> if (id == floor(id) && abs(id) < 1e15) id.toLong().toString() else id.toString()
        else -> null
    }
    return Feature(geometry, properties, id)
}

private fun geometry(source: Map<String, Any?>): Geometry {
    val coordinates = source["coordinates"]
    return when (val type = source["type"]) {
        "Point" -> Geometry.Point(position(coordinates))
        "MultiPoint" -> Geometry.MultiPoint(positions(coordinates))
        "LineString" -> Geometry.LineString(positions(coordinates))
        "MultiLineString" -> Geometry.MultiLineString(coordinates.asList("coordinates").map(::positions))
        "Polygon" -> Geometry.Polygon(rings(coordinates))
        "MultiPolygon" -> Geometry.MultiPolygon(coordinates.asList("coordinates").map(::rings))
        "GeometryCollection" -> Geometry.GeometryCollection(
            source["geometries"].asList("geometries").map { geometry(it.asObject("a geometry")) }
        )
        else -> bad("unknown geometry type $type")
    }
}

private fun rings(source: Any?): List<List<LatLon>> = source.asList("rings").map(::positions)

private fun positions(source: Any?): List<LatLon> = source.asList("positions").map(::position)

/** GeoJSON writes a position as `[longitude, latitude]`, with an optional altitude we ignore. */
private fun position(source: Any?): LatLon {
    val numbers = source.asList("a position")
    val lon = numbers.getOrNull(0) as? Double
    val lat = numbers.getOrNull(1) as? Double
    if (lon == null || lat == null || !lon.isFinite() || !lat.isFinite()) {
        bad("a position needs a longitude and a latitude, found $numbers")
    }
    return LatLon(lat, lon)
}

private fun bad(message: String): Nothing = throw IllegalArgumentException("Not GeoJSON: $message")

@Suppress("UNCHECKED_CAST") // the only maps there are come from Json.obj, whose names are strings
private fun Any?.asObject(what: String): Map<String, Any?> =
    this as? Map<String, Any?> ?: bad("$what is not an object")

private fun Any?.asList(what: String): List<Any?> = this as? List<Any?> ?: bad("$what is not an array")

/**
 * JSON, as far as GeoJSON needs it: objects become [Map]s, arrays [List]s, and numbers are all
 * [Double]. Anything after the document is an error; a byte order mark before it is skipped.
 */
internal class Json(private val text: String) {
    private var at = 0

    fun document(): Any? {
        if (text.startsWith('﻿')) at = 1
        val value = value(0)
        space()
        if (at < text.length) fail("unexpected '${text[at]}' after the document")
        return value
    }

    private fun value(depth: Int): Any? {
        // A cap, so that a hostile file of brackets is an error rather than a stack overflow.
        if (depth > MAX_DEPTH) fail("nested deeper than $MAX_DEPTH")
        space()
        if (at >= text.length) fail("the text ends early")
        return when (val c = text[at]) {
            '{' -> obj(depth)
            '[' -> array(depth)
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
        }
    }

    private fun obj(depth: Int): Map<String, Any?> {
        at++ // {
        val members = LinkedHashMap<String, Any?>()
        space()
        if (peek() == '}') return members.also { at++ }
        while (true) {
            space()
            if (peek() != '"') fail("expected a name")
            val name = string()
            space()
            expect(':')
            members[name] = value(depth + 1)
            space()
            when (peek()) {
                ',' -> at++
                '}' -> return members.also { at++ }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun array(depth: Int): List<Any?> {
        at++ // [
        val items = ArrayList<Any?>()
        space()
        if (peek() == ']') return items.also { at++ }
        while (true) {
            items += value(depth + 1)
            space()
            when (peek()) {
                ',' -> at++
                ']' -> return items.also { at++ }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun string(): String {
        at++ // "
        val out = StringBuilder()
        while (true) {
            if (at >= text.length) fail("a string is not closed")
            when (val c = text[at++]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (at >= text.length) fail("a string is not closed")
                    when (val escape = text[at++]) {
                        '"', '\\', '/' -> out.append(escape)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            val code = text.substring(at, minOf(at + 4, text.length)).takeIf { it.length == 4 }
                                ?.toIntOrNull(16)
                                ?: fail("a \\u escape needs four hex digits")
                            out.append(code.toChar())
                            at += 4
                        }
                        else -> fail("unknown escape \\$escape")
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = at
        while (at < text.length && text[at] in "+-.eE0123456789") at++
        return text.substring(start, at).toDoubleOrNull() ?: run {
            at = start
            fail("not a number")
        }
    }

    private fun <T> literal(word: String, value: T): T {
        if (!text.startsWith(word, at)) fail("unexpected '${text[at]}'")
        at += word.length
        return value
    }

    private fun space() {
        while (at < text.length && text[at].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) at++
    }

    private fun peek(): Char? = text.getOrNull(at)

    private fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        at++
    }

    private fun fail(message: String): Nothing =
        throw IllegalArgumentException("Not GeoJSON: $message, at $at")

    private companion object {
        const val MAX_DEPTH = 256
    }
}
