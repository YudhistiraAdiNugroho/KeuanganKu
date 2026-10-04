package id.keuanganku.app.ui.screens.main

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.ui.components.*
import kotlinx.coroutines.launch
import id.keuanganku.app.ui.theme.*
import id.keuanganku.app.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repo: FinanceRepository,
    onOpenTxForm: (Long?) -> Unit,
    onOpenSection: (String) -> Unit
) {
    val txns by repo.txns.collectAsState()
    val budgets by repo.budgets.collectAsState()
    val syncing by repo.syncing.collectAsState()

    // Auto-sync saat dashboard dibuka
    LaunchedEffect(Unit) {
        try { repo.syncPending() } catch (_: Exception) {}
    }

    // Tanpa sistem akun: total saldo = total pemasukan - total pengeluaran (semua waktu)
    val totalBalance = txns.filter { it.type == "income" }.sumOf { it.amount } -
            txns.filter { it.type == "expense" }.sumOf { it.amount }
    val monthKey = currentMonthStr()
    val monthTx = txns.filter { it.date.startsWith(monthKey) }
    val income = monthTx.filter { it.type == "income" }.sumOf { it.amount }
    val expense = monthTx.filter { it.type == "expense" }.sumOf { it.amount }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing || syncing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                repo.syncPending()
                isRefreshing = false
            }
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
    // Responsif: sesuaikan padding & ukuran dengan lebar layar
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 360.dp
        val hPad = if (compact) 12.dp else 16.dp
        val vSpace = if (compact) 8.dp else 12.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(hPad),
        verticalArrangement = Arrangement.spacedBy(vSpace)
    ) {
        item {
            // Sapaan personal
            val hour = java.time.LocalTime.now().hour
            val greeting = when (hour) {
                in 5..10 -> "Selamat pagi"
                in 11..14 -> "Selamat siang"
                in 15..17 -> "Selamat sore"
                else -> "Selamat malam"
            }
            var userName by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val raw = repo.getUserName()?.split(" ")?.firstOrNull() ?: ""
                userName = raw.replaceFirstChar { it.uppercase() }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (userName.isNotBlank()) "$greeting, $userName" else greeting,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    formatTanggalHariIni(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        item {
            // Kartu saldo dengan gradien
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(Brand700, Brand600)))
                    .padding(24.dp)
            ) {
                Column {
                    Column(modifier = Modifier.offset(y = (-8).dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Saldo", color = Color.White.copy(0.85f),
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (syncing) CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(formatRp(totalBalance), color = Color.White,
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MiniStat("Masuk", formatRp(income), IncomeGreen)
                        MiniStat("Keluar", formatRp(expense), Color.White)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("＋", "Pemasukan") { onOpenTxForm(-3L) }
                QuickAction("－", "Pengeluaran") { onOpenTxForm(-4L) }
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                SectionTitle("Anggaran bulan ini")
                budgets.take(3).forEach { b ->
                    BudgetRow(b.categoryIcon, b.categoryName, b.spent, b.amount)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item { SectionTitle("Transaksi terakhir") }

        if (txns.isEmpty()) {
            item { EmptyState("🧾", "Belum ada transaksi", "Ketuk + untuk mencatat pertama") }
        } else {
            items(txns.take(8), key = { it.id }) { t ->
                TxnRow(t)
            }
        }
    }
    }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String, accent: Color) {
    Column(Modifier.weight(1f)) {
        Text(label, color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.QuickAction(icon: String, label: String, onClick: () -> Unit) {
    val (iconBg, iconColor) = when (label) {
        "Pemasukan" -> Brand100 to Brand700
        "Pengeluaran" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        "Transfer" -> Color(0xFFE0F2FE) to Color(0xFF0277BD)
        else -> Color(0xFFFFF3E0) to Color(0xFFE65100)
    }
    // Animasi tekan ala e-wallet: kartu mengecil sedikit saat ditekan
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.6f),
        label = "quickaction_scale"
    )
    Card(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium, color = iconColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BudgetRow(icon: String, name: String, spent: Double, budget: Double) {
    val pct = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0) else 0.0
    val color = when { pct >= 1.0 -> ExpenseRed; pct >= 0.8 -> Amber; else -> Brand600 }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = color,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pct.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("${formatRp(spent)} / ${formatRp(budget)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TxnRow(t: id.keuanganku.app.data.repo.Txn, onClick: (() -> Unit)? = null) {
    val (icon, color) = when (t.type) {
        "income" -> "↓" to IncomeGreen
        "transfer" -> "⇄" to TransferBlue
        else -> (t.categoryIcon.ifBlank { "🛒" }) to ExpenseRed
    }
    val sign = when (t.type) { "income" -> "+"; "expense" -> "−"; else -> "" }
    val cardModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, bg = color.copy(alpha = 0.12f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.note?.ifBlank { null } ?: t.categoryName.ifBlank { "Transaksi" },
                    fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text(prettyDateTime(t.date),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$sign${formatRp(t.amount)}", fontWeight = FontWeight.Bold, color = color)
        }
    }
}
