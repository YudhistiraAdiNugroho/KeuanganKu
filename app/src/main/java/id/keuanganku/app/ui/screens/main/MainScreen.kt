package id.keuanganku.app.ui.screens.main

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.ui.navigation.MainTabs
import id.keuanganku.app.ui.theme.Brand600

data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScreen(
    repo: FinanceRepository,
    onScan: () -> Unit,
    onLogout: () -> Unit,
    onOpenTxForm: (Long?) -> Unit,
    onOpenUpdate: () -> Unit,
) {
    var tab by remember { mutableStateOf(MainTabs.DASHBOARD) }
    var subScreen by remember { mutableStateOf<String?>(null) } // akun|anggaran|berulang|tabungan

    val tabs = listOf(
        Tab(MainTabs.DASHBOARD, "Beranda", Icons.Filled.Home),
        Tab(MainTabs.TRANSACTIONS, "Transaksi", Icons.Filled.Receipt),
        Tab(MainTabs.SCAN, "Pindai", Icons.Filled.DocumentScanner),
        Tab(MainTabs.REPORTS, "Laporan", Icons.Filled.BarChart),
        Tab(MainTabs.SETTINGS, "Lainnya", Icons.Filled.MoreHoriz),
    )

    // Refresh data tiap masuk tab
    LaunchedEffect(tab) {
        try { repo.refreshAll() } catch (_: Exception) {}
    }

    Scaffold(
        bottomBar = {
            // Sembunyikan bottom bar saat di sub-layar
            if (subScreen == null) {
                NavigationBar {
                    tabs.forEach { t ->
                        val selected = tab == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (t.route == MainTabs.SCAN) onScan() else tab = t.route },
                            icon = {
                                if (t.route == MainTabs.SCAN) {
                                    FloatingActionButton(
                                        onClick = onScan,
                                        modifier = Modifier.size(52.dp),
                                        containerColor = Brand600
                                    ) {
                                        Icon(t.icon, "Pindai struk",
                                            tint = androidx.compose.ui.graphics.Color.White)
                                    }
                                } else Icon(t.icon, t.label)
                            },
                            label = { if (t.route != MainTabs.SCAN) Text(t.label) },
                            alwaysShowLabel = t.route != MainTabs.SCAN
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (subScreen != null) {
                SubScreenScaffold(
                    title = when (subScreen) {
                        "anggaran" -> "Anggaran"
                        "berulang" -> "Transaksi Berulang"
                        else -> "Target Tabungan"
                    },
                    onBack = { subScreen = null }
                ) {
                    when (subScreen) {
                        "anggaran" -> BudgetsScreen(repo)
                        "berulang" -> RecurringScreen(repo)
                        else -> SavingsScreen(repo)
                    }
                }
            } else {
                AnimatedContent(targetState = tab, label = "tab") { t ->
                    when (t) {
                        MainTabs.DASHBOARD -> DashboardScreen(
                            repo, onOpenTxForm,
                            onOpenSection = { subScreen = it }
                        )
                        MainTabs.TRANSACTIONS -> TransactionsScreen(repo)
                        MainTabs.REPORTS -> ReportsScreen(repo)
                        MainTabs.SETTINGS -> SettingsScreen(
                            repo, onLogout,
                            onOpenSection = { subScreen = it },
                            onOpenUpdate = onOpenUpdate
                        )
                        else -> DashboardScreen(
                            repo, onOpenTxForm,
                            onOpenSection = { subScreen = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) { content() }
    }
}
