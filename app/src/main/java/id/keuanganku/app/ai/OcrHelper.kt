package id.keuanganku.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Hasil OCR: teks mentah + gambar terkompresi siap kirim/upload. */
data class OcrResult(val text: String, val jpegBytes: ByteArray)

/**
 * Baca teks dari foto struk memakai ML Kit (on-device, tanpa kuota).
 * Gambar dikompresi ke ~150-300 KB dan diputar sesuai EXIF.
 */
object OcrHelper {
    suspend fun recognize(context: Context, uri: Uri, maxDim: Int = 1600): OcrResult {
        val bytes = loadCompressedJpeg(context, uri, maxDim)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val visionText = recognizer.process(image).await()
            OcrResult(visionText.text.trim(), bytes)
        } finally {
            recognizer.close()
        }
    }

    /** Kompresi JPEG adaptif: target 150-300 KB. */
    fun loadCompressedJpeg(context: Context, uri: Uri, maxDim: Int = 1600): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Tidak bisa membuka gambar")
        val raw = input.use { it.readBytes() }
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IllegalArgumentException("Format gambar tidak didukung")

        // Putar sesuai orientasi EXIF (foto kamera sering miring)
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orient) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                if (!matrix.isIdentity) {
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
            }
        } catch (_: Exception) {}

        // Downscale jika terlalu besar
        val scale = max(bitmap.width, bitmap.height).toFloat() / maxDim
        if (scale > 1f) {
            val w = (bitmap.width / scale).toInt()
            val h = (bitmap.height / scale).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        }

        // Cari kualitas JPEG agar ukuran 150-300 KB
        var quality = 85
        var out = jpeg(bitmap, quality)
        while (out.size > 300 * 1024 && quality > 40) {
            quality -= 10
            out = jpeg(bitmap, quality)
        }
        if (out.size < 40 * 1024 && quality < 90) {
            // Terlalu kecil (teks bisa kabur) — naikkan sedikit
            out = jpeg(bitmap, 85)
        }
        return out
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }
}
