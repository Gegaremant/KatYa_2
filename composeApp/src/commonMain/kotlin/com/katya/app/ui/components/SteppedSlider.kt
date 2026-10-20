@file:OptIn(ExperimentalMaterial3Api::class)

package com.katya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.katya.app.ui.adaptiveTextColor
import com.katya.app.ui.handCursor

/**
 * Ползунок с кнопками точной подстройки под ним.
 *
 * Ползунок сам по себе даёт «примерно», а человеку нужно «ровно на пять
 * процентов быстрее» — поэтому под ползунком ряд кнопок: каждая сдвигает
 * значение на свой шаг. Кнопки нажимаются без отпускания, поэтому шаг применяется
 * ровно один раз, а не «пока держишь».
 *
 * Шаги задаются в единицах шкалы самого ползунка: для скорости и тембра это
 * проценты (шаг 1), для задержки — десятые доли секунды (шаг 0.1).
 */
@Composable
fun SteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    title: String? = null,
    valueText: String? = null,
    steps: List<Float> = listOf(-10f, -5f, -1f, 1f, 5f, 10f),
    stepLabels: List<String> = steps.map { formatSigned(it) },
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (title != null || valueText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = adaptiveTextColor(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (valueText != null) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelSmall,
                        color = adaptiveTextColor(),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        KatyaSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            steps.forEachIndexed { index, step ->
                val label = stepLabels.getOrElse(index) { formatSigned(step) }
                StepButton(
                    text = label,
                    enabled = enabled && canMove(value, step, valueRange),
                    onClick = {
                        val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                        if (next != value) {
                            onValueChange(next)
                            onValueChangeFinished?.invoke()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Кнопка шага.
 *
 * Нажата — значит значение сдвинется; недоступна — значит уже в самом краю.
 * Иначе кнопка выглядит работающей, а ничего не делает, и человек зря ищет,
 * где сломалось.
 */
@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.25f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .handCursor(),
        )
    }
}

/** Можно ли сдвинуть значение на [step], не выходя за края шкалы. */
private fun canMove(value: Float, step: Float, range: ClosedFloatingPointRange<Float>): Boolean {
    val next = value + step
    return next > range.start && next < range.endInclusive
}

/** Подпись шага со знаком: −10, −5, −1, +1, +5, +10. */
private fun formatSigned(step: Float): String {
    val magnitude = kotlin.math.abs(step)
    val whole = magnitude.toInt()
    val text = if (magnitude == whole.toFloat()) {
        whole.toString()
    } else {
        // Десятых без локали: в общем коде нет `java.util.Locale`, а точка
        // вместо запятой в подписи шага всё равно читается однозначно.
        val tenths = kotlin.math.round((magnitude - whole) * 10f).toInt()
        "$whole.$tenths"
    }
    return if (step < 0) "−$text" else "+$text"
}
