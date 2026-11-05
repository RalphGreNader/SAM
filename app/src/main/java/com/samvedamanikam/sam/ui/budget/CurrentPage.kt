package com.samvedamanikam.sam.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.QuickEntryParser
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.BudgetPageDetail
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.data.api.Expense
import com.samvedamanikam.sam.data.api.ExpenseSaveRequest
import com.samvedamanikam.sam.data.api.Merchant
import com.samvedamanikam.sam.data.api.PageCreateRequest
import com.samvedamanikam.sam.ui.common.BarItem
import com.samvedamanikam.sam.ui.common.CategoryBars
import com.samvedamanikam.sam.ui.common.EmptyState
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.common.LocalSnackbar
import com.samvedamanikam.sam.ui.common.MessageBanner
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamNegative
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface BudgetEvent {
    data class Message(val text: String) : BudgetEvent
    data class Deleted(val expense: Expense) : BudgetEvent
}

/** @param enqueue queues an op in the outbox (used when an add can't reach the server). */
class CurrentPageViewModel(private val repo: SamRepository, private val enqueue: (OutboxOp) -> Unit) : ViewModel() {
    private val cached = repo.cachedCurrentPage()
    var detail by mutableStateOf(cached?.detail)
        private set
    /** True once we know whether a page exists (cached or fetched), so "no page" isn't shown while loading. */
    var known by mutableStateOf(cached != null)
        private set
    var categories by mutableStateOf(repo.cachedCategories())
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var input by mutableStateOf("")
        private set
    var selectedCategoryId by mutableStateOf<Int?>(null)
    var merchants by mutableStateOf<List<Merchant>>(emptyList())
        private set

