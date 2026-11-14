package com.coderwise.libs.sample.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.coderwise.libs.sample.ui.SampleApp
import java.awt.Desktop
import javax.imageio.ImageIO

private const val APP_NAME = "Coderwise Libraries"
private const val APP_VERSION = "1.0.0"

fun main() {
    // The app menu's title, for a launch that did not go through the Gradle run task's -Xdock
    // options -- an IDE run configuration, say. Read when AWT starts, so it has to come first.
    System.setProperty("apple.awt.application.name", APP_NAME)
    application {
        val painter = remember {
            val png = checkNotNull(Thread.currentThread().contextClassLoader.getResource("icon.png")) {
                "icon.png is missing from the desktop resources"
            }
            BitmapPainter(ImageIO.read(png).toComposeImageBitmap())
        }
        var showAbout by remember { mutableStateOf(false) }

        // macOS's standard About panel names the app after the bundle, which for a run from Gradle
        // is the JDK's ("java"). Taking the menu entry over is the one way to say what this is.
        LaunchedEffect(Unit) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
                Desktop.getDesktop().setAboutHandler { showAbout = true }
            }
        }

        Window(onCloseRequest = ::exitApplication, title = APP_NAME, icon = painter) {
            SampleApp()
        }

        if (showAbout) {
            DialogWindow(
                onCloseRequest = { showAbout = false },
                title = "About $APP_NAME",
                icon = painter,
                resizable = false,
                state = rememberDialogState(size = DpSize(340.dp, 300.dp))
            ) {
                MaterialTheme {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Image(painter, contentDescription = null, modifier = Modifier.size(96.dp))
                        Text(APP_NAME, style = MaterialTheme.typography.h6)
                        Text("Version $APP_VERSION", style = MaterialTheme.typography.body2)
                        Text(
                            "A gallery of the coderwise/libs-mobile libraries.",
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }
    }
}
