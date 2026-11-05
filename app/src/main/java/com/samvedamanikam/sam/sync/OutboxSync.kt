package com.samvedamanikam.sam.sync

import com.samvedamanikam.sam.data.ApiException
import com.samvedamanikam.sam.data.OutboxOp
import com.samvedamanikam.sam.data.OutboxStore
import com.samvedamanikam.sam.data.SamRepository
import com.samvedamanikam.sam.data.api.ExpenseSaveRequest
import com.samvedamanikam.sam.data.api.TodoSaveRequest

object OutboxSync {

    /**
     * Sends pending ops oldest first. Returns false when it had to stop early (offline, server busy/down,
     * or no longer paired) so the worker retries later. Ops the server rejects (4xx) move to "failed".
     */
    suspend fun flush(outbox: OutboxStore, repo: SamRepository): Boolean {
        while (true) {
            val op = outbox.peek() ?: return true
            try {
                send(op, repo)
                outbox.remove(op.id)
            } catch (e: ApiException) {
                when {
                    isTransient(e) -> return false
                    op is OutboxOp.CompleteTodo && e.code == 404 -> outbox.remove(op.id) // todo already deleted
                    else -> outbox.fail(op, e.message ?: "The server rejected this item.")
                }
            }
        }
    }

    fun isTransient(e: ApiException): Boolean {
        val code = e.code ?: return true
        return code == 401 || code == 408 || code == 429 || code >= 500
    }

    private suspend fun send(op: OutboxOp, repo: SamRepository) {
        when (op) {
            is OutboxOp.AddExpense -> repo.addExpense(
                ExpenseSaveRequest(
                    amount = op.amount,
                    description = op.description,
                    categoryId = op.categoryId,
                    spentDate = op.spentDate,
                    source = op.source
                )
            )
            is OutboxOp.AddTodo -> repo.addTodo(TodoSaveRequest(title = op.title, dueDate = op.dueDate, priority = op.priority, source = op.source))
            is OutboxOp.CompleteTodo -> repo.completeTodo(op.todoId)
        }
    }
}
