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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.BudgetPageDetail
import com.samvedamanikam.sam.ui.common.BarItem
import com.samvedamanikam.sam.ui.common.CategoryBars
import com.samvedamanikam.sam.ui.common.EmptyState
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.common.MessageBanner
import com.samvedamanikam.sam.ui.common.SectionCard
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamNegative
import com.samvedamanikam.sam.ui.theme.SamPositive
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: SamRepository) : ViewModel() {
    var pages by mutableStateOf(repo.cachedPages())
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
                pages = repo.pages()
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
fun HistoryTab(onOpenPage: (Int) -> Unit) {
    val container = appContainer()
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container.repository()) }
    LaunchedEffect(Unit) { vm.refresh() }

    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { MessageBanner(vm.message) }
            if (vm.pages.isEmpty() && !vm.loading) item { EmptyState("No pages yet", "Pages you start on the Current page tab show up here.") }
            items(vm.pages, key = { it.id }) { page -> PageCard(page, onClick = { onOpenPage(page.id) }) }
        }
    }
}

@Composable
private fun PageCard(page: BudgetPage, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateRange(page), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OverUnder(page)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Budget ${Fmt.money(page.amount)} · Spent ${Fmt.money(page.spent)} · ${page.itemCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverUnder(page: BudgetPage) {
    val over = page.left < 0
    Column(horizontalAlignment = Alignment.End) {
        Text(
            Fmt.money(kotlin.math.abs(page.left)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (over) SamNegative else SamPositive
        )
        Text(if (over) "over" else if (page.endDate == null) "left" else "under", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dateRange(page: BudgetPage) =
    "${Fmt.mediumDate(page.startDate)} – ${if (page.endDate == null) "now" else Fmt.mediumDate(page.endDate)}"

class PageDetailViewModel(private val repo: SamRepository, private val pageId: Int) : ViewModel() {
    var detail by mutableStateOf<BudgetPageDetail?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                detail = repo.page(pageId)
            } catch (e: ApiException) {
                message = e.message
            }
        }
    }
}

/** Read-only view of a past (or current) page with its category breakdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDetailScreen(pageId: Int, onBack: () -> Unit) {
    val container = appContainer()
    val vm: PageDetailViewModel = viewModel(key = "page-$pageId") { PageDetailViewModel(container.repository(), pageId) }
    val detail = vm.detail

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(detail?.page?.title ?: "Budget page") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
        MessageBanner(vm.message, Modifier.padding(horizontal = 16.dp))
        if (detail == null) {
            if (vm.message == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val page = detail.page
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    Text(dateRange(page), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.money(page.amount), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            items(detail.items, key = { it.id }) { expense ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("Left", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        Fmt.money(page.left),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (page.left < 0) SamNegative else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (detail.categories.isNotEmpty()) item {
                Text("By category", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                CategoryBars(detail.categories.map { BarItem(it.categoryName, it.total, it.color) })
            }
        }
    }
}
