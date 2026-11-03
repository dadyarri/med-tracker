package dev.dadyarri.medtracker.medications

import org.junit.Assert.*
import org.junit.Test

class MedicationAppearanceColorsTest {
    @Test fun saturatedHexBecomesPastelWithoutChangingHue() {
        assertEquals(0xFFEDC0C0, pastelMedicationColor("FF0000"))
        assertEquals(0xFFC0EDC0, pastelMedicationColor("00FF00"))
        assertEquals(0xFFC0C0ED, pastelMedicationColor("0000FF"))
        assertEquals(pastelMedicationColor("FF0000"), pastelMedicationColor(" #f00 "))
    }
    @Test fun neutralColorsStayNeutralAndInvalidHexIsRejected() {
        assertEquals(0xFFD6D6D6, pastelMedicationColor("000000"))
        assertEquals(0xFFFFFFFF, pastelMedicationColor("FFFFFF"))
        assertEquals(0xFFD6D6D6, pastelMedicationColor("808080"))
        listOf("", "12", "1234", "12345G", "1234567", "red").forEach { assertNull(pastelMedicationColor(it)) }
    }
}
