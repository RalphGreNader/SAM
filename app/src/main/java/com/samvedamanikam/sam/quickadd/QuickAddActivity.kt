package com.samvedamanikam.sam.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.samvedamanikam.sam.AppContainer
import com.samvedamanikam.sam.MainActivity
import com.samvedamanikam.sam.SamApp
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.QuickEntryParser
import com.samvedamanikam.sam.ui.budget.CategoryChips
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.theme.SamTheme
import java.time.LocalDate

/**
 * Translucent dialog opened from the widget's "+ Expense" / "+ Task". Saves to the outbox (instant, works
 * offline and on a sleeping server), then SyncWorker sends it.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamApp).container
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_EXPENSE
        val paired = container.deviceAuth.paired.value
        setContent {
            SamTheme {
                when {
                    !paired -> NotPaired(onOpen = { openApp() }, onCancel = ::finish)
                    mode == MODE_TODO -> AddTodo(onSave = { save(container, it, "Todo added") }, onCancel = ::finish)
                    else -> AddExpense(container, onSave = { save(container, it, "Expense added") }, onCancel = ::finish)
                }
            }
        }
    }

    private fun save(container: AppContainer, op: OutboxOp, message: String) {
        container.enqueue(listOf(op))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_EXPENSE = "expense"
        const val MODE_TODO = "todo"

        fun intent(context: Context, mode: String): Intent =
            Intent(context, QuickAddActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                // Distinct data per mode so the widget's two PendingIntents don't collapse into one
                .setData("sam://quickadd/$mode".toUri())
    }
}

@Composable
private fun NotPaired(onOpen: () -> Unit, onCancel: () -> Unit) {
    QuickAddCard("Pair SAM first", "Open SAM", confirmEnabled = true, onConfirm = onOpen, onCancel = onCancel) {
        Text("Open the app and pair this phone once; then the widget can add items.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddExpense(container: AppContainer, onSave: (OutboxOp) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    val categories = remember { container.cachedCategories() }
    val entry = QuickEntryParser.parse(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val save = {
        entry?.let {
            onSave(
                OutboxOp.AddExpense(
                    id = OutboxOp.newId(),
                    createdAtMs = System.currentTimeMillis(),
                    amount = it.amount.toDouble(),
                    description = it.description,
                    categoryId = categoryId,
                    spentDate = Fmt.iso(LocalDate.now()),
                    source = "WIDGET"
                )
            )
        }
    }

    QuickAddCard("Add expense", "Add", confirmEnabled = entry != null, onConfirm = { save() }, onCancel = onCancel) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("\$amount expense") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        if (categories.isNotEmpty()) {
            CategoryChips(categories, categoryId, onSelect = { categoryId = if (categoryId == it) null else it })
        }
        Text(
            entry?.let { e ->
                val category = categories.firstOrNull { it.id == categoryId }?.name ?: "auto category"
                "Adds ${Fmt.money(e.amount.toDouble())} · ${e.description} · $category"
            } ?: "Amount first, then what it was for, e.g. 17 tacos charlie",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class Due(val label: String) { None("No date"), Today("Today"), Tomorrow("Tomorrow") }

@Composable
private fun AddTodo(onSave: (OutboxOp) -> Unit, onCancel: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf(Due.None) }
    var important by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val save = {
        if (title.isNotBlank()) onSave(
            OutboxOp.AddTodo(
                id = OutboxOp.newId(),
                createdAtMs = System.currentTimeMillis(),
                title = title.trim().take(300),
                dueDate = when (due) {
                    Due.None -> null
                    Due.Today -> Fmt.iso(LocalDate.now())
                    Due.Tomorrow -> Fmt.iso(LocalDate.now().plusDays(1))
                },
                priority = if (important) 1 else 0,
                source = "WIDGET"
            )
        )
    }

    QuickAddCard("Add todo", "Add", confirmEnabled = title.isNotBlank(), onConfirm = { save() }, onCancel = onCancel) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("What needs doing?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Due.entries.forEach { option ->
                FilterChip(selected = due == option, onClick = { due = option }, label = { Text(option.label) })
            }
        }
        FilterChip(selected = important, onClick = { important = !important }, label = { Text("! Important") })
    }
}
