package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * One screen of the gallery: a self-contained demo of a single library, labelled with the
 * [module] it exercises so that what is on screen can be traced back to the API behind it.
 *
 * [content] is emitted inside a padded, scrolling column — a demo lays out its own children and
 * needs no outer wrapper. [fillsScreen] opts out of both, for a demo like the map that wants the
 * whole viewport and does its own gesture handling (a scrolling parent would eat the drags).
 */
@Immutable
class Example(
    val title: String,
    val module: String,
    val summary: String,
    val fillsScreen: Boolean = false,
    val content: @Composable () -> Unit
)

/** The whole gallery, in order. Every module builds for every target, so every platform
 * shows the same list. */
fun sampleExamples(): List<Example> = mapExamples() + libraryExamples()
