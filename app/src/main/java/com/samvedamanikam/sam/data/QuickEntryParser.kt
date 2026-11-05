package com.samvedamanikam.sam.data

import java.math.BigDecimal

/**
 * Kotlin port of Helios SamQuickEntry ("60 gym", "$17 tacos charlie", "17.50 heb").
 * Keep in sync with Common/SamCommon.cs; QuickEntryParserTest mirrors SamQuickEntryTests.
 */
object QuickEntryParser {
    data class Entry(val amount: BigDecimal, val description: String)

    private val pattern = Regex("""^\s*\$?\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s+(\S.*?)\s*$""")

    fun parse(text: String?): Entry? {
        if (text.isNullOrBlank()) return null
        val match = pattern.matchEntire(text) ?: return null
        val amount = BigDecimal(match.groupValues[1].replace(",", ""))
        val description = match.groupValues[2]
        if (amount.signum() <= 0 || description.length > 200) return null
        return Entry(amount, description)
    }
}
