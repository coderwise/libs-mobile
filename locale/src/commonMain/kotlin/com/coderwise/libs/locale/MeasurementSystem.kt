package com.coderwise.libs.locale

/** The unit conventions a region uses day to day. */
enum class MeasurementSystem {
    METRIC,

    /** Metric, except road distances and speeds, which are in miles. */
    UK,

    /** US customary units, including Fahrenheit. */
    US;

    /** Whether temperatures are conventionally given in °F. */
    val usesFahrenheit: Boolean get() = this == US

    companion object {
        // Regions whose everyday units are US customary (CLDR `measurementSystem` = US).
        private val usRegions = setOf(
            "US", "AS", "GU", "MP", "PR", "UM", "VI", // United States and territories
            "BS", "BZ", "KY", "LR", "PW", "FM", "MH", // others using the US system
        )

        /** The system for an ISO 3166-1 alpha-2 [regionCode]; metric when unknown or null. */
        fun forRegionCode(regionCode: String?): MeasurementSystem = when (regionCode?.uppercase()) {
            null -> METRIC
            "GB" -> UK
            in usRegions -> US
            else -> METRIC
        }
    }
}

/** The [MeasurementSystem] of the device's region, or metric when the region is unknown. */
fun deviceMeasurementSystem(): MeasurementSystem =
    MeasurementSystem.forRegionCode(deviceRegionCode())
