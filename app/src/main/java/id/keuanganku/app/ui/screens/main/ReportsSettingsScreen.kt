package id.keuanganku.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.data.repo.OpResult
import id.keuanganku.app.data.update.UpdateChecker
import id.keuanganku.app.ui.components.*
import id.keuanganku.app.ui.theme.*
import id.keuanganku.app.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(repo: FinanceRepository) {
    val txns by repo.txns.collectAsState()
    val accounts by repo.accounts.collectAsState()
    var monthOffset by remember { mutableStateOf(0) }
    var accountFilter by remember { mutableStateOf<Long?>(null) }

    val ym = LocalDate.now().minusMonths(monthOffset.toLong())
    val monthKey = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val monthTx = txns.filter {
        it.date.startsWith(monthKey) && (accountFilter == null || it.accountId == accountFilter)
    }
    val income = monthTx.filter { it.type == "income" }.sumOf { it.amount }
    val expense = monthTx.filter { it.type == "expense" }.sumOf { it.amount }

    // Tren 6 bulan terakhir
    val trend = (5 downTo 0).map { off ->
        val m = LocalDate.now().minusMonths(off.toLong())
        val key = m.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val inc = txns.filter { it.date.startsWith(key) && it.type == "income" }.sumOf { it.amount }
        val exp = txns.filter { it.date.startsWith(key) && it.type == "expense" }.sumOf { it.amount }
        Triple(m.format(DateTimeFormatter.ofPattern("MMM", java.util.Locale("id"))), inc, exp)
    }
    val maxTrend = (trend.maxOfOrNull { maxOf(it.second, it.third) } ?: 1.0).coerceAtLeast(1.0)

    // Breakdown kategori pengeluaran
    val byCat = monthTx.filter { it.type == "expense" }
        .groupBy { it.categoryName.ifBlank { "Tanpa kategori" } }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList().sortedByDescending { it.second }
    val maxCat = (byCat.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Laporan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            // Navigasi bulan
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { monthOffset++ }) { Text("‹") }
                Text(monthName(monthKey), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = { if (monthOffset > 0) monthOffset-- }) { Text("›") }
            }
            Spacer(Modifier.height(8.dp))
            DropdownField("Filter akun", listOf("Semua akun") + accounts.map { it.name },
                accountFilter?.let { accounts.indexOfFirst { a -> a.id == it } + 1 } ?: 0) {
                accountFilter = if (it == 0) null else accounts.getOrNull(it - 1)?.id
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Pemasukan", formatRp(income), IncomeGreen, Modifier.weight(1f))
                StatCard("Pengeluaran", formatRp(expense), ExpenseRed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatCard("Sisa Saldo", formatRp(income - expense),
                if (income - expense >= 0) Brand700 else ExpenseRed, Modifier.fillMaxWidth())
        }

        item {
            SectionTitle("Tren 6 bulan")
            AppCard {
                Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    trend.forEach { (label, inc, exp) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.width(10.dp)
                                    .height((110 * inc / maxTrend).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(3.dp)).background(IncomeGreen))
                                Box(Modifier.width(10.dp)
                                    .height((110 * exp / maxTrend).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(3.dp)).background(ExpenseRed))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(IncomeGreen, "Pemasukan"); LegendDot(ExpenseRed, "Pengeluaran")
                }
            }
        }

        item {
            SectionTitle("Pengeluaran per kategori")
            if (byCat.isEmpty()) EmptyState("📊", "Belum ada data", "Catat pengeluaran bulan ini dulu")
            else AppCard {
                byCat.forEach { (name, total) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(formatRp(total), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (total / maxCat).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Brand600, trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, color = accent,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(
    repo: FinanceRepository,
    onLogout: () -> Unit,
    onOpenSection: (String) -> Unit,
    onOpenUpdate: () -> Unit
) {
    val categories by repo.categories.collectAsState()
    val syncing by repo.syncing.collectAsState()
    val scope = rememberCoroutineScope()
    var showCatForm by remember { mutableStateOf(false) }
    var catType by remember { mutableStateOf("expense") }
    var confirmLogout by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pengaturan", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
        }
        item {
            AppCard {
                SectionTitle("Fitur")
                ClickableRow("🏦", "Akun", "Kelola dompet, bank & e-wallet") { onOpenSection("akun") }
                ClickableRow("🎯", "Anggaran", "Batas belanja per kategori") { onOpenSection("anggaran") }
                ClickableRow("🔁", "Berulang", "Jadwal transaksi otomatis") { onOpenSection("berulang") }
                ClickableRow("🐷", "Tabungan", "Target menabung") { onOpenSection("tabungan") }
            }
        }
        item {
            AppCard {
                SectionTitle("Sinkronisasi")
                Text("Data tersimpan offline dan dikirim otomatis saat online.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                AppButton(if (syncing) "Menyinkronkan…" else "Sinkronkan sekarang", {
                    scope.launch {
                        syncMsg = null
                        val r = repo.syncPending()
                        syncMsg = if (r is OpResult.Ok) "Sinkronisasi selesai ✓"
                        else (r as OpResult.Err).message
                    }
                }, loading = syncing)
                syncMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Brand700)
                }
            }
        }
        item {
            AppCard {
                SectionTitle("Kategori") {
                    TextButton(onClick = { showCatForm = true }) { Text("+ Tambah") }
                }
                ChipRow(listOf("Pengeluaran", "Pemasukan"),
                    if (catType == "income") "Pemasukan" else "Pengeluaran") {
                    catType = if (it == "Pemasukan") "income" else "expense"
                }
                Spacer(Modifier.height(8.dp))
                categories.filter { it.type == catType }.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)) {
                        IconCircle(c.icon, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, modifier = Modifier.weight(1f))
                        var del by remember { mutableStateOf(false) }
                        TextButton(onClick = { del = true }) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (del) AlertDialog(
                            onDismissRequest = { del = false },
                            title = { Text("Hapus kategori?") },
                            text = { Text("Transaksi & jadwal yang memakai kategori ini jadi \"Tanpa kategori\".") },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch { repo.deleteCategory(c.id); del = false }
                                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { del = false }) { Text("Batal") } }
                        )
                    }
                }
            }
        }
        item {
            AppCard {
                ClickableRow("🔄", "Cek Pembaruan", "Periksa versi terbaru aplikasi") { onOpenUpdate() }
                ClickableRow("🚪", "Keluar", "Hapus sesi di perangkat ini") { confirmLogout = true }
                Spacer(Modifier.height(4.dp))
                Text("KeuanganKu v${UpdateChecker.currentVersionName()} • data tersimpan di Supabase milikmu",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCatForm) CategoryFormDialog(repo, catType) { showCatForm = false }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Keluar dari akun?") },
        confirmButton = {
            TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Keluar") }
        },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } }
    )
}

@Composable
fun CategoryFormDialog(repo: FinanceRepository, type: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(if (type == "income") "💰" else "🛒") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val icons = if (type == "income")
        listOf("💰", "💼", "🎁", "📈", "🏠", "💻")
    else
        listOf("🛒", "🍔", "🚗", "🏠", "💡", "🎮", "👕", "🏥", "📚", "✈️", "📦", "💸")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kategori baru", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                AppTextField(name, { name = it }, "Nama kategori")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons.size) { i ->
                        FilterChip(selected = icon == icons[i],
                            onClick = { icon = icons[i] }, label = { Text(icons[i]) })
                    }
                }
                ErrorText(error)
                AppButton("Simpan", {
                    if (name.isBlank()) { error = "Nama wajib diisi"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = repo.addCategory(name, type, icon)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}
