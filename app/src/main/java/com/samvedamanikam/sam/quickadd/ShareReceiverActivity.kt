package com.samvedamanikam.sam.quickadd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.samvedamanikam.sam.MainActivity
import com.samvedamanikam.sam.SamApp
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.ui.theme.SamTheme

/** "Share → SAM": shared text becomes todos, one per line. */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamApp).container
        val titles = ShareText.toTodoTitles(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        val paired = container.deviceAuth.paired.value

        setContent {
            SamTheme {
                when {
                    !paired -> QuickAddCard("Pair SAM first", "Open SAM", true, onConfirm = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }, onCancel = ::finish) {
                        Text("Open the app and pair this phone once; then you can share text into SAM.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    titles.isEmpty() -> QuickAddCard("Nothing to add", "OK", true, onConfirm = ::finish, onCancel = ::finish) {
                        Text("The shared content had no text.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> QuickAddCard(
                        title = if (titles.size == 1) "Add todo" else "Add ${titles.size} todos",
                        confirmLabel = "Add",
                        confirmEnabled = true,
                        onConfirm = {
                            val now = System.currentTimeMillis()
                            container.enqueue(titles.map { OutboxOp.AddTodo(OutboxOp.newId(), now, it, source = "SHARE") })
                            Toast.makeText(this, if (titles.size == 1) "Todo added" else "${titles.size} todos added", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = ::finish
                    ) {
                        titles.take(6).forEach { Text("• $it", maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        if (titles.size > 6) Text("and ${titles.size - 6} more", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

object ShareText {
    private const val MAX_TITLE = 300
    private const val MAX_ITEMS = 25

    /**
     * One todo per non-blank line. A single line shared with a subject (e.g. a web page: title + URL)
     * becomes one todo "subject: text".
     */
    fun toTodoTitles(subject: String?, text: String?): List<String> {
        val lines = text.orEmpty().lines().map { it.trim().trimStart('-', '*', '•').trim() }.filter { it.isNotEmpty() }
        val s = subject?.trim().orEmpty()
        val titles = when {
            lines.isEmpty() && s.isNotEmpty() -> listOf(s)
            lines.size == 1 && s.isNotEmpty() && !lines[0].contains(s) -> listOf("$s: ${lines[0]}")
            else -> lines
        }
        return titles.map { it.take(MAX_TITLE) }.take(MAX_ITEMS)
    }
}
