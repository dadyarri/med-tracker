package dev.dadyarri.medtracker.pressure

import org.junit.Assert.*
import org.junit.Test

class PressureDraftTest {
    private val valid = PressureDraft("120", "80", time = "03.11.2026 08:45")
    @Test fun optionalFieldsAreNotRequiredAndContextDoesNotDependOnTime() {
        assertNull(valid.error())
        assertNull(valid.copy(context = PressureContext.NIGHT.name).error())
    }
    @Test fun malformedAndNonPositiveValuesAreRejected() {
        for (value in listOf("", "0", "-1", "120.5", "999999999999")) {
            assertNotNull(valid.copy(systolic = value).error())
        }
        assertNotNull(valid.copy(diastolic = "120").error())
        assertNotNull(valid.copy(pulse = "0").error())
        assertNotNull(valid.copy(time = "31.02.2026 08:45").error())
    }
    @Test fun unusualValuesWarnWithoutBlocking() {
        val unusual = valid.copy(systolic = "300", pulse = "250")
        assertNull(unusual.error())
        assertTrue(unusual.unusual())
        assertFalse(valid.unusual())
    }
    @Test fun automaticContextUsesDayPartBoundaries() {
        for (hour in listOf(0, 5)) assertEquals(PressureContext.NIGHT, PressureContext.forHour(hour))
        for (hour in listOf(6, 11)) assertEquals(PressureContext.MORNING, PressureContext.forHour(hour))
        for (hour in listOf(12, 17)) assertEquals(PressureContext.DAY, PressureContext.forHour(hour))
        for (hour in listOf(18, 23)) assertEquals(PressureContext.EVENING, PressureContext.forHour(hour))
    }

    @Test fun timestampRoundTripsAtMinutePrecision() {
        assertEquals(valid.time, PressureDraft.format(valid.instant()!!))
    }
}
