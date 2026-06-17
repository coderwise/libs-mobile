package com.coderwise.libs.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// Desktop has no native share sheet; copy to the system clipboard instead.
actual fun shareText(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}
