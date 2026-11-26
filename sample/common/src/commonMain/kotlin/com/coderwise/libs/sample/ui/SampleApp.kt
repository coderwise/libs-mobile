package com.coderwise.libs.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.coderwise.libs.utils.horizontalSlideTransition

/**
 * A gallery of the libraries in this repo, running on every supported platform. Each entry is a
 * small screen exercising one module through its public API, meant to be read as much as run.
 *
 * The shell is deliberately plain: a list, a detail screen, and one back affordance, with only
 * navigation3's NavDisplay, no DI container, and no resources — so that what a reader has to
 * understand to copy an example is the library it demonstrates, and nothing else.
 */
@Composable
fun SampleApp(topBarStartInset: Dp = 0.dp) {
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        val onToggleTheme = { dark = !dark }
        val examples = remember { sampleExamples() }
        // The back stack is the gallery, then at most one example (keyed by its title).
        val backStack = remember { mutableStateListOf<Any>(GalleryKey) }
        // Hoisted above the NavDisplay, so coming back from an example lands where the reader
        // left the list rather than at the top of it.
        val galleryState = rememberLazyGridState()

        // NavDisplay takes the system back gesture itself, and slides every screen in from the
        // right and back out again (horizontalSlideTransition, from :utils).
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { key ->
                NavEntry(key, metadata = horizontalSlideTransition()) {
                    if (key == GalleryKey) {
                        ExampleGallery(examples, galleryState, topBarStartInset, onToggleTheme, onSelect = { backStack.add(it.title) })
                    } else {
                        ExampleScreen(
                            examples.first { it.title == key },
                            topBarStartInset,
                            onToggleTheme,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            }
        )
    }
}

private data object GalleryKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleGallery(
    examples: List<Example>,
    listState: LazyGridState,
    topBarStartInset: Dp,
    onToggleTheme: () -> Unit,
    onSelect: (Example) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coderwise libraries") },
                actions = { ThemeToggle(onToggleTheme) },
                windowInsets = WindowInsets(left = topBarStartInset)
            )
        }
    ) { inner ->
        // As many columns as fit at a card's comfortable width: one on a phone, which is the
        // list this used to be, and more as the window widens, instead of one card stretched
        // across a desktop or browser window.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { GalleryIntro() }
            items(examples, key = { it.title }) { example ->
                ExampleCard(example, onClick = { onSelect(example) })
            }
        }
    }
}

private val CARD_MIN_WIDTH = 320.dp

@Composable
private fun GalleryIntro() {
    Text(
        text = "Each entry demonstrates one module through its published API. Every module " +
            "builds for every target, so this same list runs on Android, iOS, desktop, and " +
            "both browser builds — what changes between them is what each platform does with " +
            "the call.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ExampleCard(example: Example, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(example.title, style = MaterialTheme.typography.titleMedium)
            ModuleLabel(example.module)
            Text(
                text = example.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Cards in one grid row are sized by their own content, so a row only reads as
                // even if every summary takes the same three lines.
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleScreen(example: Example, topBarStartInset: Dp, onToggleTheme: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(left = topBarStartInset),
                actions = { ThemeToggle(onToggleTheme) },
                title = {
                    Column {
                        Text(example.title, style = MaterialTheme.typography.titleMedium)
                        ModuleLabel(example.module)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(BackArrow, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        if (example.fillsScreen) {
            Box(Modifier.fillMaxSize().padding(inner)) { example.content() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = example.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                example.content()
            }
        }
    }
}

/**
 * Material's `arrow_back`, drawn here rather than imported: the icon packs are a separate
 * artifact from Compose, and one glyph is not worth a dependency the copied code would
 * inherit. A text arrow was worse — a `TextButton` around "←" is a small, off-centre
 * character where a 48dp navigation control belongs.
 */
private val BackArrow: ImageVector = ImageVector.Builder(
    name = "ArrowBack",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.41f, 18.59f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        verticalLineTo(11f)
        close()
    }
}.build()

@Composable
private fun ThemeToggle(onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(ContrastIcon, contentDescription = "Toggle light/dark theme")
    }
}

/** Material's `contrast`, drawn here for the same reason as [BackArrow]. */
private val ContrastIcon: ImageVector = ImageVector.Builder(
    name = "Contrast",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = PathParser()
        .parsePathString("M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm1-17.93c3.94.49 7 3.85 7 7.93s-3.05 7.44-7 7.93V4.07z")
        .toNodes(),
    fill = SolidColor(Color.Black)
).build()

@Composable
private fun ModuleLabel(module: String) {
    Text(
        text = module,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Preview
@Composable
fun SampleAppPreview() {
    SampleApp()
}
