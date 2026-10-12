package com.coderwise.libs.locale

import android.content.Context
import android.telephony.TelephonyManager

// The SIM's home country first: unlike the network's, it does not flip while roaming abroad.
actual fun deviceCountryCode(): String? {
    val telephony = LocaleContextProvider.appContext
        ?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return telephony?.let { it.simCountryIso.asCountryCode() ?: it.networkCountryIso.asCountryCode() }
        ?: deviceRegionCode()
}

private fun String?.asCountryCode(): String? =
    this?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
