package com.samvedamanikam.sam.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Same cases as Helios SamQuickEntryTests, so app and server parse identically. */
class QuickEntryParserTest {

    private fun assertParses(text: String, amount: String, description: String) {
        val entry = QuickEntryParser.parse(text)
        assertNotNull("expected '$text' to parse", entry)
        assertEquals(0, BigDecimal(amount).compareTo(entry!!.amount))
        assertEquals(description, entry.description)
    }

    @Test
    fun parsesAmountAndDescription() {
        assertParses("60 gym", "60", "gym")
        assertParses("$17 tacos charlie", "17", "tacos charlie")
        assertParses("17.50 heb", "17.50", "heb")
        assertParses("$ 5 coffee", "5", "coffee")
        assertParses("  38   sushi  ", "38", "sushi")
        assertParses("1,200 rent share", "1200", "rent share")
    }

    @Test
    fun rejectsInvalidEntries() {
        listOf(null, "", "gym", "60", "0 gym", "-5 gym", "gym 60", "17.555 heb").forEach {
            assertNull("expected '$it' to be rejected", QuickEntryParser.parse(it))
        }
    }
}
