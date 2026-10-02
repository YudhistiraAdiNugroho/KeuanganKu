package id.keuanganku.app.ui.screens.update

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import id.keuanganku.app.data.update.UpdateCheckResult
import id.keuanganku.app.data.update.UpdateChecker
import id.keuanganku.app.data.update.UpdateInfo
import id.keuanganku.app.ui.components.AppButton
import id.keuanganku.app.ui.components.AppCard
import id.keuanganku.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    fun doCheck() {
        scope.launch {
            checking = true
            statusMsg = null
            state = UpdateChecker.check()
            checking = false
        }
    }

    fun doDownload(info: UpdateInfo) {
        scope.launch {
            downloading = true
            progress = 0f
            statusMsg = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder().url(info.apkUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("Unduhan gagal: HTTP ${resp.code}")
                        val body = resp.body ?: throw Exception("Respons kosong")
                        val total = body.contentLength()
                        val outFile = File(ctx.cacheDir, "keuanganku-update.apk")
                        body.byteStream().use { input ->
                            outFile.outputStream().use { output ->
                                val buf = ByteArray(8192)
                                var downloaded = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n)
                                    downloaded += n
                                    if (total > 0) progress = downloaded.toFloat() / total
                                }
                            }
                        }
                        outFile
                    }
                }
                // Minta install via FileProvider
                val uri = FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Android 8+: sistem akan meminta izin "install unknown apps" sekali saat pertama install.
                ctx.startActivity(intent)
                statusMsg = "Pemasang membuka — setujui instalasi untuk menyelesaikan pembaruan."
            } catch (e: Exception) {
                statusMsg = "Gagal mengunduh: ${e.message}"
            }
            downloading = false
        }
    }

    // Cek otomatis saat layar dibuka
    LaunchedEffect(Unit) { doCheck() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
        }
        Text("Pembaruan Aplikasi", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold)

        AppCard {
            SectionTitle("Versi Terpasang")
            Text("v${UpdateChecker.currentVersionName()} (${UpdateChecker.currentVersionCode()})",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }

        AppCard {
            SectionTitle("Status Pembaruan")
            when {
                checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Memeriksa…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state is UpdateCheckResult.Available -> {
                    val info = (state as UpdateCheckResult.Available).info
                    Text("🎉 Versi baru tersedia: v${info.versionName}",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.changelog, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (downloading) {
                        LinearProgressIndicator(progress = { progress },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("Mengunduh… ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium)
                    } else {
                        AppButton("⬇️ Unduh & Install v${info.versionName}") { doDownload(info) }
                    }
                }
                state is UpdateCheckResult.UpToDate ->
                    Text("✅ Aplikasi sudah versi terbaru.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                state is UpdateCheckResult.NotConfigured ->
                    Text("⚠️ URL pembaruan belum dikonfigurasi.\nIsi update.url di local.properties lalu rebuild.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                state is UpdateCheckResult.Error ->
                    Text("❌ ${(state as UpdateCheckResult.Error).message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
            }
            statusMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { doCheck() }, enabled = !checking && !downloading,
                modifier = Modifier.fillMaxWidth()) {
                Text("🔄 Cek Ulang")
            }
        }

        Text("Tips: setiap rilis versi baru di GitHub, perbarui file version.json di repo agar aplikasi mendeteksi pembaruan.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
