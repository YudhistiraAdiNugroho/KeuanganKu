package id.keuanganku.app.data.update

import id.keuanganku.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Isi file version.json yang di-host di GitHub repo. */
@Serializable
data class UpdateInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    val changelog: String = ""
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NotConfigured : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Ambil version.json dari GitHub dan bandingkan dengan versi terpasang. */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val url = BuildConfig.UPDATE_URL.trim()
        if (url.isBlank()) return@withContext UpdateCheckResult.NotConfigured
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("Server: HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val info = json.decodeFromString<UpdateInfo>(body)
                return@withContext if (info.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateCheckResult.Available(info)
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Gagal memeriksa pembaruan")
        }
    }

    fun currentVersionName(): String = BuildConfig.VERSION_NAME
    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE
}
