package com.coderwise.libs.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleTest {
    @Test
    fun regionFromTag() {
        assertEquals("US", regionCodeFromLanguageTag("en-US"))
        assertEquals("US", regionCodeFromLanguageTag("en_us"))
        assertEquals("CN", regionCodeFromLanguageTag("zh-Hans-CN"))
        assertEquals("US", regionCodeFromLanguageTag("en_US.UTF-8"))
        assertEquals("DE", regionCodeFromLanguageTag("de-DE@collation=phonebk"))
    }

    @Test
    fun tagWithoutRegion() {
        assertNull(regionCodeFromLanguageTag("en"))
        assertNull(regionCodeFromLanguageTag("zh-Hans"))
        assertNull(regionCodeFromLanguageTag("es-419"))
        assertNull(regionCodeFromLanguageTag(""))
    }

    @Test
    fun systems() {
        assertEquals(MeasurementSystem.US, MeasurementSystem.forRegionCode("us"))
        assertEquals(MeasurementSystem.US, MeasurementSystem.forRegionCode("LR"))
        assertEquals(MeasurementSystem.UK, MeasurementSystem.forRegionCode("GB"))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.forRegionCode("CA"))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.forRegionCode(null))
    }

    @Test
    fun fahrenheitOnlyForUs() {
        assertTrue(MeasurementSystem.US.usesFahrenheit)
        assertFalse(MeasurementSystem.UK.usesFahrenheit)
        assertFalse(MeasurementSystem.METRIC.usesFahrenheit)
    }
}
