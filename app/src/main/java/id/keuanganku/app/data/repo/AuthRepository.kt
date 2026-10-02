package id.keuanganku.app.data.repo

import id.keuanganku.app.data.remote.AuthApi
import id.keuanganku.app.data.remote.SessionManager
import id.keuanganku.app.data.remote.dto.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/** Hasil operasi yang bisa sukses/gagal dengan pesan ramah. */
sealed class OpResult<out T> {
    data class Ok<T>(val data: T) : OpResult<T>()
    data class Err(val message: String) : OpResult<Nothing>()
}

/** Ambil pesan error asli dari body respons GoTrue: {"msg"|"message"|"error": "..."} */
private fun serverMessage(e: HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
    obj["msg"]?.jsonPrimitive?.content
        ?: obj["message"]?.jsonPrimitive?.content
        ?: obj["error"]?.jsonPrimitive?.content
} catch (_: Exception) { null }

private fun codeMessage(code: Int, fallback: String) = when (code) {
    400, 422 -> "Data tidak valid. Periksa kembali."
    401 -> "Tidak diizinkan. Periksa konfigurasi API."
    429 -> "Terlalu banyak percobaan. Tunggu sebentar."
    in 500..599 -> "Server bermasalah. Coba lagi nanti."
    else -> fallback
}

fun Throwable.userMessage(fallback: String = "Terjadi kesalahan. Coba lagi."): String {
    if (this is HttpException) {
        val serverMsg = serverMessage(this)?.lowercase()
        if (serverMsg != null) {
            return when {
                "rate limit" in serverMsg || "too many requests" in serverMsg ->
                    "Terlalu banyak permintaan kode. Tunggu ±1 jam lalu coba lagi."
                "signup" in serverMsg && "disabled" in serverMsg ->
                    "Pendaftaran dimatikan di Supabase (Authentication → Settings)."
                "already registered" in serverMsg || "already exists" in serverMsg ->
                    "Email sudah terdaftar. Silakan masuk saja."
                "invalid" in serverMsg && "email" in serverMsg ->
                    "Format email tidak valid."
                "smtp" in serverMsg || "send" in serverMsg && "email" in serverMsg ->
                    "Gagal mengirim email. Periksa SMTP di dashboard Supabase."
                "invalid api key" in serverMsg || "apikey" in serverMsg ->
                    "API key salah. Periksa supabase.anonKey di local.properties."
                else -> serverMessage(this) ?: codeMessage(code(), fallback)
            }
        }
        return codeMessage(code(), fallback)
    }
    val m = message ?: return fallback
    return when {
        m.contains("Unable to resolve host", true) || m.contains("Failed to connect", true) ->
            "Tidak ada koneksi internet."
        else -> fallback
    }
}

class AuthRepository(
    private val api: AuthApi,
    private val session: SessionManager
) {
    suspend fun login(email: String, password: String): OpResult<Unit> = try {
        val s = api.signIn(SignInRequest(email.trim(), password))
        val name = s.user.userMetadata["name"]?.jsonPrimitive?.contentOrNull
            ?: s.user.email?.substringBefore("@") ?: "Pengguna"
        session.save(s.accessToken, s.refreshToken, s.user.id, s.user.email ?: email, name)
        OpResult.Ok(Unit)
    } catch (e: Exception) {
        OpResult.Err(e.userMessage("Login gagal. Periksa email & kata sandi."))
    }

    /** Langkah 1 daftar: kirim OTP ke email (via SMTP Brevo di Supabase). */
    suspend fun requestRegisterOtp(email: String): OpResult<Unit> = try {
        api.sendOtp(OtpRequest(email.trim(), createUser = true)).close()
        OpResult.Ok(Unit)
    } catch (e: Exception) {
        OpResult.Err(e.userMessage("Gagal mengirim kode OTP."))
    }

    /** Langkah 2 daftar: verifikasi OTP (tipe "email") lalu buat akun dengan password. */
    suspend fun verifyRegisterOtp(email: String, code: String, password: String, name: String): OpResult<Unit> = try {
        val s = api.verifyOtp(VerifyRequest(email.trim(), code.trim(), "email"))
        // Set password pada user yang baru terverifikasi
        api.updateUser("Bearer ${s.accessToken}", UpdateUserRequest(password))
        val displayName = name.ifBlank {
            s.user.userMetadata["name"]?.jsonPrimitive?.contentOrNull
                ?: s.user.email?.substringBefore("@") ?: "Pengguna"
        }
        session.save(s.accessToken, s.refreshToken, s.user.id, s.user.email ?: email, displayName)
        OpResult.Ok(Unit)
    } catch (e: Exception) {
        OpResult.Err(e.userMessage("Kode OTP salah atau kedaluwarsa."))
    }

    /** Lupa password: kirim OTP recovery. */
    suspend fun requestRecoveryOtp(email: String): OpResult<Unit> = try {
        api.recover(RecoverRequest(email.trim())).close()
        OpResult.Ok(Unit)
    } catch (e: Exception) {
        OpResult.Err(e.userMessage("Gagal mengirim kode pemulihan."))
    }

    /** Verifikasi OTP recovery lalu set password baru. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): OpResult<Unit> = try {
        val s = api.verifyOtp(VerifyRequest(email.trim(), code.trim(), "recovery"))
        api.updateUser("Bearer ${s.accessToken}", UpdateUserRequest(newPassword))
        OpResult.Ok(Unit)
    } catch (e: Exception) {
        OpResult.Err(e.userMessage("Kode salah/kedaluwarsa, atau password tidak valid."))
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) {}
        session.clear()
    }

    suspend fun refreshIfNeeded(): Boolean = try {
        val refresh = session.getRefresh() ?: return false
        val s = api.refresh(RefreshRequest(refresh))
        session.updateTokens(s.accessToken, s.refreshToken)
        true
    } catch (_: Exception) { false }
}
