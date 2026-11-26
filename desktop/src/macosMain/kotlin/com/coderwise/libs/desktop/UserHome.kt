package com.coderwise.libs.desktop

import platform.Foundation.NSHomeDirectory

internal actual fun userHomeDir(): String = NSHomeDirectory()
