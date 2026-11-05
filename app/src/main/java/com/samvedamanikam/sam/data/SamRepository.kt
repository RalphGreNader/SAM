package com.samvedamanikam.sam.data

import com.samvedamanikam.sam.data.api.BudgetPage
import com.samvedamanikam.sam.data.api.BudgetPageDetail
import com.samvedamanikam.sam.data.api.Category
import com.samvedamanikam.sam.data.api.CurrentPageSnapshot
import com.samvedamanikam.sam.data.api.Dashboard
import com.samvedamanikam.sam.data.api.Expense
import com.samvedamanikam.sam.data.api.ExpenseSaveRequest
import com.samvedamanikam.sam.data.api.Merchant
import com.samvedamanikam.sam.data.api.PageCreateRequest
import com.samvedamanikam.sam.data.api.PeriodTotal
import com.samvedamanikam.sam.data.api.QuickEntryRequest
import com.samvedamanikam.sam.data.api.SamApi
import com.samvedamanikam.sam.data.api.Todo
import com.samvedamanikam.sam.data.api.TodoSaveRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * A failure worth showing to the user. [offline] means "couldn't reach the server"; [notSent] means the request
 * certainly never reached it (safe to queue for later without risking a duplicate); [code] is the HTTP status.
 */
class ApiException(message: String, val offline: Boolean = false, val code: Int? = null, val notSent: Boolean = false) : Exception(message)

/**
 * @param onDashboard called with every fresh dashboard (drives the home-screen widget).
 * @param onMutated called after any successful change, so the widget can refresh.
 */
class SamRepository(
    private val api: SamApi,
    private val cache: JsonCache,
    private val json: Json,
    private val onDashboard: (Dashboard) -> Unit = {},
    private val onMutated: () -> Unit = {}
) {

    fun cachedDashboard(): Dashboard? = cache.read("dashboard")
    suspend fun dashboard(): Dashboard = call { api.dashboard() }.also {
        cache.write("dashboard", it)
        onDashboard(it)
    }

    fun cachedCategories(): List<Category> = cache.read("categories") ?: emptyList()
    suspend fun categories(): List<Category> = call { api.categories() }.also { cache.write("categories", it) }

    fun cachedCurrentPage(): CurrentPageSnapshot? = cache.read("current_page")
    suspend fun currentPage(): BudgetPageDetail? = call {
        val response = api.currentPage()
        when {
            response.isSuccessful -> response.body()
            response.code() == 404 -> null
            else -> throw HttpException(response)
        }
    }.also { cache.write("current_page", CurrentPageSnapshot(it)) }

    fun cachedPages(): List<BudgetPage> = cache.read("pages") ?: emptyList()
    suspend fun pages(): List<BudgetPage> = call { api.pages() }.also { cache.write("pages", it) }

    suspend fun page(id: Int): BudgetPageDetail = call { api.page(id) }

    suspend fun createPage(request: PageCreateRequest): BudgetPageDetail = mutation { api.createPage(request) }
    suspend fun deletePage(id: Int) = mutation { api.deletePage(id).orThrow() }

    suspend fun addExpense(request: ExpenseSaveRequest): Expense = mutation { api.addExpense(request) }
    suspend fun quickExpense(text: String): Expense = mutation { api.quickExpense(QuickEntryRequest(text)) }
    suspend fun updateExpense(id: Int, request: ExpenseSaveRequest): Expense = mutation { api.updateExpense(id, request) }
    suspend fun deleteExpense(id: Int) = mutation { api.deleteExpense(id).orThrow() }

    suspend fun addTodo(request: TodoSaveRequest): Todo = mutation { api.addTodo(request) }
    suspend fun completeTodo(id: Int): Todo = mutation { api.completeTodo(id) }

    suspend fun merchants(q: String?): List<Merchant> = call { api.merchants(q) }

    fun cachedMonthlyReport(year: Int): List<PeriodTotal>? = cache.read("report_month_$year")
    suspend fun monthlyReport(year: Int): List<PeriodTotal> = call { api.monthlyReport(year) }.also { cache.write("report_month_$year", it) }

    fun cachedYearlyReport(): List<PeriodTotal>? = cache.read("report_year")
    suspend fun yearlyReport(): List<PeriodTotal> = call { api.yearlyReport() }.also { cache.write("report_year", it) }

    private fun Response<Unit>.orThrow() {
        if (!isSuccessful) throw HttpException(this)
    }

    private suspend fun <T> mutation(block: suspend () -> T): T = call(block).also { onMutated() }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw ApiException(httpMessage(e), code = e.code())
    } catch (e: IOException) {
        throw ApiException("Can't reach the server. Showing saved data.", offline = true, notSent = neverReachedServer(e))
    } catch (e: SerializationException) {
        throw ApiException("Unexpected response from the server.")
    }

    /** No network / DNS / refused connection: the request certainly wasn't processed (unlike a timeout). */
    private fun neverReachedServer(e: IOException): Boolean =
        e is UnknownHostException || e is ConnectException || e is NoRouteToHostException

    private fun httpMessage(e: HttpException): String {
        if (e.code() == 401) return "This phone is no longer paired."
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val serverMessage = body?.let {
            runCatching {
                val obj = json.decodeFromString(JsonObject.serializer(), it)
                (obj["message"] ?: obj["error_description"])?.jsonPrimitive?.content
            }.getOrNull()
        }
        return serverMessage ?: "Server error (HTTP ${e.code()})."
    }
}
