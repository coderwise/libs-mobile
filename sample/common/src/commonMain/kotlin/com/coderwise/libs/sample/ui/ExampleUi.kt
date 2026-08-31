package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The building blocks every demo screen shares, so each example file is only its library. */

/** A labelled group of controls or readouts inside an example. */
@Composable
internal fun DemoSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/** Whatever the library just returned, shown verbatim and monospaced. */
@Composable
internal fun OutputBox(
    text: String,
    modifier: Modifier = Modifier,
    maxHeight: Int = 260,
    scrollable: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        val body = Modifier.padding(12.dp).heightIn(max = maxHeight.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (scrollable) body.verticalScroll(rememberScrollState()) else body
        )
    }
}

/**
 * One `label — value` line, for the short results a demo computes: a number, a coordinate
 * pair, a packed key. Both halves are weighted, so a value longer than its half wraps inside
 * it rather than squeezing the label down to one letter per line. Anything sentence-shaped is
 * a paragraph, not a readout.
 */
@Composable
internal fun ReadoutRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A caveat the reader needs — what a platform does differently, what the OS will not re-ask. */
@Composable
internal fun PlatformNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Multiplatform-safe fixed-decimal formatting (String.format is JVM-only). */
internal fun Double.formatted(decimals: Int = 4): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val scaled = (this * factor)
    val rounded = (if (scaled < 0) scaled - 0.5 else scaled + 0.5).toLong()
    return (rounded / factor).toString()
}
