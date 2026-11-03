package dev.dadyarri.medtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.dadyarri.medtracker.meals.CycleDay
import dev.dadyarri.medtracker.meals.CycleDayDialog
import dev.dadyarri.medtracker.measurements.MealType

@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun CycleDayDialogPreview() {
    MaterialTheme {
        Surface {
            CycleDayDialog(1, CycleDay(MealType.BREAKFAST, "FASTING", true), false, {}, {})
        }
    }
}
