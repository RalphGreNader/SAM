package com.samvedamanikam.sam.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.PeriodTotal
import com.samvedamanikam.sam.ui.common.BarItem
import com.samvedamanikam.sam.ui.common.CategoryBars
import com.samvedamanikam.sam.ui.common.EmptyState
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.common.MessageBanner
import com.samvedamanikam.sam.ui.common.SectionCard
import com.samvedamanikam.sam.ui.common.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ReportMode { Month, Year }

class ReportsViewModel(private val repo: SamRepository) : ViewModel() {
    /** Category colors for the bars (report rows only carry names). */
    val categoryColors: Map<Int, String?> = repo.cachedCategories().associate { it.id to it.color }
    var mode by mutableStateOf(ReportMode.Month)
        private set
    var year by mutableIntStateOf(LocalDate.now().year)
        private set
    var monthly by mutableStateOf(repo.cachedMonthlyReport(year).orEmpty())
        private set
    var yearly by mutableStateOf(repo.cachedYearlyReport().orEmpty())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun select(newMode: ReportMode) {
        mode = newMode
        refresh()
    }

    fun changeYear(delta: Int) {
        year += delta
        monthly = repo.cachedMonthlyReport(year).orEmpty()
        refresh()
    }

    fun refresh() {
        loading = true
        val requestedYear = year
        viewModelScope.launch {
            try {
                if (mode == ReportMode.Month) {
                    val rows = repo.monthlyReport(requestedYear)
                    if (requestedYear == year) monthly = rows
                } else {
                    yearly = repo.yearlyReport()
                }
                message = null
            } catch (e: ApiException) {
                message = e.message
            } finally {
                loading = false
            }
        }
    }
}

/** Spending by category, grouped by the date each item was spent (not by page). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab() {
    val container = appContainer()
    val vm: ReportsViewModel = viewModel { ReportsViewModel(container.repository()) }
    LaunchedEffect(Unit) { vm.refresh() }

    PullToRefreshBox(isRefreshing = vm.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = vm.mode == ReportMode.Month, onClick = { vm.select(ReportMode.Month) }, label = { Text("By month") })
                    FilterChip(selected = vm.mode == ReportMode.Year, onClick = { vm.select(ReportMode.Year) }, label = { Text("By year") })
                    Spacer(Modifier.weight(1f))
                    if (vm.mode == ReportMode.Month) {
                        IconButton(onClick = { vm.changeYear(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year") }
                        Text(vm.year.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.changeYear(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year") }
                    }
                }
            }
            item { MessageBanner(vm.message) }
            if (vm.mode == ReportMode.Month) monthlyReport(vm.year, vm.monthly, vm.categoryColors) else yearlyReport(vm.yearly, vm.categoryColors)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.monthlyReport(year: Int, rows: List<PeriodTotal>, colors: Map<Int, String?>) {
    if (rows.isEmpty()) {
        item { EmptyState("Nothing spent in $year", "Items you add show up here, grouped by the month they were spent.") }
        return
    }
    item {
        SectionCard {
            val label = if (year == LocalDate.now().year) "Year to date" else "Total $year"
            TotalHeader(label, rows.sumOf { it.total })
            Spacer(Modifier.height(12.dp))
            CategoryBars(byCategory(rows, colors))
        }
    }
    val months = rows.groupBy { it.month ?: 0 }.toSortedMap(compareByDescending { it })
    items(months.entries.toList(), key = { "m-${it.key}" }) { (month, monthRows) ->
        SectionCard {
            TotalHeader(Fmt.monthName(month), monthRows.sumOf { it.total })
            Spacer(Modifier.height(12.dp))
            CategoryBars(byCategory(monthRows, colors))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.yearlyReport(rows: List<PeriodTotal>, colors: Map<Int, String?>) {
    if (rows.isEmpty()) {
        item { EmptyState("No spending yet", "Yearly totals by category appear once you add items.") }
        return
    }
    val years = rows.groupBy { it.year }.toSortedMap(compareByDescending { it })
    items(years.entries.toList(), key = { "y-${it.key}" }) { (year, yearRows) ->
        SectionCard {
            TotalHeader(year.toString(), yearRows.sumOf { it.total })
            Spacer(Modifier.height(12.dp))
            CategoryBars(byCategory(yearRows, colors))
        }
    }
}

private fun byCategory(rows: List<PeriodTotal>, colors: Map<Int, String?>) =
    rows.groupBy { it.categoryId }.map { (id, r) -> BarItem(r.first().categoryName, r.sumOf { it.total }, colors[id]) }

@Composable
private fun TotalHeader(label: String, total: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(Fmt.money(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
