package id.keuanganku.app.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.data.repo.OpResult
import id.keuanganku.app.ui.components.*
import id.keuanganku.app.ui.theme.*
import id.keuanganku.app.util.*
import kotlinx.coroutines.launch

@Composable
fun BudgetsScreen(repo: FinanceRepository) {
    val budgets by repo.budgets.collectAsState()
    val categories by repo.categories.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }, containerColor = Brand600) {
                Icon(Icons.Filled.Add, "Tambah anggaran", tint = Color.White)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Anggaran", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(monthName(currentMonthStr()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (budgets.isEmpty()) EmptyState("🎯", "Belum ada anggaran",
                "Atur batas belanja per kategori bulan ini")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(budgets, key = { it.id }) { b ->
                    BudgetRow(b.categoryIcon, b.categoryName, b.spent, b.amount)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        var del by remember { mutableStateOf(false) }
                        TextButton(onClick = { del = true }) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (del) AlertDialog(
                            onDismissRequest = { del = false },
                            title = { Text("Hapus anggaran?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch { repo.deleteBudget(b.id); del = false }
                                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { del = false }) { Text("Batal") } }
                        )
                    }
                }
            }
        }
    }
    if (showForm) BudgetFormDialog(repo, categories) { showForm = false }
}

@Composable
fun BudgetFormDialog(
    repo: FinanceRepository,
    categories: List<id.keuanganku.app.data.repo.Category>,
    onDismiss: () -> Unit
) {
    val expenseCats = categories.filter { it.type == "expense" }
    var catIdx by remember { mutableStateOf<Int?>(null) }
    var amount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Anggaran ${monthName(currentMonthStr())}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                DropdownField("Kategori", expenseCats.map { "${it.icon} ${it.name}" }, catIdx) { catIdx = it }
                AppTextField(amount, { amount = it.filter(Char::isDigit) }, "Batas (Rp)",
                    keyboardType = KeyboardType.Number)
                ErrorText(error)
                AppButton("Simpan", {
                    val c = expenseCats.getOrNull(catIdx ?: -1)
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (c == null) { error = "Pilih kategori"; return@AppButton }
                    if (amt <= 0) { error = "Nominal harus > 0"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = repo.upsertBudget(c.id, amt)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}

@Composable
fun RecurringScreen(repo: FinanceRepository) {
    val list by repo.recurring.collectAsState()
    val accounts by repo.accounts.collectAsState()
    val categories by repo.categories.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }, containerColor = Brand600) {
                Icon(Icons.Filled.Add, "Tambah jadwal", tint = Color.White)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Berulang", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Jadwal otomatis dibuat jadi transaksi saat jatuh tempo", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) EmptyState("🔁", "Belum ada jadwal",
                "Contoh: gaji tiap bulan, langganan tiap bulan")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { r ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.note?.ifBlank { null } ?: "Jadwal",
                                    fontWeight = FontWeight.SemiBold)
                                Text("${r.accountName} • ${freqLabel(r.frequency)} • berikut: ${prettyDate(r.nextDate)}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRp(r.amount), fontWeight = FontWeight.Bold,
                                color = if (r.type == "income") IncomeGreen else ExpenseRed)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.active) "Aktif" else "Nonaktif",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r.active) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            Switch(checked = r.active,
                                onCheckedChange = { scope.launch { repo.toggleRecurring(r.id, it) } })
                            var del by remember { mutableStateOf(false) }
                            TextButton(onClick = { del = true }) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error)
                            }
                            if (del) AlertDialog(
                                onDismissRequest = { del = false },
                                title = { Text("Hapus jadwal?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        scope.launch { repo.deleteRecurring(r.id); del = false }
                                    }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { del = false }) { Text("Batal") } }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showForm) RecurringFormDialog(repo, accounts, categories) { showForm = false }
}

fun freqLabel(f: String) = when (f) {
    "daily" -> "Harian"; "weekly" -> "Mingguan"; "yearly" -> "Tahunan"; else -> "Bulanan"
}

