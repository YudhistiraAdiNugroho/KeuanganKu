package id.keuanganku.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------- Auth (GoTrue) ----------
@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class OtpRequest(
    val email: String,
    @SerialName("create_user") val createUser: Boolean = true
)

@Serializable
data class VerifyRequest(val email: String, val token: String, val type: String)

@Serializable
data class RecoverRequest(val email: String)

@Serializable
data class UpdateUserRequest(val password: String, val data: Map<String, String>? = null)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class SessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUser
)

// ---------- PostgREST rows ----------
@Serializable
data class AccountDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    val type: String = "cash",
    val balance: Double = 0.0,
    val icon: String = "\uD83D\uDCB0"
)

@Serializable
data class CategoryDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    val type: String = "expense", // expense | income
    val icon: String = "\uD83D\uDCCC"
)

@Serializable
data class TransactionDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("account_id") val accountId: Long,
    @SerialName("to_account_id") val toAccountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val type: String, // income | expense | transfer
    val amount: Double,
    val date: String, // yyyy-MM-dd
    val note: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class BudgetDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Long,
    val amount: Double,
    val month: String, // yyyy-MM
    val year: Int
)

@Serializable
data class RecurringDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("account_id") val accountId: Long,
    @SerialName("category_id") val categoryId: Long? = null,
    val type: String,
    val amount: Double,
    val note: String? = null,
    val frequency: String = "monthly", // daily | weekly | monthly | yearly
    @SerialName("next_date") val nextDate: String,
    val active: Boolean = true
)

@Serializable
data class SavingGoalDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    val icon: String = "\uD83D\uDCB0"
)

// ---------- 9Router (OpenAI-compatible) ----------
@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: Map<String, String> = mapOf("type" to "json_object"),
    val temperature: Double = 0.1
)

@Serializable
data class ChatChoice(val message: ChatMessage)

@Serializable
data class ChatResponse(val choices: List<ChatChoice>)

@Serializable
data class ReceiptItem(val name: String, val price: Double = 0.0, val qty: Double = 1.0)

@Serializable
data class ReceiptParsed(
    val merchant: String = "",
    val date: String = "", // yyyy-MM-dd
    val total: Double = 0.0,
    val currency: String = "IDR",
    val items: List<ReceiptItem> = emptyList()
)
