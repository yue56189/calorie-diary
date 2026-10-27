package io.github.yue56189.caloriediary.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val cnDate = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val cnWeek = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

fun Double.fmt0(): String = String.format(Locale.CHINA, "%.0f", this)

fun Double.fmt1(): String = String.format(Locale.CHINA, "%.1f", this)

fun Float.fmt0(): String = String.format(Locale.CHINA, "%.0f", this)

/** 0.5 → 「0.5」，12.0 → 「12」 */
fun Double.fmtTrim(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else fmt1()

fun LocalDate.title(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.plusDays(1) -> "明天"
        else -> cnDate.format(this)
    }
}

fun LocalDate.subtitle(): String = cnWeek.format(this)

fun LocalDate.shortLabel(): String = cnDate.format(this)
