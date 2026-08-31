package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.logger.AppLogger

private const val TAG = "SampleApp"

@Composable
internal fun LoggerExample() {
    // The library writes to the platform log, which the sample cannot read back; this echo is
    // only so that pressing a button visibly does something on a device with no console attached.
    val echoed = remember { mutableStateListOf<String>() }

    fun record(level: String, message: String) {
        echoed.add(0, "$level  $TAG: $message")
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                AppLogger.info(TAG, "Info from the sample")
                record("I", "Info from the sample")
            }) { Text("info") }
            Button(onClick = {
                AppLogger.warn(TAG, "Warning from the sample")
                record("W", "Warning from the sample")
            }) { Text("warn") }
            Button(onClick = {
                val cause = IllegalStateException("Nothing is actually wrong")
                AppLogger.error(TAG, "Error from the sample", cause)
                record("E", "Error from the sample (${cause.message})")
            }) { Text("error") }
        }
        if (echoed.isNotEmpty()) {
            DemoSection("Logged this session") {
                OutputBox(echoed.joinToString("\n"))
                OutlinedButton(onClick = { echoed.clear() }) { Text("Clear") }
            }
        }
        PlatformNote(
            "The lines themselves go where Kermit sends them: logcat on Android, the Xcode " +
                "console on iOS, stdout on desktop, the browser console on web. An app " +
                "configures that in one place — the same global Kermit logger this module " +
                "exposes — which is why :logger depends on Kermit as api, not implementation."
        )
    }
}
