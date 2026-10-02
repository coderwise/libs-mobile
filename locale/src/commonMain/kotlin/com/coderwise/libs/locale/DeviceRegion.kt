package com.coderwise.libs.locale

/**
 * ISO 3166-1 alpha-2 region of the device's current locale (e.g. `"US"`), upper-cased, or null
 * when the platform reports none — a language-only locale such as `en`, or a UN M.49 area code
 * such as `es-419`.
 *
 * This is the region the user has chosen for formats, not where the device physically is.
 */
expect fun deviceRegionCode(): String?

/**
 * The region subtag of a BCP 47 / POSIX-style locale tag, upper-cased, or null if it has none.
 * Accepts `-` or `_` separators and skips a script subtag: `en-US`, `en_US`, `zh-Hans-CN` all
 * resolve to their country; `en`, `zh-Hans` and `es-419` resolve to null.
 *
 * For platform implementations that only have a tag to go on.
 */
fun regionCodeFromLanguageTag(tag: String): String? =
    tag.substringBefore('@').substringBefore('.')
        .split('-', '_')
        .drop(1)
        .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
        ?.uppercase()
