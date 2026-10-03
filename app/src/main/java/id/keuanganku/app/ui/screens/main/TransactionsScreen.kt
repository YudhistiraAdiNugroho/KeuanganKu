package id.keuanganku.app.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.ui.components.*
import id.keuanganku.app.ui.theme.*
import id.keuanganku.app.util.*
import kotlinx.coroutines.launch

@Composable
fun TransactionsScreen(repo: FinanceRepository) {
    val txns by repo.txns.collectAsState()
    var filter by remember { mutableStateOf("Semua") }
    var query by remember { mutableStateOf("") }
    var formTarget by remember { mutableStateOf<Long?>(null) } // null=tutup, -1=baru, -2=transfer baru, >0=edit
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val notifyOk: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val typeKey = when (filter) {
        "Pemasukan" -> "income"; "Pengeluaran" -> "expense"; "Transfer" -> "transfer"; else -> null
    }
    val filtered = txns.filter {
        (typeKey == null || it.type == typeKey) &&
        (query.isBlank() || (it.note ?: "").contains(query, true) ||
                it.categoryName.contains(query, true) || it.accountName.contains(query, true))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { formTarget = -1L }, containerColor = Brand600) {
                Icon(Icons.Filled.Add, "Tambah", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Transaksi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            AppTextField(query, { query = it }, "Cari transaksi…")
            Spacer(Modifier.height(8.dp))
            ChipRow(listOf("Semua", "Pemasukan", "Pengeluaran", "Transfer"), filter) { filter = it }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("🔍", "Tidak ada transaksi", "Coba ubah filter atau kata kunci")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        var confirmDel by remember { mutableStateOf(false) }
                        TxnRow(t, onClick = { formTarget = t.id })
                        if (confirmDel) {
                            AlertDialog(
                                onDismissRequest = { confirmDel = false },
                                title = { Text("Hapus transaksi?") },
                                text = { Text("Transaksi dihapus dan saldo dikembalikan.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            repo.deleteTransaction(t.id)
                                            confirmDel = false
                                            notifyOk("Transaksi berhasil dihapus")
                                        }
                                    }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Batal") } }
                            )
                        }
                    }
                }
            }
        }
    }

    // Form tambah/ubah (dipicu FAB & klik baris di layar ini)
    formTarget?.let { target ->
        val initialType = if (target == -2L) "transfer" else null
        val editId = if (target != -1L && target != -2L) target else null
        TransactionFormDialog(
            repo = repo,
            editingId = editId,
            initialType = initialType,
            onDismiss = { formTarget = null },
            onSuccess = notifyOk
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormDialog(
    repo: FinanceRepository,
    editingId: Long?,
    initialType: String? = null,
    prefill: PrefillTxn? = null,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit = {}
) {
    val accounts by repo.accounts.collectAsState()
    val categories by repo.categories.collectAsState()
    val txns by repo.txns.collectAsState()
    val scope = rememberCoroutineScope()

    val editing = editingId?.let { id -> txns.find { it.id == id } }
    var type by remember { mutableStateOf(editing?.type ?: prefill?.type ?: initialType ?: "expense") }
    var amount by remember { mutableStateOf(editing?.amount?.toLong()?.toString() ?: prefill?.amount?.toLong()?.toString() ?: "") }
    var accountId by remember { mutableStateOf(editing?.accountId ?: prefill?.accountId) }
    var toAccountId by remember { mutableStateOf(editing?.toAccountId) }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var date by remember { mutableStateOf(editing?.date ?: prefill?.date ?: todayStr()) }
    var note by remember { mutableStateOf(editing?.note ?: prefill?.note ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val typeLabel = mapOf("expense" to "Pengeluaran", "income" to "Pemasukan", "transfer" to "Transfer")
    val cats = categories.filter { it.type == if (type == "income") "income" else "expense" }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (editing != null) "Ubah transaksi" else "Transaksi baru",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                ChipRow(listOf("Pengeluaran", "Pemasukan", "Transfer"), typeLabel[type] ?: "") { label ->
                    type = typeLabel.entries.first { it.value == label }.key
                }

                AppTextField(amount, { amount = it.filter(Char::isDigit) }, "Nominal (Rp)",
                    keyboardType = KeyboardType.Number,
                    visualTransformation = ThousandSeparatorTransformation())

                DropdownField("Akun", accounts.map { it.name }, accounts.indexOfFirst { it.id == accountId }.takeIf { it >= 0 }) {
                    accountId = accounts.getOrNull(it)?.id
                }

                if (type == "transfer") {
                    DropdownField("Akun tujuan",
                        accounts.filter { it.id != accountId }.map { it.name },
                        accounts.filter { it.id != accountId }.indexOfFirst { it.id == toAccountId }.takeIf { it >= 0 }) {
                        toAccountId = accounts.filter { it.id != accountId }.getOrNull(it)?.id
                    }
                } else {
                    DropdownField("Kategori", listOf("Tanpa kategori") + cats.map { "${it.icon} ${it.name}" },
                        if (categoryId == null) 0 else cats.indexOfFirst { it.id == categoryId } + 1) {
                        categoryId = if (it == 0) null else cats.getOrNull(it - 1)?.id
                    }
                }

                var showDatePicker by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                    OutlinedTextField(
                        value = try {
                            prettyDate(date)
                        } catch (_: Exception) { date },
                        onValueChange = {},
                        label = { Text("Tanggal") },
                        readOnly = true,
                        trailingIcon = { Text("📅") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = try {
                            java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (_: Exception) { System.currentTimeMillis() }
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val ld = java.time.Instant.ofEpochMilli(millis)
                                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    date = ld.format(java.time.format.DateTimeFormatter.ISO_DATE)
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
                        }
                    ) { DatePicker(state = datePickerState) }
                }
                AppTextField(note, { note = it }, "Catatan (opsional)")

                ErrorText(error)

                AppButton(if (editing != null) "Simpan perubahan" else "Simpan", {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) { error = "Nominal harus lebih dari 0"; return@AppButton }
                    if (accountId == null) { error = "Pilih akun dulu"; return@AppButton }
                    if (type == "transfer" && (toAccountId == null || toAccountId == accountId)) {
                        error = "Pilih akun tujuan yang berbeda"; return@AppButton
                    }
                    saving = true; error = null
                    scope.launch {
                        val r = if (editing != null) {
                            repo.updateTransaction(editing.id, accountId!!, toAccountId, categoryId, type, amt, date, note)
                        } else {
                            repo.addTransaction(accountId!!, toAccountId, categoryId, type, amt, date, note,
                                prefill?.receiptUrl, prefill?.receiptLocalPath)
                        }
                        saving = false
                        when (r) {
                            is id.keuanganku.app.data.repo.OpResult.Ok -> {
                                onSuccess(if (editing != null) "Transaksi berhasil diubah" else "Transaksi berhasil ditambahkan")
                                onDismiss()
                            }
                            is id.keuanganku.app.data.repo.OpResult.Err -> error = r.message
                        }
                    }
                }, loading = saving)

                if (editing != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Hapus transaksi") }
                }
            }
        }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus transaksi?") },
            text = { Text("Saldo akun akan dikembalikan.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteTransaction(editing.id)
                        onSuccess("Transaksi berhasil dihapus")
                        onDismiss()
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

/** Prefill dari hasil pindai struk AI. */
data class PrefillTxn(
    val type: String = "expense",
    val amount: Double = 0.0,
    val date: String = todayStr(),
    val note: String? = null,
    val accountId: Long? = null,
    val receiptUrl: String? = null,
    val receiptLocalPath: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedIndex?.let { options.getOrNull(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}
