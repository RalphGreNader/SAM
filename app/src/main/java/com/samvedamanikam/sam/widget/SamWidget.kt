package com.samvedamanikam.sam.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.samvedamanikam.sam.MainActivity
import com.samvedamanikam.sam.SamApp
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.quickadd.QuickAddActivity
import com.samvedamanikam.sam.ui.common.Fmt

private val Background = ColorProvider(Color(0xFF171A21))
private val Surface = ColorProvider(Color(0xFF262B35))
private val OnSurface = ColorProvider(Color(0xFFE3E5EA))
private val Muted = ColorProvider(Color(0xFFB8BEC9))
private val Accent = ColorProvider(Color(0xFF26C6DA))
private val Negative = ColorProvider(Color(0xFFEF5350))
private val Track = ColorProvider(Color(0xFF2E3440))

/** Resizable home-screen widget: budget left, todos due today, quick-add buttons. */
class SamWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as SamApp).container
        provideContent {
            val paired by container.deviceAuth.paired.collectAsState()
            val dashboard by container.dashboard.collectAsState()
            val outbox by container.outbox.state.collectAsState()
            val categoryNames = container.cachedCategories().associate<Category, Int?, String> { it.id to it.name }
            WidgetContent(context, WidgetSnapshot.build(paired, dashboard, outbox, categoryNames = categoryNames))
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 90.dp)
        val MEDIUM = DpSize(250.dp, 140.dp)
        val LARGE = DpSize(250.dp, 260.dp)
    }
}

class SamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SamWidget()
}

/** Tap the circle next to a todo: completes it locally right away, then syncs. */
class CompleteTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TodoIdKey] ?: return
        val container = (context.applicationContext as SamApp).container
        container.enqueue(listOf(OutboxOp.CompleteTodo(OutboxOp.newId(), System.currentTimeMillis(), todoId, parameters[TodoTitleKey].orEmpty())))
    }

    companion object {
        val TodoIdKey = ActionParameters.Key<Int>("todoId")
        val TodoTitleKey = ActionParameters.Key<String>("todoTitle")
    }
}

@Composable
private fun WidgetContent(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val medium = size.width >= SamWidget.MEDIUM.width && size.height >= SamWidget.MEDIUM.height
    val large = medium && size.height >= SamWidget.LARGE.height
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(Background)
            .padding(12.dp)
    ) {
        when {
            !s.paired -> Message("Open SAM to pair this phone", GlanceModifier.clickable(openApp))
            !s.loaded -> Message("Open SAM to load your budget", GlanceModifier.clickable(openApp))
            else -> {
                Column(GlanceModifier.defaultWeight().fillMaxWidth().clickable(openApp)) {
                    Header(s, showTitle = medium)
                    Budget(s, detailed = medium)
                    if (medium) Expenses(s)
                    if (large) Todos(s)
                }
                Spacer(GlanceModifier.height(8.dp))
                QuickAddButtons(context, compact = !medium)
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: GlanceModifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Muted, fontSize = 13.sp))
    }
}

@Composable
private fun Header(s: WidgetSnapshot, showTitle: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SAM", style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        if (showTitle && s.pageTitle != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(s.pageTitle, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        // Same wording as the app's Home tab; shortened on the small widget
        val status = when {
            s.failedCount > 0 -> "${s.failedCount} failed"
            s.pendingCount > 0 -> if (showTitle) "${s.pendingCount} waiting to sync" else "${s.pendingCount} waiting"
            else -> null
        }
        if (status != null) Text(status, style = TextStyle(color = if (s.failedCount > 0) Negative else Muted, fontSize = 11.sp))
    }
}

@Composable
private fun Budget(s: WidgetSnapshot, detailed: Boolean) {
    val left = s.left
    if (left == null) {
        Spacer(GlanceModifier.height(4.dp))
        Text("No budget page", style = TextStyle(color = Muted, fontSize = 14.sp))
    } else {
        Text(Fmt.money(left), style = TextStyle(color = if (left < 0) Negative else OnSurface, fontSize = if (detailed) 30.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (detailed && s.amount != null && s.spent != null) {
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = if (s.amount <= 0) 1f else (s.spent / s.amount).toFloat().coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = if (left < 0) Negative else Accent,
                backgroundColor = Track
            )
            Spacer(GlanceModifier.height(4.dp))
            Text("Spent ${Fmt.money(s.spent)} of ${Fmt.money(s.amount)}", style = TextStyle(color = Muted, fontSize = 12.sp))
        } else {
            Text("left", style = TextStyle(color = Muted, fontSize = 11.sp))
        }
    }
    Spacer(GlanceModifier.height(4.dp))
    val todoLine = buildString {
        append("${s.dueToday} due today")
        if (s.overdue > 0) append(" · ${s.overdue} overdue")
    }
    Text(todoLine, style = TextStyle(color = if (s.overdue > 0) Negative else Muted, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun Expenses(s: WidgetSnapshot) {
    if (s.expenses.isEmpty()) return
    Spacer(GlanceModifier.height(6.dp))
    s.expenses.forEach { e ->
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(Fmt.moneyCompact(e.amount), style = TextStyle(color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
            Text(e.description + if (e.pending) " (syncing)" else "", style = TextStyle(color = if (e.pending) Muted else OnSurface, fontSize = 13.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Todos(s: WidgetSnapshot) {
    Spacer(GlanceModifier.height(8.dp))
    Text("Todos", style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    if (s.todos.isEmpty()) {
        Text("Nothing open", style = TextStyle(color = Muted, fontSize = 13.sp))
        return
    }
    s.todos.forEach { t ->
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            val circle = GlanceModifier.width(28.dp).let { m ->
                if (t.id != null) m.clickable(
                    actionRunCallback<CompleteTodoAction>(
                        actionParametersOf(CompleteTodoAction.TodoIdKey to t.id, CompleteTodoAction.TodoTitleKey to t.title)
                    )
                ) else m
            }
            Text(if (t.pending) "…" else "○", style = TextStyle(color = Accent, fontSize = 18.sp), modifier = circle)
            Text(t.title, style = TextStyle(color = if (t.pending) Muted else OnSurface, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            t.dueDate?.let { Text(Fmt.mediumDate(it), style = TextStyle(color = Muted, fontSize = 11.sp)) }
        }
    }
}

@Composable
private fun QuickAddButtons(context: Context, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth()) {
        Pill(if (compact) "+ \$" else "+ Expense", QuickAddActivity.intent(context, QuickAddActivity.MODE_EXPENSE), compact, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        Pill(if (compact) "+ Todo" else "+ Task", QuickAddActivity.intent(context, QuickAddActivity.MODE_TODO), compact, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Pill(label: String, intent: Intent, compact: Boolean, modifier: GlanceModifier) {
    val fontSize: TextUnit = if (compact) 12.sp else 14.sp
    Box(
        modifier
            .height(if (compact) 30.dp else 36.dp)
            .cornerRadius(18.dp)
            .background(Surface)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(color = Accent, fontSize = fontSize, fontWeight = FontWeight.Bold))
    }
}
