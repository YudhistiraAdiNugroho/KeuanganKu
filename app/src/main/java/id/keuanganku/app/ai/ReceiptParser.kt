package id.keuanganku.app.ai

import id.keuanganku.app.BuildConfig
import id.keuanganku.app.data.remote.NineRouterApi
import id.keuanganku.app.data.remote.dto.*
import id.keuanganku.app.data.repo.OpResult
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Mengirim teks OCR ke 9Router (OpenAI-compatible) untuk diubah menjadi
 * struktur {merchant, date, total, items[], currency}.
 * Dipanggil langsung dari HP — teks struk tidak lewat server perantara.
 */
class ReceiptParser(private val api: NineRouterApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parse(ocrText: String): OpResult<ReceiptParsed> {
        if (ocrText.isBlank()) return OpResult.Err("Tidak ada teks yang terbaca dari foto. Coba foto lebih jelas.")
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val system = """
            Kamu adalah pengekstrak data struk belanja Indonesia. Dari teks OCR berikut, kembalikan HANYA JSON valid (tanpa markdown) dengan skema:
            {"merchant": string, "date": "yyyy-MM-dd", "total": number, "currency": "IDR", "items": [{"name": string, "price": number, "qty": number}]}
            Aturan: total = angka GRAND TOTAL / TOTAL BAYAR (bukan subtotal, bukan kembalian). Tanggal format yyyy-MM-dd; jika tidak jelas pakai $today. Harga tanpa titik/koma ribuan. Jika ragu, kosongkan string / nolkan angka.
        """.trimIndent()
        return try {
            val res = api.chat(
                ChatRequest(
                    model = BuildConfig.NINEROUTER_MODEL.ifBlank { "default" },
                    messages = listOf(
                        ChatMessage("system", system),
                        ChatMessage("user", ocrText.take(6000))
                    )
                )
            )
            val raw = res.choices.firstOrNull()?.message?.content?.trim()
                ?: return OpResult.Err("AI tidak mengembalikan jawaban.")
            val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = json.decodeFromString<ReceiptParsed>(cleaned)
            OpResult.Ok(parsed.copy(total = parsed.total.coerceAtLeast(0.0)))
        } catch (e: Exception) {
            OpResult.Err("Gagal memproses dengan AI: ${e.message ?: "coba lagi"}")
        }
    }
}
