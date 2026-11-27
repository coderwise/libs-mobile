package com.coderwise.libs.share

/**
 * A file on its way out: its [bytes], what they are, and the [name] to give the copy, without an
 * extension (the platform adds the one that goes with [mimeType]). Not a data class: comparing two of
 * them would compare every byte.
 */
class ShareFile(val bytes: ByteArray, val mimeType: String, val name: String)

/**
 * Hands content to other apps through the platform's share sheet. Implemented by the platforms that
 * can; callers bind it only there and offer no Share where nothing is bound.
 */
interface Sharer {
    /**
     * Opens the share sheet with [text] and [file], either of which may be absent. [subject] is the
     * title some targets (mail) show.
     */
    suspend fun share(subject: String?, text: String?, file: ShareFile?)
}

/** Puts a file where the user's gallery or downloads will show it. */
interface GallerySaver {
    suspend fun saveToGallery(file: ShareFile)
}
