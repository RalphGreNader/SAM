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
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.OutboxState
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.data.api.Dashboard
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.common.MessageBanner
import com.samvedamanikam.sam.ui.common.SectionCard
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamAccent
import com.samvedamanikam.sam.ui.theme.SamNegative
import com.samvedamanikam.sam.widget.WidgetSnapshot
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val repo: SamRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** The fresh dashboard lands in AppContainer.dashboard (shared with the widget and SyncWorker). */
    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                repo.dashboard()
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
    val dashboard by container.dashboard.collectAsStateWithLifecycle()
    val outbox by container.outbox.state.collectAsStateWithLifecycle()
    val categoryNames = remember(dashboard) { container.cachedCategories().associate<Category, Int?, String> { it.id to it.name } }
    val snapshot = WidgetSnapshot.build(paired = true, dashboard = dashboard, outbox = outbox, categoryNames = categoryNames)

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
            item {
                OutboxCard(
                    outbox,
                    onRetry = {
                        container.outbox.retryFailed()
                        container.requestSync()
                    },
                    onDiscard = { container.outbox.discardFailed() }
                )
            }
            item { BudgetCard(dashboard, snapshot, onOpenBudget) }
            item { TodosCard(dashboard, snapshot, onOpenTodos) }
        }
    }
}

/** Widget/share items waiting to sync, and any the server rejected (e.g. no budget page yet). */
@Composable
private fun OutboxCard(outbox: OutboxState, onRetry: () -> Unit, onDiscard: () -> Unit) {
    if (outbox.pending.isEmpty() && outbox.failed.isEmpty()) return
    SectionCard {
        if (outbox.pending.isNotEmpty()) {
            Text(
                "${outbox.pending.size} item${if (outbox.pending.size == 1) "" else "s"} waiting to sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (outbox.failed.isNotEmpty()) {
            Text("Couldn't save ${outbox.failed.size} item${if (outbox.failed.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, color = SamNegative)
            outbox.failed.forEach { failed ->
                Text("${describe(failed.op)}: ${failed.message}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private fun describe(op: OutboxOp): String = when (op) {
    is OutboxOp.AddExpense -> "${Fmt.moneyCompact(op.amount)} ${op.description}"
    is OutboxOp.AddTodo -> "Todo \"${op.title}\""
    is OutboxOp.CompleteTodo -> "Complete \"${op.title}\""
}

/** Budget numbers include items still waiting to sync, so Home and the widget always agree. */
@Composable
private fun BudgetCard(dashboard: Dashboard?, snapshot: WidgetSnapshot, onOpenBudget: () -> Unit) {
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
        BudgetSummary(page, snapshot)
        if (snapshot.expenses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            snapshot.expenses.forEach { ExpenseLine(it) }
        }
    }
}

@Composable
private fun BudgetSummary(page: BudgetPage, snapshot: WidgetSnapshot) {
    val left = snapshot.left ?: page.left
    val spent = snapshot.spent ?: page.spent
    val over = left < 0
    Text(page.title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        Fmt.money(left),
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
        progress = { if (page.amount <= 0) 1f else (spent / page.amount).toFloat().coerceIn(0f, 1f) },
        color = if (over) SamNegative else SamAccent,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(8.dp)
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Spent ${Fmt.money(spent)} · ${snapshot.itemCount} items since ${Fmt.mediumDate(page.startDate)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExpenseLine(expense: WidgetSnapshot.ExpenseLine) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.moneyCompact(expense.amount), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
        Text(
            expense.description + if (expense.pending) " (syncing)" else "",
            color = if (expense.pending) dim else Color.Unspecified,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(expense.category.orEmpty(), style = MaterialTheme.typography.labelMedium, color = dim)
    }
}

@Composable
private fun TodosCard(dashboard: Dashboard?, snapshot: WidgetSnapshot, onOpenTodos: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onOpenTodos)) {
        Text("Todos", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Count("Overdue", snapshot.overdue, if (snapshot.overdue > 0) SamNegative else MaterialTheme.colorScheme.onSurface)
            Count("Due today", snapshot.dueToday, MaterialTheme.colorScheme.onSurface)
            Count("Open", snapshot.openCount, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (snapshot.todos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            snapshot.todos.forEach { TodoLine(it, Fmt.date(dashboard?.today)) }
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
private fun TodoLine(todo: WidgetSnapshot.TodoLine, today: LocalDate?) {
    val due = todo.dueDate
    val overdue = due != null && today != null && due.isBefore(today)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (todo.priority > 0) "! " else "") + todo.title + if (todo.pending) " (syncing)" else "",
            color = if (todo.pending) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
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
