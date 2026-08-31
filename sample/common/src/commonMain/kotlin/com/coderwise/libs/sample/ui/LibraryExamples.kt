package com.coderwise.libs.sample.ui

/** Everything that is not the map: one screen per library. */
internal fun libraryExamples(): List<Example> = listOf(
    Example(
        title = "Text file picker",
        module = ":filepicker",
        summary = "Opens the system document picker and hands back the chosen file as one " +
            "string — the shape a GPX backup, a JSON export or a CSV is parsed from anyway."
    ) { TextFilePickerExample() },
    Example(
        title = "Image picker",
        module = ":imagepicker",
        summary = "Opens the system photo picker and hands back the image as bytes, already " +
            "downscaled to a size worth storing."
    ) { ImagePickerExample() },
    Example(
        title = "Permissions",
        module = ":permissions",
        summary = "Runtime permission state as a Compose value, including the refusal the OS " +
            "stops prompting for — the case that needs a trip to the app's settings page."
    ) { PermissionsExample() },
    Example(
        title = "Share text",
        module = ":utils",
        summary = "One call onto the platform's share affordance: the share sheet on Android " +
            "and iOS, the clipboard where there is no such concept."
    ) { ShareTextExample() },
    Example(
        title = "Logging",
        module = ":logger",
        summary = "The AppLogger facade over Kermit, and where each platform's lines come out."
    ) { LoggerExample() }
)
