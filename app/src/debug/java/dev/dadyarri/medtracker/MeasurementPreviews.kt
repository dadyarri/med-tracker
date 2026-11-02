package dev.dadyarri.medtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.*
import dev.dadyarri.medtracker.pressure.PressureRecord

@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun WeightEntryPreview() {
    MaterialTheme { Surface {
        MeasurementEditor("preview", MeasurementDraft(MeasurementType.WEIGHT, "72,3", "03.11.2026 08:45"),
            false, false, null, {}, {}, {})
    } }
}
@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun GlucoseEntryPreview() {
    MaterialTheme { Surface {
        MeasurementEditor("preview", MeasurementDraft(MeasurementType.GLUCOSE, "5,6", "03.11.2026 08:45"),
            false, false, null, {}, {}, {})
    } }
}
@Preview(widthDp = 400, heightDp = 800)
@Composable
fun MeasurementChartsPreview() {
    val start = 1793682000000L
    val entries = journal(
        listOf(PressureRecord("p1", 120, 80, 72, null, "", start, start, start),
            PressureRecord("p2", 125, 82, null, null, "", start + 86400000, start, start)),
        listOf(WeightRecord("w", 723, null, "", start, start, start)),
        listOf(GlucoseRecord("g1", 550, "MMOL", "FASTING", null, "", start, start, start),
            GlucoseRecord("g2", 12600, "MG", "AFTER", null, "", start + 86400000, start, start)))
    MaterialTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Analytics(entries, GlucoseUnit.MMOL)
    } } }
}
