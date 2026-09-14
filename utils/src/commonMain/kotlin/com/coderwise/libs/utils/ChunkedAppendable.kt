package com.coderwise.libs.utils

/**
 * An [Appendable] that passes what it is given on in chunks of roughly [chunkChars] characters,
 * for the platforms whose file writing takes whole strings — a chunk at a time is what keeps a
 * document off the heap. Small enough that the destination, not memory, holds the document; large
 * enough that writing a hundred thousand short lines is not a hundred thousand writes.
 *
 * [flush] has to be called when the last append is done; nothing else empties the tail.
 */
internal abstract class ChunkedAppendable(private val chunkChars: Int = 64 * 1024) : Appendable {
    private val pending = StringBuilder(chunkChars + SPILL_CHARS)

    protected abstract fun emit(text: String)

    override fun append(value: Char): Appendable = apply {
        pending.append(value)
        flushIfFull()
    }

    override fun append(value: CharSequence?): Appendable = apply {
        pending.append(value)
        flushIfFull()
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable = apply {
        pending.append(value, startIndex, endIndex)
        flushIfFull()
    }

    private fun flushIfFull() {
        if (pending.length >= chunkChars) flush()
    }

    fun flush() {
        // A trailing high surrogate is half a character: emitted on its own it would be encoded as
        // a replacement character, so it waits here for the low surrogate that completes it.
        val end = if (pending.isNotEmpty() && pending.last().isHighSurrogate()) {
            pending.length - 1
        } else {
            pending.length
        }
        if (end == 0) return
        val text = pending.substring(0, end)
        pending.deleteRange(0, end)
        emit(text)
    }
}

private const val SPILL_CHARS = 1024