@Composable
fun RecurringFormDialog(
    repo: FinanceRepository,
    accounts: List<id.keuanganku.app.data.repo.Account>,
    categories: List<id.keuanganku.app.data.repo.Category>,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf("expense") }
    var amount by remember { mutableStateOf("") }
    var accIdx by remember { mutableStateOf<Int?>(null) }
    var catIdx by remember { mutableStateOf<Int?>(null) }
    var freq by remember { mutableStateOf("monthly") }
    var date by remember { mutableStateOf(todayStr()) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cats = categories.filter { it.type == if (type == "income") "income" else "expense" }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Jadwal baru", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ChipRow(listOf("Pengeluaran", "Pemasukan"), if (type == "income") "Pemasukan" else "Pengeluaran") {
                    type = if (it == "Pemasukan") "income" else "expense"
                }
                AppTextField(amount, { amount = it.filter(Char::isDigit) }, "Nominal (Rp)",
                    keyboardType = KeyboardType.Number)
                DropdownField("Akun", accounts.map { it.name }, accIdx) { accIdx = it }
                DropdownField("Kategori (opsional)",
                    listOf("Tanpa kategori") + cats.map { "${it.icon} ${it.name}" },
                    catIdx?.let { it + 1 } ?: 0) { catIdx = if (it == 0) null else it - 1 }
                ChipRow(listOf("Harian", "Mingguan", "Bulanan", "Tahunan"), freqLabel(freq)) {
                    freq = mapOf("Harian" to "daily", "Mingguan" to "weekly",
                        "Bulanan" to "monthly", "Tahunan" to "yearly")[it] ?: "monthly"
                }
                AppTextField(date, { date = it }, "Mulai tanggal (yyyy-MM-dd)")
                AppTextField(note, { note = it }, "Catatan (opsional)")
                ErrorText(error)
                AppButton("Simpan", {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val acc = accounts.getOrNull(accIdx ?: -1)
                    if (amt <= 0) { error = "Nominal harus > 0"; return@AppButton }
                    if (acc == null) { error = "Pilih akun"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = repo.addRecurring(acc.id, cats.getOrNull(catIdx ?: -1)?.id,
                            type, amt, note, freq, date)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}

@Composable
fun SavingsScreen(repo: FinanceRepository) {
    val goals by repo.goals.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<id.keuanganku.app.data.repo.SavingGoal?>(null) }
    var moveIsAdd by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }, containerColor = Brand600) {
                Icon(Icons.Filled.Add, "Tambah target", tint = Color.White)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Tabungan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (goals.isEmpty()) EmptyState("🐷", "Belum ada target",
                "Buat target: dana darurat, liburan, gadget baru…")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(goals, key = { it.id }) { g ->
                    val pct = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount).coerceIn(0.0, 1.0) else 0.0
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(g.icon)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, fontWeight = FontWeight.SemiBold)
                                Text("${(pct * 100).toInt()}% • ${formatRp(g.currentAmount)} / ${formatRp(g.targetAmount)}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            var del by remember { mutableStateOf(false) }
                            TextButton(onClick = { del = true }) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (del) AlertDialog(
                                onDismissRequest = { del = false },
                                title = { Text("Hapus target?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        scope.launch { repo.deleteSavingGoal(g.id); del = false }
                                    }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { del = false }) { Text("Batal") } }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Brand600, trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { moveTarget = g; moveIsAdd = true },
                                modifier = Modifier.weight(1f)) { Text("＋ Nabung") }
                            OutlinedButton(onClick = { moveTarget = g; moveIsAdd = false },
                                modifier = Modifier.weight(1f)) { Text("− Tarik") }
                        }
                    }
                }
            }
        }
    }
    if (showForm) SavingFormDialog(repo) { showForm = false }
    moveTarget?.let { g ->
        MoveSavingDialog(repo, g, moveIsAdd) { moveTarget = null }
    }
}

@Composable
fun SavingFormDialog(repo: FinanceRepository, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🐷") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val icons = listOf("🐷", "✈️", "🏠", "🚗", "💻", "🎓", "💍", "🏥")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Target baru", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppTextField(name, { name = it }, "Nama target")
                AppTextField(target, { target = it.filter(Char::isDigit) }, "Target (Rp)",
                    keyboardType = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.forEach { ic ->
                        FilterChip(selected = icon == ic, onClick = { icon = ic }, label = { Text(ic) })
                    }
                }
                ErrorText(error)
                AppButton("Simpan", {
                    val t = target.toDoubleOrNull() ?: 0.0
                    if (name.isBlank()) { error = "Nama wajib diisi"; return@AppButton }
                    if (t <= 0) { error = "Target harus > 0"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = repo.addSavingGoal(name, t, icon)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}

@Composable
fun MoveSavingDialog(
    repo: FinanceRepository,
    goal: id.keuanganku.app.data.repo.SavingGoal,
    isAdd: Boolean,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isAdd) "Nabung ke ${goal.name}" else "Tarik dari ${goal.name}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppTextField(amount, { amount = it.filter(Char::isDigit) }, "Nominal (Rp)",
                    keyboardType = KeyboardType.Number)
                ErrorText(error)
                AppButton("Simpan", {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (a <= 0) { error = "Nominal harus > 0"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = repo.moveSaving(goal.id, if (isAdd) a else -a)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}
