package com.samvedamanikam.sam.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Helios returns DB column names verbatim (UPPERCASE). Dates arrive as "2026-11-02T00:00:00"; see Fmt.date().

@Serializable
data class Category(
    @SerialName("CATEGORY_ID") val id: Int,
    @SerialName("NAME") val name: String,
    @SerialName("ICON") val icon: String? = null,
    @SerialName("COLOR") val color: String? = null,
    @SerialName("SORT_ORDER") val sortOrder: Int = 0
)

@Serializable
data class Todo(
    @SerialName("TODO_ID") val id: Int,
    @SerialName("TITLE") val title: String,
    @SerialName("NOTES") val notes: String? = null,
    @SerialName("DUE_DATE") val dueDate: String? = null,
    @SerialName("PRIORITY") val priority: Int = 0,
    @SerialName("STATUS") val status: String = "OPEN"
)

@Serializable
data class BudgetPage(
    @SerialName("PAGE_ID") val id: Int,
    @SerialName("TITLE") val title: String,
    @SerialName("START_DATE") val startDate: String,
    @SerialName("END_DATE") val endDate: String? = null,
    @SerialName("AMOUNT") val amount: Double,
    @SerialName("NOTES") val notes: String? = null,
    @SerialName("SPENT") val spent: Double = 0.0,
    @SerialName("LEFT") val left: Double = 0.0,
    @SerialName("ITEM_COUNT") val itemCount: Int = 0
)

@Serializable
data class Expense(
    @SerialName("EXPENSE_ID") val id: Int,
    @SerialName("PAGE_ID") val pageId: Int,
    @SerialName("CATEGORY_ID") val categoryId: Int,
    @SerialName("AMOUNT") val amount: Double,
    @SerialName("DESCRIPTION") val description: String,
    @SerialName("SPENT_DATE") val spentDate: String,
    @SerialName("SOURCE") val source: String? = null,
    @SerialName("CATEGORY_NAME") val categoryName: String? = null
)

@Serializable
data class CategoryTotal(
    @SerialName("CATEGORY_ID") val categoryId: Int,
    @SerialName("CATEGORY_NAME") val categoryName: String,
    @SerialName("COLOR") val color: String? = null,
    @SerialName("TOTAL") val total: Double
)

/** One report row; month is null in yearly reports. */
@Serializable
data class PeriodTotal(
    @SerialName("YEAR") val year: Int,
    @SerialName("MONTH") val month: Int? = null,
    @SerialName("CATEGORY_ID") val categoryId: Int,
    @SerialName("CATEGORY_NAME") val categoryName: String,
    @SerialName("TOTAL") val total: Double
)

@Serializable
data class BudgetPageDetail(
    @SerialName("PAGE") val page: BudgetPage,
    @SerialName("ITEMS") val items: List<Expense> = emptyList(),
    @SerialName("CATEGORIES") val categories: List<CategoryTotal> = emptyList()
)

@Serializable
data class Merchant(
    @SerialName("DESCRIPTION") val description: String,
    @SerialName("CATEGORY_ID") val categoryId: Int,
    @SerialName("CATEGORY_NAME") val categoryName: String,
    @SerialName("USES") val uses: Int
)

@Serializable
data class Dashboard(
    @SerialName("TODAY") val today: String,
    @SerialName("PAGE") val page: BudgetPage? = null,
    @SerialName("RECENT_EXPENSES") val recentExpenses: List<Expense> = emptyList(),
    @SerialName("OVERDUE_COUNT") val overdueCount: Int = 0,
    @SerialName("DUE_TODAY_COUNT") val dueTodayCount: Int = 0,
    @SerialName("OPEN_COUNT") val openCount: Int = 0,
    @SerialName("TOP_TODOS") val topTodos: List<Todo> = emptyList()
)

// ---- Requests (null fields are omitted; the server applies its defaults) ----

@Serializable
data class PageCreateRequest(
    @SerialName("TITLE") val title: String? = null,
    @SerialName("AMOUNT") val amount: Double? = null,
    @SerialName("START_DATE") val startDate: String? = null,
    @SerialName("NOTES") val notes: String? = null
)

@Serializable
data class ExpenseSaveRequest(
    @SerialName("AMOUNT") val amount: Double,
    @SerialName("DESCRIPTION") val description: String,
    @SerialName("PAGE_ID") val pageId: Int? = null,
    @SerialName("CATEGORY_ID") val categoryId: Int? = null,
    @SerialName("SPENT_DATE") val spentDate: String? = null,
    @SerialName("SOURCE") val source: String? = "APP"
)

@Serializable
data class QuickEntryRequest(
    @SerialName("TEXT") val text: String,
    @SerialName("SOURCE") val source: String? = "APP"
)

/** Cached form of "current page", which may legitimately be absent. */
@Serializable
data class CurrentPageSnapshot(val detail: BudgetPageDetail? = null)
