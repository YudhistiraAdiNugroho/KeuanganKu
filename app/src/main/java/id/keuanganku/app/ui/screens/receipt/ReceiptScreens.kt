package id.keuanganku.app.ui.screens.receipt

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.keuanganku.app.data.remote.dto.ReceiptParsed
import id.keuanganku.app.ui.components.*
import id.keuanganku.app.ui.screens.main.DropdownField
import id.keuanganku.app.ui.theme.*
import id.keuanganku.app.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCaptureScreen(vm: ReceiptViewModel, onDone: (id.keuanganku.app.ui.screens.main.PrefillTxn) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var showCamera by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.processImage(it) }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCamera = true
        } else {
            vm.setError("Izin kamera diperlukan untuk mengambil foto struk.")
        }
    }
    val galleryPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { _ -> galleryLauncher.launch("image/*") }

    DisposableEffect(Unit) { onDispose { vm.reset() } }

    // Hasil akhir -> teruskan ke form transaksi
    LaunchedEffect(state) {
        if (state is ReceiptUiState.Done) {
            val prefill = (state as ReceiptUiState.Done).prefill
            vm.reset()
            onDone(prefill)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pindai Struk") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Kembali") } })
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                is ReceiptUiState.Idle, is ReceiptUiState.Error -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧾", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("Foto struk belanja", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Teks dibaca di HP, lalu AI menyusun datanya.\nKamu tetap cek & koreksi sebelum disimpan.",
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                        if (s is ReceiptUiState.Error) {
                            Spacer(Modifier.height(8.dp))
                            ErrorText(s.message)
                        }
                        Spacer(Modifier.height(24.dp))
                        AppButton("📷  Ambil foto", {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        })
                        if (showCamera) {
                            CameraScreen(
                                onPhotoTaken = { uri ->
                                    showCamera = false
                                    vm.processImage(uri)
                                },
                                onClose = { showCamera = false }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) galleryLauncher.launch("image/*")
                                else galleryPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pilih dari galeri")
                        }
                    }
                }
                is ReceiptUiState.Reading -> BusyState("Membaca teks struk…", "OCR berjalan di HP (tanpa internet)")
                is ReceiptUiState.Parsing -> BusyState("Menyusun data…", "AI membaca hasil pindaian")
                is ReceiptUiState.Uploading -> BusyState("Mengunggah foto…", "Menyimpan ke penyimpanan privatmu")
                is ReceiptUiState.Confirm -> ReceiptConfirmView(
                    parsed = s.parsed,
                    vm = vm,
                    onCancel = { vm.reset() }
                )
                is ReceiptUiState.Done -> {}
            }
        }
    }
}

@Composable
private fun BusyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Brand600, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReceiptConfirmView(parsed: ReceiptParsed, vm: ReceiptViewModel, onCancel: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    var merchant by remember(parsed) { mutableStateOf(parsed.merchant) }
    var date by remember(parsed) { mutableStateOf(parsed.date.ifBlank { todayStr() }) }
    var total by remember(parsed) { mutableStateOf(if (parsed.total > 0) parsed.total.toLong().toString() else "") }
    var accIdx by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Cek hasil pindaian", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Koreksi bila ada yang salah sebelum disimpan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        AppCard {
            SectionTitle("Data struk")
            AppTextField(merchant, { merchant = it }, "Toko/merchant")
            Spacer(Modifier.height(8.dp))
            AppTextField(date, { date = it }, "Tanggal (yyyy-MM-dd)")
            Spacer(Modifier.height(8.dp))
            AppTextField(total, { total = it.filter(Char::isDigit) }, "Total bayar (Rp)",
                keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            DropdownField("Akun", accounts.map { it.name }, accIdx) { accIdx = it }
        }

        if (parsed.items.isNotEmpty()) {
            AppCard {
                SectionTitle("Item (${parsed.items.size})")
                parsed.items.take(10).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(item.name.ifBlank { "Item" }, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("×${item.qty.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(formatRp(item.price), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                if (parsed.items.size > 10)
                    Text("…dan ${parsed.items.size - 10} item lainnya",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AppButton("Simpan sebagai pengeluaran", {
            vm.confirm(parsed, merchant, date, total.toDoubleOrNull() ?: 0.0,
                accounts.getOrNull(accIdx ?: -1)?.id)
        }, enabled = (total.toDoubleOrNull() ?: 0.0) > 0)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Text("Ulangi foto")
        }
        Spacer(Modifier.height(16.dp))
    }
}
