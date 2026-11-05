package com.samvedamanikam.sam.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.data.api.Expense
import com.samvedamanikam.sam.ui.common.DateField
import com.samvedamanikam.sam.ui.common.Fmt
import com.samvedamanikam.sam.ui.theme.SamNegative
import java.time.LocalDate

private fun parseAmount(text: String): Double? = text.trim().removePrefix("$").replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

@Composable
fun EditExpenseDialog(
    expense: Expense,
    categories: List<Category>,
    onSave: (amount: Double, description: String, categoryId: Int, date: LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(expense.amount.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var description by remember { mutableStateOf(expense.description) }
    var categoryId by remember { mutableIntStateOf(expense.categoryId) }
    var date by remember { mutableStateOf(Fmt.date(expense.spentDate) ?: LocalDate.now()) }
    val valid = parseAmount(amount) != null && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Description") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryChips(categories, categoryId, onSelect = { categoryId = it })
                DateField("Date", date, onChange = { date = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseAmount(amount)!!, description, categoryId, date) }, enabled = valid) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = SamNegative) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Starting a page closes the active one on the day before the new start date. */
@Composable
fun NewPageDialog(
    activePage: BudgetPage?,
    onConfirm: (amount: Double, start: LocalDate, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by rememberSaveable { mutableStateOf(activePage?.amount?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var title by rememberSaveable { mutableStateOf("") }
    val activeStart = Fmt.date(activePage?.startDate)
    val startError = activeStart != null && !start.isAfter(activeStart)
    val valid = parseAmount(amount) != null && !startError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start new page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Budget amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                DateField("Starts", start, onChange = { start = it })
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Title (optional)") },
                    placeholder = { Text("Budget: ${Fmt.mediumDate(start)}") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                when {
                    startError -> Text(
                        "Must start after ${Fmt.mediumDate(activeStart)}, when \"${activePage!!.title}\" started.",
                        color = SamNegative, style = MaterialTheme.typography.bodySmall
                    )
                    activePage != null -> Text(
                        "\"${activePage.title}\" will close on ${Fmt.mediumDate(start.minusDays(1))}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parseAmount(amount)!!, start, title) }, enabled = valid) { Text("Start page") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
