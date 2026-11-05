package com.samvedamanikam.sam.ui.common

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val currency = NumberFormat.getCurrencyInstance(Locale.US)
    private val shortDate = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
    private val mediumDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    /** "$1,234.50", "-$35.00". */
    fun money(value: Double): String = currency.format(value)

    /** "$60" for whole amounts, "$17.50" otherwise (the OneNote look). */
    fun moneyCompact(value: Double): String =
        if (value % 1.0 == 0.0) money(value).substringBeforeLast(".") else money(value)

    /** Server dates look like "2026-11-02T00:00:00". */
    fun date(value: String?): LocalDate? = value?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun shortDate(value: String?): String = date(value)?.format(shortDate) ?: ""
    fun mediumDate(value: String?): String = date(value)?.format(mediumDate) ?: ""
    fun mediumDate(value: LocalDate): String = value.format(mediumDate)
    fun longDate(value: String?): String = date(value)?.format(longDate) ?: ""
    fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.US)

    /** "2026-11-02" for API requests. */
    fun iso(value: LocalDate): String = value.toString()
}

/** Category colors come from the server as "#RRGGBB". */
fun parseColor(hex: String?, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
