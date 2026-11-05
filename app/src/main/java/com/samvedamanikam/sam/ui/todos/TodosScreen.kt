package com.samvedamanikam.sam.ui.todos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.samvedamanikam.sam.ui.common.EmptyState

/** Placeholder until the Todos phase (grouped list, swipe to complete, add, email capture). */
@Composable
fun TodosScreen() {
    EmptyState(
        title = "Todos are coming next",
        body = "The full todo list (add, complete, email-to-task) arrives in a later phase. The Home tab already shows overdue and due-today counts.",
        modifier = Modifier.fillMaxSize()
    )
}
