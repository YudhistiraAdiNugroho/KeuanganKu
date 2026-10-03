package id.keuanganku.app.data.remote

import id.keuanganku.app.BuildConfig
import id.keuanganku.app.data.remote.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*

/**
 * GoTrue (Supabase Auth) endpoints.
 * OTP daftar & lupa password dikirim via custom SMTP Brevo yang dikonfigurasi
 * di dashboard Supabase (Authentication > SMTP Settings).
 */
interface AuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(@Body req: SignUpRequest): AuthUser

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body req: SignInRequest): SessionResponse

    @POST("auth/v1/otp")
    suspend fun sendOtp(@Body req: OtpRequest): okhttp3.ResponseBody

    @POST("auth/v1/verify")
    suspend fun verifyOtp(@Body req: VerifyRequest): SessionResponse

    @POST("auth/v1/recover")
    suspend fun recover(@Body req: RecoverRequest): okhttp3.ResponseBody

    @PUT("auth/v1/user")
    suspend fun updateUser(
        @Header("Authorization") bearer: String,
        @Body req: UpdateUserRequest
    ): AuthUser

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(@Body req: RefreshRequest): SessionResponse

    @POST("auth/v1/logout")
    suspend fun logout()
}

/**
 * PostgREST — CRUD tiap tabel. RLS di Supabase memastikan user_id terisolasi.
 */
interface RestApi {
    // ---- accounts ----
    @GET("rest/v1/accounts")
    suspend fun getAccounts(
        @Query("select") select: String = "*",
        @Query("order") order: String = "id.asc"
    ): List<AccountDto>

    @POST("rest/v1/accounts")
    suspend fun createAccount(@Body a: AccountDto): List<AccountDto>

    @PATCH("rest/v1/accounts")
    suspend fun updateAccount(@Query("id") id: String, @Body a: JsonObject): List<AccountDto>

    @DELETE("rest/v1/accounts")
    suspend fun deleteAccount(@Query("id") id: String): JsonArray

    // ---- categories ----
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): List<CategoryDto>

    @POST("rest/v1/categories")
    suspend fun createCategory(@Body c: CategoryDto): List<CategoryDto>

    @DELETE("rest/v1/categories")
    suspend fun deleteCategory(@Query("id") id: String): JsonArray

    // ---- transactions ----
    @GET("rest/v1/transactions")
    suspend fun getTransactions(
        @Query("select") select: String = "*",
        @Query("order") order: String = "date.desc,id.desc",
        @Query("limit") limit: Int = 500
    ): List<TransactionDto>

    @POST("rest/v1/transactions")
    suspend fun createTransaction(@Body t: TransactionDto): List<TransactionDto>

    @PATCH("rest/v1/transactions")
    suspend fun updateTransaction(@Query("id") id: String, @Body t: JsonObject): List<TransactionDto>

    @DELETE("rest/v1/transactions")
    suspend fun deleteTransaction(@Query("id") id: String): JsonArray

    // ---- budgets ----
    @GET("rest/v1/budgets")
    suspend fun getBudgets(@Query("select") select: String = "*"): List<BudgetDto>

    @POST("rest/v1/budgets")
    suspend fun upsertBudget(@Body b: BudgetDto, @Header("Prefer") prefer: String = "resolution=merge-duplicates"): List<BudgetDto>

    @DELETE("rest/v1/budgets")
    suspend fun deleteBudget(@Query("id") id: String): JsonArray

    // ---- recurring ----
    @GET("rest/v1/recurring_transactions")
    suspend fun getRecurring(@Query("select") select: String = "*"): List<RecurringDto>

    @POST("rest/v1/recurring_transactions")
    suspend fun createRecurring(@Body r: RecurringDto): List<RecurringDto>

    @PATCH("rest/v1/recurring_transactions")
    suspend fun updateRecurring(@Query("id") id: String, @Body r: JsonObject): List<RecurringDto>

    @DELETE("rest/v1/recurring_transactions")
    suspend fun deleteRecurring(@Query("id") id: String): JsonArray

    // ---- saving goals ----
    @GET("rest/v1/saving_goals")
    suspend fun getSavingGoals(@Query("select") select: String = "*"): List<SavingGoalDto>

    @POST("rest/v1/saving_goals")
    suspend fun createSavingGoal(@Body s: SavingGoalDto): List<SavingGoalDto>

    @PATCH("rest/v1/saving_goals")
    suspend fun updateSavingGoal(@Query("id") id: String, @Body s: JsonObject): List<SavingGoalDto>

    @DELETE("rest/v1/saving_goals")
    suspend fun deleteSavingGoal(@Query("id") id: String): JsonArray
}

/** Storage privat untuk foto struk. */
interface StorageApi {
    @PUT("storage/v1/object/receipts/{path}")
    suspend fun uploadReceipt(
        @Path(value = "path", encoded = true) path: String,
        @Body body: okhttp3.RequestBody // media type image/jpeg dari RequestBody
    )

    @POST("storage/v1/object/sign/receipts/{path}")
    suspend fun signReceipt(
        @Path(value = "path", encoded = true) path: String,
        @Body body: JsonObject
    ): Map<String, String>
}

/** 9Router — OpenAI-compatible chat completions untuk penstruktur hasil OCR. */
interface NineRouterApi {
    @POST("chat/completions")
    suspend fun chat(@Body req: ChatRequest): ChatResponse
}

object NetworkModule {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun baseClient(authToken: () -> String?): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                // Jangan timpa Content-Type yang sudah diset (mis. image/jpeg saat upload)
                if (original.header("Content-Type") == null) {
                    builder.header("Content-Type", "application/json")
                }
                // Gabung dengan Prefer eksplisit (mis. merge-duplicates untuk upsert)
                val prefer = original.header("Prefer")
                builder.header("Prefer", if (prefer != null) "$prefer,return=representation" else "return=representation")
                // Jangan duplikat Authorization bila sudah diset eksplisit via @Header
                if (original.header("Authorization") == null) {
                    authToken()?.let { builder.header("Authorization", "Bearer $it") }
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()
    }

    fun supabase(authToken: () -> String?): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
            .client(baseClient(authToken))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun nineRouter(): Retrofit {
        val contentType = "application/json".toMediaType()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.NINEROUTER_KEY}")
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.NINEROUTER_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
