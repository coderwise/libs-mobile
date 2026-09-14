package com.coderwise.libs.utils

/**
 * Hands a text file to whatever the platform shares files with, writing it a chunk at a time.
 *
 * [writeContent] is called once, with an [Appendable] that goes straight to the file (or, in a
 * browser, to the parts a Blob is assembled from). Nothing holds the finished text: a caller with
 * megabytes to write — a backup of everything the user has recorded — never has to materialise it
 * as a single String, which on a phone is the difference between a share sheet and an
 * OutOfMemoryError. The whole document is written before the share is offered, so the file the
 * user picks a destination for is complete.
 */
expect fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit)

/** For content that is already a String and small enough that holding it was never a question. */
fun shareFile(fileName: String, content: String, mimeType: String) =
    shareFile(fileName, mimeType) { it.append(content) }
