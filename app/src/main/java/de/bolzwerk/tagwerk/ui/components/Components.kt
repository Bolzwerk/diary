package de.bolzwerk.tagwerk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.bolzwerk.tagwerk.data.repo.NoteCategory
import de.bolzwerk.tagwerk.domain.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun categoryColor(colorIndex: Int): Color =
    Color(CategoryColors.palette.getOrElse(colorIndex) { CategoryColors.palette.first() })

@Composable
fun ColorDot(colorIndex: Int, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(categoryColor(colorIndex)))
}

/**
 * Kategorie-Chip. Bestätigte Kategorien erscheinen gefüllt; KI-Vorschläge umrandet mit ✨.
 * Antippen eines Vorschlags bestätigt ihn, das ✕ entfernt ihn.
 */
@Composable
fun CategoryChip(
    category: NoteCategory,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = category.confirmed,
        onClick = { if (category.isSuggestion) onConfirm() },
        label = { Text(category.name) },
        leadingIcon = {
            if (category.isSuggestion) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "KI-Vorschlag", modifier = Modifier.size(InputChipDefaults.IconSize))
            } else {
                ColorDot(category.colorIndex)
            }
        },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = "${category.name} entfernen",
                modifier = Modifier.size(InputChipDefaults.IconSize).clip(CircleShape).clickable(onClick = onRemove),
            )
        },
        modifier = modifier,
    )
}

/** Balken-Wellenform aus Pegelwerten (0..1), neueste rechts. */
@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val barWidth = size.width / WAVEFORM_BARS
        val gap = barWidth * 0.35f
        val start = WAVEFORM_BARS - levels.size
        levels.forEachIndexed { i, level ->
            val h = (size.height * (0.06f + 0.94f * level.coerceIn(0f, 1f)))
            val x = (start + i) * barWidth
            drawRoundRect(
                color = color,
                topLeft = Offset(x + gap / 2, (size.height - h) / 2),
                size = Size(barWidth - gap, h),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    }
}

const val WAVEFORM_BARS = 48

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(millis))

fun formatShortDate(millis: Long): String {
    val today = SimpleDateFormat("yyyyMMdd", Locale.GERMANY)
    val now = Date()
    val date = Date(millis)
    return if (today.format(now) == today.format(date)) {
        SimpleDateFormat("HH:mm", Locale.GERMANY).format(date)
    } else {
        SimpleDateFormat("dd.MM.", Locale.GERMANY).format(date)
    }
}
