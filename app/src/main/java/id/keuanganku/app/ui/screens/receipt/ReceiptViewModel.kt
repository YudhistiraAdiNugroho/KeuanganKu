package id.keuanganku.app.ui.screens.receipt

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.keuanganku.app.KeuanganKuApp
import id.keuanganku.app.ai.OcrHelper
import id.keuanganku.app.data.remote.dto.ReceiptParsed
import id.keuanganku.app.ai.ReceiptParser
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.data.repo.OpResult
import id.keuanganku.app.data.repo.isOnline
import id.keuanganku.app.ui.screens.main.PrefillTxn
import id.keuanganku.app.util.todayStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface ReceiptUiState {
    data object Idle : ReceiptUiState
    data object Reading : ReceiptUiState          // OCR berjalan
    data object Parsing : ReceiptUiState          // AI 9Router berjalan
    data class Confirm(val parsed: ReceiptParsed, val ocrText: String) : ReceiptUiState
    data class Error(val message: String) : ReceiptUiState
    data object Uploading : ReceiptUiState
    data class Done(val prefill: PrefillTxn) : ReceiptUiState
}

class ReceiptViewModel(app: KeuanganKuApp) : ViewModel() {
    private val context: Context = app.applicationContext
    private val repo: FinanceRepository = app.financeRepo
    private val parser = ReceiptParser(app.nineRouterApi)
    private val storage = app.storageApi
    private val session = app.session

    private val _state = MutableStateFlow<ReceiptUiState>(ReceiptUiState.Idle)
    val state: StateFlow<ReceiptUiState> = _state.asStateFlow()

    private var lastJpeg: ByteArray? = null
    private var lastOcrText: String = ""

    val accounts = repo.accounts

    fun reset() { _state.value = ReceiptUiState.Idle; lastJpeg = null }

    fun setError(msg: String) { _state.value = ReceiptUiState.Error(msg) }

    /** URI sementara untuk hasil foto kamera (FileProvider). */
    fun newCameraUri(): Uri {
        val file = File(context.cacheDir, "struk_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun processImage(uri: Uri) {
        viewModelScope.launch {
            _state.value = ReceiptUiState.Reading
            try {
                val ocr = withContext(Dispatchers.IO) { OcrHelper.recognize(context, uri) }
                lastJpeg = ocr.jpegBytes
                lastOcrText = ocr.text
                if (ocr.text.isBlank()) {
                    _state.value = ReceiptUiState.Error("Tidak ada teks terbaca. Coba foto lebih terang & fokus.")
                    return@launch
                }
                _state.value = ReceiptUiState.Parsing
                when (val r = parser.parse(ocr.text)) {
                    is OpResult.Ok -> _state.value = ReceiptUiState.Confirm(r.data, ocr.text)
                    is OpResult.Err -> _state.value = ReceiptUiState.Error(r.message)
                }
            } catch (e: Exception) {
                _state.value = ReceiptUiState.Error("Gagal membaca gambar: ${e.message ?: "coba lagi"}")
            }
        }
    }

    /** Upload foto ke Storage privat, lalu siapkan prefill transaksi. */
    fun confirm(parsed: ReceiptParsed, merchant: String, date: String, total: Double, accountId: Long?) {
        viewModelScope.launch {
            _state.value = ReceiptUiState.Uploading
            var receiptUrl: String? = null
            var localPath: String? = null
            try {
                val jpeg = lastJpeg
                if (jpeg != null && isOnline(context)) {
                    val uid = session.getUserId() ?: throw IllegalStateException("Sesi berakhir")
                    val ym = try {
                        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy/MM"))
                    } catch (_: Exception) {
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                    }
                    val path = "$uid/$ym/struk_${System.currentTimeMillis()}.jpg"
                    val body = jpeg.toRequestBody("image/jpeg".toMediaType())
                    storage.uploadReceipt(path, body)
                    // Simpan salinan lokal sementara sebagai bukti bila offline-nanti
                    val f = File(context.cacheDir, "struk_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(jpeg)
                    localPath = f.absolutePath
                    // Signed URL (berlaku 7 hari) untuk ditampilkan; path asli tetap di DB
                    receiptUrl = try {
                        val signed = storage.signReceipt(path, buildJsonObject { put("expiresIn", 604800) })
                        signed["signedURL"]
                    } catch (_: Exception) { null }
                    receiptUrl = receiptUrl ?: "storage:receipts/$path"
                } else if (jpeg != null) {
                    // Offline: simpan lokal saja, upload menyusul saat sinkron
                    val f = File(context.cacheDir, "struk_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(jpeg)
                    localPath = f.absolutePath
                }
            } catch (e: Exception) {
                // Upload gagal bukan fatal — transaksi tetap bisa disimpan tanpa foto
                val f = lastJpeg?.let {
                    File(context.cacheDir, "struk_${System.currentTimeMillis()}.jpg").also { file -> file.writeBytes(it) }
                }
                localPath = f?.absolutePath
            }
            _state.value = ReceiptUiState.Done(
                PrefillTxn(
                    type = "expense",
                    amount = total,
                    date = date,
                    note = merchant.ifBlank { "Struk belanja" },
                    accountId = accountId,
                    receiptUrl = receiptUrl,
                    receiptLocalPath = localPath
                )
            )
        }
    }
}
