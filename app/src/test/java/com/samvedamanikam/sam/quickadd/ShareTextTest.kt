package com.samvedamanikam.sam.quickadd

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {

    @Test
    fun oneTodoPerLineIgnoringBlanksAndBullets() {
        assertEquals(
            listOf("Buy milk", "Call mom", "Pay rent"),
            ShareText.toTodoTitles(null, "Buy milk\n\n- Call mom\n  * Pay rent  \n")
        )
    }

    @Test
    fun webPageShareCombinesSubjectAndUrl() {
        assertEquals(
            listOf("Best tacos in Austin: https://example.com/tacos"),
            ShareText.toTodoTitles("Best tacos in Austin", "https://example.com/tacos")
        )
    }

    @Test
    fun subjectAlreadyInTextIsNotRepeated() {
        assertEquals(listOf("Read: Kotlin tips"), ShareText.toTodoTitles("Kotlin tips", "Read: Kotlin tips"))
    }

    @Test
    fun subjectOnlyAndEmpty() {
        assertEquals(listOf("Just a subject"), ShareText.toTodoTitles("Just a subject", null))
        assertEquals(emptyList<String>(), ShareText.toTodoTitles(null, "   \n "))
    }

    @Test
    fun longTitlesAndManyLinesAreCapped() {
        assertEquals(300, ShareText.toTodoTitles(null, "x".repeat(500)).single().length)
        assertEquals(25, ShareText.toTodoTitles(null, (1..40).joinToString("\n") { "item $it" }).size)
    }
}
