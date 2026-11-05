package com.samvedamanikam.sam.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.Dashboard
import com.samvedamanikam.sam.data.api.Expense
import com.samvedamanikam.sam.data.api.Todo
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.common.MessageBanner
import com.samvedamanikam.sam.ui.common.SectionCard
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamAccent
import com.samvedamanikam.sam.ui.theme.SamNegative
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: SamRepository) : ViewModel() {
    var dashboard by mutableStateOf(repo.cachedDashboard())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                dashboard = repo.dashboard()
                message = null
            } catch (e: ApiException) {
                message = e.message
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenBudget: () -> Unit, onOpenTodos: () -> Unit) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository()) }
    LaunchedEffect(Unit) { vm.refresh() }
    val dashboard = vm.dashboard

    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = dashboard?.let { Fmt.longDate(it.today) } ?: "SAM",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item { MessageBanner(vm.message) }
            item { BudgetCard(dashboard, onOpenBudget) }
            item { TodosCard(dashboard, onOpenTodos) }
        }
    }
}

@Composable
private fun BudgetCard(dashboard: Dashboard?, onOpenBudget: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onOpenBudget)) {
        val page = dashboard?.page
        if (page == null) {
            Text("Budget", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (dashboard == null) "Loading..." else "No budget page yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dashboard != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenBudget) { Text("Start a page") }
            }
            return@SectionCard
        }
        BudgetSummary(page)
        if (dashboard.recentExpenses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            dashboard.recentExpenses.forEach { ExpenseLine(it) }
        }
    }
}

@Composable
private fun BudgetSummary(page: BudgetPage) {
    val over = page.left < 0
    Text(page.title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        Fmt.money(page.left),
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        color = if (over) SamNegative else MaterialTheme.colorScheme.onSurface
    )
    Text(
        if (over) "over budget" else "left of ${Fmt.money(page.amount)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { if (page.amount <= 0) 1f else (page.spent / page.amount).toFloat().coerceIn(0f, 1f) },
        color = if (over) SamNegative else SamAccent,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(8.dp)
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Spent ${Fmt.money(page.spent)} · ${page.itemCount} items since ${Fmt.mediumDate(page.startDate)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExpenseLine(expense: Expense) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.moneyCompact(expense.amount), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
        Text(expense.description, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(expense.categoryName.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodosCard(dashboard: Dashboard?, onOpenTodos: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onOpenTodos)) {
        Text("Todos", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Count("Overdue", dashboard?.overdueCount ?: 0, if ((dashboard?.overdueCount ?: 0) > 0) SamNegative else MaterialTheme.colorScheme.onSurface)
            Count("Due today", dashboard?.dueTodayCount ?: 0, MaterialTheme.colorScheme.onSurface)
            Count("Open", dashboard?.openCount ?: 0, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val todos = dashboard?.topTodos.orEmpty()
        if (todos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            todos.forEach { TodoLine(it, dashboard?.today) }
        } else if (dashboard != null) {
            Spacer(Modifier.height(8.dp))
            Text("Nothing open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOpenTodos, modifier = Modifier.align(Alignment.End)) { Text("Open todos") }
    }
}

@Composable
private fun Count(label: String, value: Int, color: Color) {
    Column {
        Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoLine(todo: Todo, today: String?) {
    val due = Fmt.date(todo.dueDate)
    val overdue = due != null && Fmt.date(today)?.let { due.isBefore(it) } == true
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (todo.priority > 0) "! " else "") + todo.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (due != null) Text(
            Fmt.mediumDate(due),
            style = MaterialTheme.typography.labelMedium,
            color = if (overdue) SamNegative else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
