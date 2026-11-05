package com.samvedamanikam.sam.ui.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private val tabs = listOf("Current page", "History", "Reports")

/** Budget: the OneNote-style current page, past pages, and spending reports. */
@Composable
fun BudgetScreen(onOpenPage: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        when (selected) {
            0 -> CurrentPageTab()
            1 -> HistoryTab(onOpenPage)
            else -> ReportsTab()
        }
    }
}
