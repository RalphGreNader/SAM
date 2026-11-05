package com.samvedamanikam.sam.widget

import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.OutboxState
import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.Dashboard
import com.samvedamanikam.sam.data.api.Expense
import com.samvedamanikam.sam.data.api.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetSnapshotTest {
    private val today = LocalDate.of(2026, 11, 2)

    private val dashboard = Dashboard(
        today = "2026-11-02T00:00:00",
        page = BudgetPage(1, "Budget: Nov 1", "2026-11-01T00:00:00", null, amount = 800.0, spent = 98.0, left = 702.0, itemCount = 2),
        recentExpenses = listOf(
            Expense(2, 1, 2, 38.0, "sushi", "2026-11-02T00:00:00"),
            Expense(1, 1, 5, 60.0, "gym", "2026-11-01T00:00:00")
        ),
        overdueCount = 1,
        dueTodayCount = 1,
        openCount = 3,
        topTodos = listOf(
            Todo(10, "Renew registration", dueDate = "2026-10-30T00:00:00"),
            Todo(11, "Call dentist", dueDate = "2026-11-02T00:00:00"),
            Todo(12, "Fix bike")
        )
    )

    @Test
    fun unpairedShowsNothing() {
        val s = WidgetSnapshot.build(paired = false, dashboard = dashboard, outbox = OutboxState(), today = today)
        assertFalse(s.paired)
        assertFalse(s.loaded)
    }

    @Test
    fun serverDataWithoutPendingChanges() {
        val s = WidgetSnapshot.build(true, dashboard, OutboxState(), today)
        assertEquals(702.0, s.left!!, 0.001)
        assertEquals(listOf("sushi", "gym"), s.expenses.map { it.description })
        assertEquals(1, s.dueToday)
        assertEquals(1, s.overdue)
        assertEquals(3, s.todos.size)
    }

    @Test
    fun pendingExpenseIsSubtractedAndListedFirst() {
        val outbox = OutboxState(pending = listOf(OutboxOp.AddExpense("a", 0, 17.0, "tacos charlie", spentDate = "2026-11-02", source = "WIDGET")))
        val s = WidgetSnapshot.build(true, dashboard, outbox, today)
        assertEquals(685.0, s.left!!, 0.001)
        assertEquals(115.0, s.spent!!, 0.001)
        assertEquals("tacos charlie", s.expenses.first().description)
        assertTrue(s.expenses.first().pending)
        assertEquals(2, s.expenses.size)
        assertEquals(1, s.pendingCount)
    }

    @Test
    fun completingTodosHidesThemAndUpdatesCounts() {
        val outbox = OutboxState(
            pending = listOf(
                OutboxOp.CompleteTodo("c1", 0, 10, "Renew registration"),
                OutboxOp.CompleteTodo("c2", 0, 11, "Call dentist")
            )
        )
        val s = WidgetSnapshot.build(true, dashboard, outbox, today)
        assertEquals(listOf("Fix bike"), s.todos.map { it.title })
        assertEquals(0, s.dueToday)
        assertEquals(0, s.overdue)
    }

    @Test
    fun pendingTodoDueTodayIsCountedAndShown() {
        val outbox = OutboxState(pending = listOf(OutboxOp.AddTodo("t", 0, "Buy milk", dueDate = "2026-11-02", source = "WIDGET")))
        val s = WidgetSnapshot.build(true, dashboard, outbox, today)
        assertEquals(2, s.dueToday)
        assertEquals("Buy milk", s.todos.first().title)
        assertTrue(s.todos.first().pending)
        assertEquals(3, s.todos.size)
    }

    @Test
    fun homeFieldsIncludePendingChanges() {
        val outbox = OutboxState(
            pending = listOf(
                OutboxOp.AddExpense("e", 0, 15.0, "movie", categoryId = 4, spentDate = "2026-11-02", source = "WIDGET"),
                OutboxOp.AddTodo("t", 0, "Buy milk", source = "WIDGET"),
                OutboxOp.CompleteTodo("c", 0, 12, "Fix bike")
            )
        )
        val s = WidgetSnapshot.build(true, dashboard, outbox, today, categoryNames = mapOf(4 to "Entertainment"))
        assertEquals(3, s.itemCount)
        assertEquals("Entertainment", s.expenses.first().category)
        assertTrue(s.expenses.first().pending)
        assertEquals(3, s.openCount) // 3 open - 1 completing + 1 new
    }

    @Test
    fun noBudgetPageHidesBudgetNumbers() {
        val s = WidgetSnapshot.build(true, dashboard.copy(page = null, recentExpenses = emptyList()), OutboxState(), today)
        assertEquals(null, s.left)
        assertTrue(s.expenses.isEmpty())
    }
}