    private val _events = Channel<BudgetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var merchantJob: Job? = null

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                categories = repo.categories()
                detail = repo.currentPage()
                known = true
                message = null
            } catch (e: ApiException) {
                message = e.message
            } finally {
                loading = false
            }
        }
    }

    /** Quick entry text; looks up merchant suggestions for the description part. */
    fun onInput(text: String) {
        input = text
        merchantJob?.cancel()
        val description = QuickEntryParser.parse(text)?.description
        if (description == null || description.length < 2) {
            merchants = emptyList()
            return
        }
        merchantJob = viewModelScope.launch {
            delay(250)
            merchants = runCatching { repo.merchants(description) }.getOrDefault(emptyList())
        }
    }

    fun pickMerchant(merchant: Merchant) {
        val entry = QuickEntryParser.parse(input) ?: return
        input = "${entry.amount.toPlainString()} ${merchant.description}"
        selectedCategoryId = merchant.categoryId
        merchants = emptyList()
    }

    /** Category the server will most likely pick when none is selected (exact merchant match). */
    fun suggestedCategoryName(): String? {
        val description = QuickEntryParser.parse(input)?.description ?: return null
        return merchants.firstOrNull { it.description.equals(description, ignoreCase = true) }?.categoryName
    }

    fun add() {
        val entry = QuickEntryParser.parse(input)
        if (entry == null) {
            _events.trySend(BudgetEvent.Message("Type the amount, then what it was for, e.g. \"60 gym\"."))
            return
        }
        if (saving) return
        saving = true
        val amount = entry.amount.toDouble()
        val categoryId = selectedCategoryId
        viewModelScope.launch {
            try {
                // No category: the server uses merchant memory, then keyword rules, then "Other"
                repo.addExpense(ExpenseSaveRequest(amount = amount, description = entry.description, categoryId = categoryId))
                clearInput()
                runCatching { detail = repo.currentPage() }
            } catch (e: ApiException) {
                if (e.notSent) {
                    // Offline: queue it like a widget add; it shows as "(syncing)" and is sent automatically
                    enqueue(
                        OutboxOp.AddExpense(
                            id = OutboxOp.newId(),
                            createdAtMs = System.currentTimeMillis(),
                            amount = amount,
                            description = entry.description,
                            categoryId = categoryId,
                            spentDate = Fmt.iso(LocalDate.now()),
                            source = "APP"
                        )
                    )
                    clearInput()
                    _events.trySend(BudgetEvent.Message("You're offline. Saved on this phone; it syncs automatically."))
                } else {
                    _events.trySend(BudgetEvent.Message(e.message ?: "Something went wrong."))
                }
            } finally {
                saving = false
            }
        }
    }

    private fun clearInput() {
        input = ""
        selectedCategoryId = null
        merchants = emptyList()
    }

    fun update(expense: Expense, amount: Double, description: String, categoryId: Int, date: LocalDate) = mutate {
        repo.updateExpense(expense.id, ExpenseSaveRequest(amount = amount, description = description.trim(), categoryId = categoryId, spentDate = Fmt.iso(date)))
    }

    fun delete(expense: Expense) = mutate {
        repo.deleteExpense(expense.id)
        _events.trySend(BudgetEvent.Deleted(expense))
    }

    fun restore(expense: Expense) = mutate {
        repo.addExpense(
            ExpenseSaveRequest(
                amount = expense.amount,
                description = expense.description,
                pageId = expense.pageId,
                categoryId = expense.categoryId,
                spentDate = expense.spentDate.take(10),
                source = expense.source
            )
        )
    }

    fun startPage(amount: Double, start: LocalDate, title: String) = mutate {
        repo.createPage(PageCreateRequest(title = title.trim().ifEmpty { null }, amount = amount, startDate = Fmt.iso(start)))
    }

    /** Runs a change, then reloads the page (keeps totals exactly as the server computes them). */
    private fun mutate(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                block()
                detail = repo.currentPage()
                known = true
                message = null
            } catch (e: ApiException) {
                _events.trySend(BudgetEvent.Message(e.message ?: "Something went wrong."))
            } finally {
                saving = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentPageTab() {
    val container = appContainer()
    val vm: CurrentPageViewModel = viewModel { CurrentPageViewModel(container.repository(), enqueue = { container.enqueue(listOf(it)) }) }
    val snackbar = LocalSnackbar.current
    var editing by remember { mutableStateOf<Expense?>(null) }
    var newPageOpen by rememberSaveable { mutableStateOf(false) }

    // Offline/widget adds waiting in the outbox: listed as "(syncing)"; the page reloads once they've been sent
    val outbox by container.outbox.state.collectAsStateWithLifecycle()
    val pending = outbox.pending.filterIsInstance<OutboxOp.AddExpense>()
    val categoryNames = vm.categories.associate { it.id to it.name }
    LaunchedEffect(pending.size) { vm.refresh() }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BudgetEvent.Message -> snackbar.showSnackbar(event.text)
                is BudgetEvent.Deleted -> {
                    val result = snackbar.showSnackbar("Deleted ${Fmt.moneyCompact(event.expense.amount)} ${event.expense.description}", actionLabel = "Undo")
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.expense)
                }
            }
        }
    }

    val detail = vm.detail
    Column(Modifier.fillMaxSize().imePadding()) {
        MessageBanner(vm.message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (pending.isNotEmpty()) {
            Text(
                "${pending.size} item${if (pending.size == 1) "" else "s"} waiting to sync",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        PullToRefreshBox(isRefreshing = vm.loading, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when {
                detail != null -> PageList(detail, pending, categoryNames, onEdit = { editing = it }, onNewPage = { newPageOpen = true })
                vm.known -> EmptyState(
                    title = "No budget page",
                    body = "Start a page with your budget amount. Every item you add is subtracted from it.",
                    action = { Button(onClick = { newPageOpen = true }) { Text("Start a page") } }
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        if (detail != null) QuickEntryBar(vm)
    }

    editing?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            categories = vm.categories,
            onSave = { amount, description, categoryId, date ->
                vm.update(expense, amount, description, categoryId, date)
                editing = null
            },
            onDelete = {
                vm.delete(expense)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    if (newPageOpen) {
        NewPageDialog(
            activePage = detail?.page,
            onConfirm = { amount, start, title ->
                vm.startPage(amount, start, title)
                newPageOpen = false
            },
            onDismiss = { newPageOpen = false }
        )
    }
}

/** The page laid out like the OneNote original: title + amount, items, then what's left. */
@Composable
private fun PageList(
    detail: BudgetPageDetail,
    pending: List<OutboxOp.AddExpense>,
    categoryNames: Map<Int, String>,
    onEdit: (Expense) -> Unit,
    onNewPage: () -> Unit
) {
    val page = detail.page
    val left = page.left - pending.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(Fmt.money(page.amount), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Since ${Fmt.mediumDate(page.startDate)} · ${page.itemCount + pending.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
        if (detail.items.isEmpty() && pending.isEmpty()) item {
            Text(
                "No items yet. Add one below, e.g. \"60 gym\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        items(detail.items, key = { it.id }) { ExpenseRow(it, onClick = { onEdit(it) }) }
        items(pending, key = { it.id }) { PendingRow(it, categoryNames[it.categoryId]) }
        item {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Left", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    Fmt.money(left),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (left < 0) SamNegative else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (detail.categories.isNotEmpty()) item {
            Spacer(Modifier.height(8.dp))
            Text("By category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CategoryBars(detail.categories.map { BarItem(it.categoryName, it.total, it.color) })
        }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNewPage, modifier = Modifier.fillMaxWidth()) { Text("Start new page") }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(Fmt.moneyCompact(expense.amount), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(76.dp))
        Text(expense.description, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            expense.categoryName.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(Fmt.shortDate(expense.spentDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An item saved on the phone but not yet on the server (not editable until it syncs). */
@Composable
private fun PendingRow(op: OutboxOp.AddExpense, category: String?) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.moneyCompact(op.amount), fontWeight = FontWeight.SemiBold, color = dim, modifier = Modifier.width(76.dp))
        Text("${op.description} (syncing)", color = dim, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(category ?: "auto", style = MaterialTheme.typography.labelMedium, color = dim, modifier = Modifier.padding(horizontal = 8.dp))
        Text(Fmt.shortDate(op.spentDate), style = MaterialTheme.typography.labelMedium, color = dim)
    }
}

/** "$amount expense" (e.g. "17 tacos charlie") + optional category chip; merchant suggestions pre-fill the category. */
@Composable
private fun QuickEntryBar(vm: CurrentPageViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vm.merchants.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.merchants, key = { it.description }) { m ->
                        AssistChip(onClick = { vm.pickMerchant(m) }, label = { Text("${m.description} · ${m.categoryName}") })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = vm::onInput,
                    placeholder = { Text("\$amount expense") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.add() }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::add, enabled = !vm.saving) {
                    if (vm.saving) CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Add, contentDescription = "Add item")
                }
            }
            CategoryChips(vm.categories, vm.selectedCategoryId, onSelect = { vm.selectedCategoryId = if (vm.selectedCategoryId == it) null else it })
            val entry = QuickEntryParser.parse(vm.input)
            if (entry != null) {
                val category = vm.categories.firstOrNull { it.id == vm.selectedCategoryId }?.name ?: vm.suggestedCategoryName() ?: "auto category"
                Text(
                    "Adds ${Fmt.money(entry.amount.toDouble())} · ${entry.description} · $category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryChips(categories: List<Category>, selectedId: Int?, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            FilterChip(selected = category.id == selectedId, onClick = { onSelect(category.id) }, label = { Text(category.name) })
        }
    }
}
