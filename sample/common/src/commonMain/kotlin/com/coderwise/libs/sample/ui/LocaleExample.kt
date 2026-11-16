package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.locale.MeasurementSystem
import com.coderwise.libs.locale.deviceCountryCode
import com.coderwise.libs.locale.deviceMeasurementSystem
import com.coderwise.libs.locale.deviceRegionCode
import com.coderwise.libs.locale.regionCodeFromLanguageTag

@Composable
internal fun LocaleExample() {
    var region by remember { mutableStateOf("GB") }
    var tag by remember { mutableStateOf("zh-Hans-CN") }
    val device = deviceMeasurementSystem()
    val tried = MeasurementSystem.forRegionCode(region.ifBlank { null })

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoSection("This device — deviceRegionCode(), deviceCountryCode()") {
            ReadoutRow("Region", deviceRegionCode() ?: "null")
            ReadoutRow("Country", deviceCountryCode() ?: "null")
            ReadoutRow("Measurement system", device.name)
            ReadoutRow("Uses Fahrenheit", device.usesFahrenheit.toString())
        }
        DemoSection("Try a region — MeasurementSystem.forRegionCode()") {
            OutlinedTextField(
                value = region,
                onValueChange = { region = it.take(2).uppercase() },
                label = { Text("Region code (ISO 3166-1 alpha-2)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ReadoutRow("Measurement system", tried.name)
            ReadoutRow("Uses Fahrenheit", tried.usesFahrenheit.toString())
        }
        DemoSection("From a language tag — regionCodeFromLanguageTag()") {
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("BCP 47 or POSIX tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ReadoutRow("Region", regionCodeFromLanguageTag(tag) ?: "null")
        }
        PlatformNote(
            "Region is the one the user chose for formats; country is where they are used. They " +
                "differ on Android, where the country comes from the SIM and then the mobile " +
                "network before the locale — a phone in Canada commonly runs \"English (United " +
                "States)\", which the locale alone would call US. Everywhere else the two are the " +
                "same. Try \"GB\", \"US\" and \"DE\" above: the UK is metric except for road " +
                "distances, and only the US is Fahrenheit. A language-only tag such as \"en\", or " +
                "an area code such as \"es-419\", has no region and reads null."
        )
    }
}
