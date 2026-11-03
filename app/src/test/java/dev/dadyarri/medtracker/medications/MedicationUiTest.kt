package dev.dadyarri.medtracker.medications

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MedicationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun swipeRevealsActionsWithoutOpeningOrArchivingMedication() {
        var opened = 0
        var edited = 0
        var archived = 0
        compose.setContent { MaterialTheme {
            SwipeableMedicationCard(Medication("m", "Препарат"), true,
                { opened++ }, { edited++ }, { archived++ })
        } }
        val card = compose.onNode(hasClickAction() and hasText("Препарат"))
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Изменить препарат").assertIsDisplayed()
        compose.onNodeWithContentDescription("В архив").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened); assertEquals(0, archived) }
        card.performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("В архив").assertDoesNotExist()
        card.performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Изменить препарат").performClick()
        compose.runOnIdle { assertEquals(1, edited); assertEquals(0, archived) }
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("В архив").performClick()
        compose.runOnIdle { assertEquals(1, archived); assertEquals(1, opened) }
    }
    @Test fun archivedCardRevealsRestoreAction() {
        var restored = false
        compose.setContent { MaterialTheme {
            SwipeableMedicationCard(Medication("m", "Препарат", archived = true), true, {}, {}, { restored = true })
        } }
        compose.onNode(hasClickAction() and hasText("Препарат")).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("В архив").assertDoesNotExist()
        compose.onNodeWithContentDescription("Вернуть из архива").performClick()
        compose.runOnIdle { assertTrue(restored) }
    }
    @Test fun medicationFormRequiresNameKeepsNotesAndSupportsFractionalDose() {
        val initial = Medication("m", note = "saved note")
        var saved: Medication? = null
        compose.setContent { MaterialTheme { MedicationEditor(initial, false, null, { saved = it }, {}) } }
        compose.onNodeWithText("Форма").assertDoesNotExist()
        compose.onNodeWithText("Сохранить").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Название").performScrollTo().performTextInput("Препарат")
        compose.onNodeWithContentDescription("Доза").performTextReplacement("0,5")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("Препарат", value.name)
            assertEquals("0,5", value.usualDose)
            assertEquals("saved note", value.note)
        }
    }
    @Test fun appearanceCancelPreservesOriginalAndConfirmKeepsIndependentCapsuleColors() {
        val initial = Medication("m", "Препарат", strength = "10 мг", note = "preserved")
        var saved: Medication? = null
        compose.setContent { MaterialTheme { MedicationEditor(initial, false, null, { saved = it }, {}) } }
        compose.onNodeWithContentDescription("Внешний вид").performClick()
        compose.onNodeWithContentDescription("Форма: Капсула").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Цвет: B8DFFF").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.shape, requireNotNull(saved).shape) }
        compose.onNodeWithContentDescription("Внешний вид").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Форма: Капсула").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Цвет: B8DFFF").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Вторая половина").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Цвет: FFDAD6").performScrollTo().performClick()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("CAPSULE", value.shape)
            assertEquals(0xFFB8DFFF, value.color)
            assertEquals(0xFFFFDAD6, value.secondColor)
            assertEquals(initial.strength, value.strength)
            assertEquals(initial.note, value.note)
        }
    }
    @Test fun horizontalShapeGridSelectsContainerAndCustomHexSavesPastel() {
        val initial = Medication("m", "Препарат", unit = "мл", usualDose = "2.5")
        var saved: Medication? = null
        compose.setContent { MaterialTheme { MedicationEditor(initial, false, null, { saved = it }, {}) } }
        compose.onNodeWithContentDescription("Внешний вид").performClick()
        compose.onNodeWithContentDescription("Формы препарата").performScrollToIndex(PillShape.INHALER.ordinal)
        compose.onNodeWithContentDescription("Форма: Ингалятор").performClick()
        compose.onNodeWithContentDescription("Одна").assertDoesNotExist()
        compose.onNodeWithContentDescription("Свой цвет").performScrollTo().performClick()
        compose.onNodeWithContentDescription("HEX цвета").performTextReplacement("#FF0000")
        compose.onNodeWithText("Пастельный · #EDC0C0").assertExists()
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("INHALER", value.shape)
            assertEquals(0xFFEDC0C0, value.color)
            assertEquals("мл", value.unit)
            assertEquals("2,5", value.usualDose)
            assertEquals(0, value.score)
        }
    }
    @Test fun customUnitIsSelectedWithoutChangingDose() {
        var saved: Medication? = null
        compose.setContent { MaterialTheme { MedicationEditor(Medication("m", "Препарат"), false, null, { saved = it }, {}) } }
        compose.onNodeWithContentDescription("Доза").performTextReplacement("2,5")
        compose.onNodeWithContentDescription("Единица дозы").performClick()
        compose.onNodeWithText("Другая…").performClick()
        compose.onNodeWithContentDescription("Своя единица").performTextReplacement("мерная ложка")
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("мерная ложка", value.unit)
            assertEquals("2,5", value.usualDose)
        }
    }
    @Test fun intervalEditorBuildsFullSnapshotAndUsesLargeNumericField() {
        var saved: RegimenVersion? = null
        compose.setContent { MaterialTheme { RegimenEditor(Medication("m", "Препарат"), null, null, false, null,
            onSave = { _, version -> saved = version }, onClose = {}) } }
        compose.onNodeWithText("Каждый день").performScrollTo().performClick()
        compose.onNodeWithText("Каждые N часов").performClick()
        compose.onNodeWithContentDescription("Часов после приёма").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("HOURS", value.mode)
            assertEquals(12, value.everyHours)
            assertEquals("табл.", value.unit)
            assertFalse(value.foodOffset)
        }
    }
    @Test fun repeatingCourseCreatesNewIdentityAndCopiesEditableParameters() {
        val old = MedicationRegimen("old-course", "m", "COURSE", 1000, endsAt = 604801000, completedAt = 5000, createdAt = 1000)
        val version = RegimenVersion("old-version", old.id, 1000, "0.5", "табл.", "DAYS",
            everyDays = 2, anchorDate = "2026-11-03", food = "AFTER")
        var saved: MedicationRegimen? = null
        var snapshot: RegimenVersion? = null
        compose.setContent { MaterialTheme { RegimenEditor(Medication("m", "Препарат"), null, version, false, null,
            repeatFrom = old, onSave = { regimen, value -> saved = regimen; snapshot = value }, onClose = {}) } }
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            val revision = requireNotNull(snapshot)
            assertNotEquals(old.id, value.id)
            assertEquals("COURSE", value.kind)
            assertNull(value.completedAt)
            assertEquals("0.5", revision.dose)
            assertEquals(2, revision.everyDays)
            assertEquals("AFTER", revision.food)
            assertNotEquals(version.id, revision.id)
        }
    }
    @Test fun doseRevisionPreservesCalendarDayAnchor() {
        val old = MedicationRegimen("old", "m", "CONTINUOUS", 1000, createdAt = 1000)
        val initial = RegimenVersion("v1", old.id, 1000, "1", "табл.", "DAYS", everyDays = 3, anchorDate = "2020-01-01")
        var saved: RegimenVersion? = null
        compose.setContent { MaterialTheme { RegimenEditor(Medication("m", "Препарат"), old, initial, false, null,
            onSave = { _, version -> saved = version }, onClose = {}) } }
        compose.onNodeWithContentDescription("Доза · табл.").performTextReplacement("0,5")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val value = requireNotNull(saved)
            assertEquals("2020-01-01", value.anchorDate)
            assertEquals(3, value.everyDays)
            assertTrue(value.effectiveAt > old.startsAt)
        }
    }
    @Test fun intakeDoesNotRequireMealEventAndKeepsComment() {
        var saved: MedicationIntake? = null
        val initial = MedicationIntake("i", "m", dose = "1", unit = "табл.", takenAt = System.currentTimeMillis(),
            createdAt = 1, updatedAt = 1, comment = "preserved")
        compose.setContent { MaterialTheme { IntakeEditor(Medication("m", "Препарат"), initial, true, emptyList(),
            false, null, { saved = it }, {}, {}) } }
        compose.onNodeWithContentDescription("Доза · табл.").performTextReplacement("0,5")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle { val value = requireNotNull(saved); assertEquals("0,5", value.dose); assertEquals("preserved", value.comment) }
    }
}
