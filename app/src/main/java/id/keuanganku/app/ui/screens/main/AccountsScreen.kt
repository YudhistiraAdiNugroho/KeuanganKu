package id.keuanganku.app.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

val ACCOUNT_ICONS = listOf("💵", "🏦", "💳", "📱", "👛", "💰")
val ACCOUNT_TYPES = mapOf("cash" to "Tunai", "bank" to "Bank", "ewallet" to "E-Wallet", "other" to "Lainnya")

@Composable
fun AccountsScreen(repo: FinanceRepository) {
    val accounts by repo.accounts.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<id.keuanganku.app.data.repo.Account?>(null) }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showForm = true }, containerColor = Brand600) {
                Icon(Icons.Filled.Add, "Tambah akun", tint = Color.White)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Akun", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Total ${formatRp(accounts.sumOf { it.balance })} • ${accounts.size} akun",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (accounts.isEmpty()) EmptyState("🏦", "Belum ada akun", "Tambah akun pertamamu")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts, key = { it.id }) { a ->
                    Card(shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(a.icon)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.SemiBold)
                                Text(ACCOUNT_TYPES[a.type] ?: a.type,
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRp(a.balance), fontWeight = FontWeight.Bold,
                                color = if (a.balance < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editing = a; showForm = true }) { Text("Ubah") }
                            var del by remember { mutableStateOf(false) }
                            TextButton(onClick = { del = true }) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error)
                            }
                            if (del) AlertDialog(
                                onDismissRequest = { del = false },
                                title = { Text("Hapus akun?") },
                                text = { Text("Akun yang masih dipakai transaksi tidak bisa dihapus.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            when (val r = repo.deleteAccount(a.id)) {
                                                is OpResult.Err -> msg = r.message
                                                else -> {}
                                            }
                                            del = false
                                        }
                                    }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { del = false }) { Text("Batal") } }
                            )
                        }
                    }
                }
            }
            msg?.let { ErrorText(it) }
        }
    }
    if (showForm) AccountFormDialog(repo, editing) { showForm = false; editing = null }
}

@Composable
fun AccountFormDialog(repo: FinanceRepository, editing: id.keuanganku.app.data.repo.Account?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var type by remember { mutableStateOf(editing?.type ?: "cash") }
    var balance by remember { mutableStateOf(editing?.balance?.toLong()?.toString() ?: "0") }
    var icon by remember { mutableStateOf(editing?.icon ?: "💵") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (editing != null) "Ubah akun" else "Akun baru",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppTextField(name, { name = it }, "Nama akun")
                ChipRow(ACCOUNT_TYPES.values.toList(), ACCOUNT_TYPES[type] ?: "") {
                    type = ACCOUNT_TYPES.entries.first { e -> e.value == it }.key
                }
                AppTextField(balance, { balance = it.filter(Char::isDigit) }, "Saldo awal (Rp)",
                    keyboardType = KeyboardType.Number)
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ACCOUNT_ICONS.forEach { ic ->
                        FilterChip(selected = icon == ic, onClick = { icon = ic }, label = { Text(ic) })
                    }
                }
                ErrorText(error)
                AppButton("Simpan", {
                    if (name.isBlank()) { error = "Nama wajib diisi"; return@AppButton }
                    saving = true
                    scope.launch {
                        val r = if (editing != null)
                            repo.updateAccount(editing.id, name, type, balance.toDoubleOrNull() ?: 0.0, icon)
                        else repo.addAccount(name, type, balance.toDoubleOrNull() ?: 0.0, icon)
                        saving = false
                        if (r is OpResult.Ok) onDismiss() else error = (r as OpResult.Err).message
                    }
                }, loading = saving)
            }
        }
    }
}
