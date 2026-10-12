package com.coderwise.libs.locale

/**
 * ISO 3166-1 alpha-2 country the device is used in, upper-cased, or null when unknown.
 *
 * On Android this is the SIM's home country, then the mobile network's, falling back to
 * [deviceRegionCode]: phones in Canada commonly run "English (United States)", so the locale
 * alone would report US. Elsewhere it is [deviceRegionCode].
 */
expect fun deviceCountryCode(): String?
