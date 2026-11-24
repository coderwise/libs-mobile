package com.coderwise.libs.sample.ui

/** One screen per library. */
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
        title = "Camera",
        module = ":camera",
        summary = "A live viewfinder and a single capture, behind a controller that reports " +
            "whether this device has a camera at all."
    ) { CameraExample() },
    Example(
        title = "Location",
        module = ":location",
        summary = "One GPS fix, or a stream of them, as a Result — so a refusal is something " +
            "to show rather than catch."
    ) { LocationExample() },
    Example(
        title = "Permissions",
        module = ":permissions",
        summary = "Runtime permission state as a Compose value, including the refusal the OS " +
            "stops prompting for — the case that needs a trip to the app's settings page."
    ) { PermissionsExample() },
    Example(
        title = "Locale",
        module = ":locale",
        summary = "The device's region and country, and the measurement system they imply — " +
            "with the Android case where the SIM disagrees with the locale."
    ) { LocaleExample() },
    Example(
        title = "Settings",
        module = ":settings",
        summary = "Typed, serializable settings that outlive the process: a DataStore file on " +
            "Android, iOS and desktop, localStorage in the browser."
    ) { SettingsExample() },
    Example(
        title = "Database",
        module = ":database",
        summary = "The SQLite driver for each platform, behind one factory — here driving a " +
            "table of notes with a hand-written schema."
    ) { DatabaseExample() },
    Example(
        title = "Billing",
        module = ":billing",
        summary = "One-time purchases behind one API — shown through the inert implementation " +
            "every build without a store gets."
    ) { BillingExample() },
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
