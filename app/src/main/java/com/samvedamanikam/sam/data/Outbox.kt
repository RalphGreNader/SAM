package com.samvedamanikam.sam.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A change made on the phone (widget quick-add, share, tap-to-complete) waiting to be sent to the server. */
@Serializable
sealed interface OutboxOp {
    val id: String
    val createdAtMs: Long

    @Serializable
    @SerialName("add_expense")
    data class AddExpense(
        override val id: String,
        override val createdAtMs: Long,
        val amount: Double,
        val description: String,
        val categoryId: Int? = null,
        /** Captured when entered, so an item synced tomorrow still lands on the right day. */
        val spentDate: String,
        val source: String
    ) : OutboxOp

    @Serializable
    @SerialName("add_todo")
    data class AddTodo(
        override val id: String,
        override val createdAtMs: Long,
        val title: String,
        val dueDate: String? = null,
        val priority: Int = 0,
        val source: String
    ) : OutboxOp

    @Serializable
    @SerialName("complete_todo")
    data class CompleteTodo(
        override val id: String,
        override val createdAtMs: Long,
        val todoId: Int,
        val title: String
    ) : OutboxOp

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** An op the server rejected (e.g. "Start a budget page first."); kept so nothing is lost silently. */
@Serializable
data class FailedOp(val op: OutboxOp, val message: String)

@Serializable
data class OutboxState(
    val pending: List<OutboxOp> = emptyList(),
    val failed: List<FailedOp> = emptyList()
)

/**
 * Persistent queue (a small JSON file). Saving here is instant even when the server is asleep or the
 * phone is offline; SyncWorker sends the ops in order.
 */
class OutboxStore(private val file: File, private val json: Json) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<OutboxState> = _state.asStateFlow()

    @Synchronized
    fun add(op: OutboxOp) = update { it.copy(pending = it.pending + op) }

    @Synchronized
    fun addAll(ops: List<OutboxOp>) = update { it.copy(pending = it.pending + ops) }

    @Synchronized
    fun peek(): OutboxOp? = _state.value.pending.firstOrNull()

    @Synchronized
    fun remove(id: String) = update { s -> s.copy(pending = s.pending.filterNot { it.id == id }) }

    @Synchronized
    fun fail(op: OutboxOp, message: String) = update { s ->
        s.copy(pending = s.pending.filterNot { it.id == op.id }, failed = s.failed + FailedOp(op, message))
    }

    /** Puts rejected ops back in the queue (e.g. after starting a budget page). */
    @Synchronized
    fun retryFailed() = update { s -> s.copy(pending = s.pending + s.failed.map { it.op }, failed = emptyList()) }

    @Synchronized
    fun discardFailed() = update { it.copy(failed = emptyList()) }

    @Synchronized
    fun clear() = update { OutboxState() }

    private fun update(change: (OutboxState) -> OutboxState) {
        val next = change(_state.value)
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(OutboxState.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        _state.value = next
    }

    private fun load(): OutboxState =
        runCatching { json.decodeFromString(OutboxState.serializer(), file.readText()) }.getOrDefault(OutboxState())
}
