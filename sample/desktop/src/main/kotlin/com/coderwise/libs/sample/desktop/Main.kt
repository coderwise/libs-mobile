package com.coderwise.libs.sample.desktop

import com.coderwise.libs.desktop.AboutInfo
import com.coderwise.libs.desktop.DesktopAppConfig
import com.coderwise.libs.desktop.desktopApp
import com.coderwise.libs.desktop.prepareMacOs
import com.coderwise.libs.desktop.unifiedTitleBarInset
import com.coderwise.libs.sample.ui.SampleApp

private const val APP_NAME = "Coderwise Libraries"

fun main() {
    prepareMacOs(APP_NAME)
    desktopApp(
        DesktopAppConfig(
            appName = APP_NAME,
            dataDirName = ".coderwise-libs-sample",
            appVersion = "1.0.0",
            about = AboutInfo(
                description = "A sample app exercising the Coderwise shared libraries.",
                copyright = "© Coderwise",
            ),
            koinModules = { emptyList() },
            theme = { _, content -> content() },
            unifiedTitleBar = true,
        ),
    ) {
        SampleApp(topBarStartInset = unifiedTitleBarInset)
    }
}
