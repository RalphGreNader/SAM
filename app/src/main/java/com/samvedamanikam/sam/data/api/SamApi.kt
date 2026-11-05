package com.samvedamanikam.sam.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Helios api/sam endpoints (policy "app:sam"). Auth headers are added by DeviceAuthClient. */
interface SamApi {
    @GET("api/sam/dashboard")
    suspend fun dashboard(): Dashboard

    @GET("api/sam/categories")
    suspend fun categories(): List<Category>

    @GET("api/sam/budget/pages")
    suspend fun pages(): List<BudgetPage>

    /** 404 when there is no active page. */
    @GET("api/sam/budget/pages/current")
    suspend fun currentPage(): Response<BudgetPageDetail>

    @GET("api/sam/budget/pages/{id}")
    suspend fun page(@Path("id") id: Int): BudgetPageDetail

    @POST("api/sam/budget/pages")
    suspend fun createPage(@Body body: PageCreateRequest): BudgetPageDetail

    @DELETE("api/sam/budget/pages/{id}")
    suspend fun deletePage(@Path("id") id: Int): Response<Unit>

    @POST("api/sam/budget/expenses")
    suspend fun addExpense(@Body body: ExpenseSaveRequest): Expense

    @POST("api/sam/budget/expenses/quick")
    suspend fun quickExpense(@Body body: QuickEntryRequest): Expense

    @PUT("api/sam/budget/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: Int, @Body body: ExpenseSaveRequest): Expense

    @DELETE("api/sam/budget/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Int): Response<Unit>

    @GET("api/sam/budget/merchants")
    suspend fun merchants(@Query("q") q: String?): List<Merchant>

    @GET("api/sam/budget/reports/monthly")
    suspend fun monthlyReport(@Query("year") year: Int): List<PeriodTotal>

    @GET("api/sam/budget/reports/yearly")
    suspend fun yearlyReport(): List<PeriodTotal>
}
