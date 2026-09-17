package com.coderwise.libs.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Appends every logged line to a [LogFile].
 *
 * [CommonWriter][co.touchlab.kermit.CommonWriter] only reaches stdout, which
 * exists solely while the device is tethered to a Mac. That is exactly what a
 * field test is not: the interesting run happens with the phone in a pocket,
 * and afterwards there is nothing to inspect but the app's own state and a
 * guess. A file survives the run — and, unlike [RecentLogWriter], the process.
 */
internal class LogFileWriter(private val file: LogFile) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        file.append(formatLogLine(severity, message, tag, throwable))
    }
}
