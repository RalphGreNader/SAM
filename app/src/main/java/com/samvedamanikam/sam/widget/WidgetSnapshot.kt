package com.samvedamanikam.sam.widget

import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.OutboxState
import com.samvedamanikam.sam.data.api.Dashboard
import java.time.LocalDate

/**
 * The last dashboard from the server with pending phone changes layered on top, so a quick-add shows up
 * immediately even before it syncs. Used by both the widget and the app's Home tab so they always agree.
 */
data class WidgetSnapshot(
    val paired: Boolean,
    val loaded: Boolean,
    val pageTitle: String? = null,
    val amount: Double? = null,
    val spent: Double? = null,
    val left: Double? = null,
    val itemCount: Int = 0,
    val expenses: List<ExpenseLine> = emptyList(),
    val dueToday: Int = 0,
    val overdue: Int = 0,
    val openCount: Int = 0,
    val todos: List<TodoLine> = emptyList(),
    val pendingCount: Int = 0,
    val failedCount: Int = 0
) {
    data class ExpenseLine(val amount: Double, val description: String, val pending: Boolean, val category: String? = null)
    data class TodoLine(val id: Int?, val title: String, val dueDate: LocalDate?, val pending: Boolean, val priority: Int = 0)

    companion object {
        fun build(
            paired: Boolean,
            dashboard: Dashboard?,
            outbox: OutboxState,
            today: LocalDate = LocalDate.now(),
            categoryNames: Map<Int?, String> = emptyMap()
        ): WidgetSnapshot {
            if (!paired) return WidgetSnapshot(paired = false, loaded = false)
            val pendingExpenses = outbox.pending.filterIsInstance<OutboxOp.AddExpense>()
            val pendingTodos = outbox.pending.filterIsInstance<OutboxOp.AddTodo>()
            val completing = outbox.pending.filterIsInstance<OutboxOp.CompleteTodo>().map { it.todoId }.toSet()
            val base = WidgetSnapshot(paired = true, loaded = dashboard != null, pendingCount = outbox.pending.size, failedCount = outbox.failed.size)
            if (dashboard == null) return base

            val page = dashboard.page
            val pendingSpent = pendingExpenses.sumOf { it.amount }
            val expenses = pendingExpenses.reversed().map { ExpenseLine(it.amount, it.description, pending = true, category = categoryNames[it.categoryId]) } +
                dashboard.recentExpenses.map { ExpenseLine(it.amount, it.description, pending = false, category = it.categoryName) }

            val serverTodos = dashboard.topTodos.filterNot { it.id in completing }.map {
                TodoLine(it.id, it.title, it.dueDate?.take(10)?.let(LocalDate::parse), pending = false, priority = it.priority)
            }
            val newTodos = pendingTodos.map { TodoLine(null, it.title, it.dueDate?.let(LocalDate::parse), pending = true, priority = it.priority) }
            val completedDueToday = dashboard.topTodos.count { it.id in completing && it.dueDate?.take(10) == today.toString() }
            val completedOverdue = dashboard.topTodos.count { t -> t.id in completing && t.dueDate?.take(10)?.let { LocalDate.parse(it).isBefore(today) } == true }

            return base.copy(
                pageTitle = page?.title,
                amount = page?.amount,
                spent = page?.let { it.spent + pendingSpent },
                left = page?.let { it.left - pendingSpent },
                itemCount = page?.let { it.itemCount + pendingExpenses.size } ?: 0,
                expenses = if (page == null) emptyList() else expenses.take(2),
                dueToday = (dashboard.dueTodayCount - completedDueToday + pendingTodos.count { it.dueDate == today.toString() }).coerceAtLeast(0),
                overdue = (dashboard.overdueCount - completedOverdue).coerceAtLeast(0),
                openCount = (dashboard.openCount - completing.size + pendingTodos.size).coerceAtLeast(0),
                todos = (newTodos + serverTodos).take(3)
            )
        }
    }
}
