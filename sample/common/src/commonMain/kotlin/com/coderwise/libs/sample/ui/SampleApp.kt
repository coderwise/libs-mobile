package com.coderwise.libs.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A gallery of the libraries in this repo, running on every supported platform. Each entry is a
 * small screen exercising one module through its public API, meant to be read as much as run.
 *
 * The shell is deliberately plain: a list, a detail screen, and one back affordance, with no
 * navigation library, no DI container, and no resources — so that what a reader has to
 * understand to copy an example is the library it demonstrates, and nothing else.
 */
@Composable
fun SampleApp() {
    MaterialTheme {
        val examples = remember { sampleExamples() }
        var selected by remember { mutableStateOf<Example?>(null) }
        // Hoisted above the branch, so coming back from an example lands where the reader left
        // the list rather than at the top of it.
        val galleryState = rememberLazyListState()

        if (selected == null) {
            ExampleGallery(examples, galleryState, onSelect = { selected = it })
        } else {
            ExampleScreen(requireNotNull(selected), onBack = { selected = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleGallery(
    examples: List<Example>,
    listState: LazyListState,
    onSelect: (Example) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Coderwise libraries") }) }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { GalleryIntro() }
            items(examples, key = { it.title }) { example ->
                ExampleCard(example, onClick = { onSelect(example) })
            }
        }
    }
}

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
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleScreen(example: Example, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
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
