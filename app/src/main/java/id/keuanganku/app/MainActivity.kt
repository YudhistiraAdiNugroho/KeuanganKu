package id.keuanganku.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.keuanganku.app.ui.navigation.Routes
import id.keuanganku.app.ui.screens.auth.*
import id.keuanganku.app.ui.screens.main.MainScreen
import id.keuanganku.app.ui.screens.main.PrefillTxn
import id.keuanganku.app.ui.screens.main.TransactionFormDialog
import id.keuanganku.app.ui.screens.receipt.ReceiptCaptureScreen
import id.keuanganku.app.ui.screens.receipt.ReceiptViewModel
import id.keuanganku.app.ui.screens.update.UpdateScreen
import id.keuanganku.app.ui.theme.Brand600
import id.keuanganku.app.ui.theme.KeuanganKuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KeuanganKuApp
        setContent {
            KeuanganKuTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                var startDest by remember { mutableStateOf<String?>(null) }

                // Tentukan layar awal dari sesi tersimpan
                LaunchedEffect(Unit) {
                    val loggedIn = try { app.session.getAccess() != null } catch (_: Exception) { false }
                    startDest = if (loggedIn) Routes.MAIN else Routes.LOGIN
                    if (loggedIn) {
                        // Refresh data di background saat app dibuka
                        scope.launch {
                            try { app.financeRepo.refreshAll() } catch (_: Exception) {}
                            try { app.financeRepo.observe() } catch (_: Exception) {}
                        }
                    } else {
                        scope.launch { try { app.financeRepo.observe() } catch (_: Exception) {} }
                    }
                }

                if (startDest == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Brand600)
                    }
                } else {
                    val authVm: AuthViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            AuthViewModel(app.authRepo) as T
                    })
                    val receiptVm: ReceiptViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ReceiptViewModel(app) as T
                    })
                    var receiptPrefill by remember { mutableStateOf<PrefillTxn?>(null) }
                    var txFormTarget by remember { mutableStateOf<Long?>(null) }
                    val snackbar = remember { SnackbarHostState() }
                    val notifyOk: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

                    Box(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = startDest!!) {
                        composable(Routes.LOGIN) {
                            LoginScreen(
                                vm = authVm,
                                onLoggedIn = {
                                    scope.launch {
                                        try { app.financeRepo.refreshAll() } catch (_: Exception) {}
                                    }
                                    nav.navigate(Routes.MAIN) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onRegister = { nav.navigate(Routes.REGISTER) },
                                onForgot = { nav.navigate(Routes.FORGOT) }
                            )
                        }
                        composable(Routes.REGISTER) {
                            RegisterScreen(
                                vm = authVm,
                                onOtpSent = { email, name ->
                                    nav.navigate(Routes.verifyRegister(email, name))
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            Routes.VERIFY_REGISTER,
                            arguments = listOf(
                                navArgument("email") { type = NavType.StringType },
                                navArgument("name") { type = NavType.StringType }
                            )
                        ) { backStack ->
                            VerifyRegisterScreen(
                                vm = authVm,
                                email = backStack.arguments?.getString("email") ?: "",
                                name = backStack.arguments?.getString("name") ?: "",
                                onDone = {
                                    nav.navigate(Routes.MAIN) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(Routes.FORGOT) {
                            ForgotScreen(
                                vm = authVm,
                                onOtpSent = { email -> nav.navigate(Routes.verifyForgot(email)) },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            Routes.VERIFY_FORGOT,
                            arguments = listOf(navArgument("email") { type = NavType.StringType })
                        ) { backStack ->
                            VerifyForgotScreen(
                                vm = authVm,
                                email = backStack.arguments?.getString("email") ?: "",
                                onDone = {
                                    nav.navigate(Routes.LOGIN) {
                                        popUpTo(Routes.FORGOT) { inclusive = true }
                                    }
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(Routes.MAIN) {
                            MainScreen(
                                repo = app.financeRepo,
                                onScan = { nav.navigate(Routes.RECEIPT_CAPTURE) },
                                onOpenUpdate = { nav.navigate(Routes.UPDATE) },
                                onLogout = {
                                    authVm.logout()
                                    nav.navigate(Routes.LOGIN) {
                                        popUpTo(Routes.MAIN) { inclusive = true }
                                    }
                                },
                                // null = form baru biasa, -2 = form transfer, >0 = edit
                                onOpenTxForm = { id -> txFormTarget = id ?: -1L }
                            )
                        }
                        composable(Routes.RECEIPT_CAPTURE) {
                            ReceiptCaptureScreen(
                                vm = receiptVm,
                                onDone = { prefill ->
                                    receiptPrefill = prefill
                                    nav.popBackStack()
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(Routes.UPDATE) {
                            UpdateScreen(onBack = { nav.popBackStack() })
                        }
                    }

                    // Form transaksi dari hasil pindai struk (prefill)
                    receiptPrefill?.let { prefill ->
                        TransactionFormDialog(
                            repo = app.financeRepo,
                            editingId = null,
                            prefill = prefill,
                            onDismiss = { receiptPrefill = null },
                            onSuccess = notifyOk
                        )
                    }

                    // Form transaksi dari tombol + (dipicu via MainScreen callback)
                    // -1 = baru, -2 = transfer baru, >0 = edit
                    txFormTarget?.let { target ->
                        TransactionFormDialog(
                            repo = app.financeRepo,
                            editingId = if (target > 0) target else null,
                            initialType = if (target == -2L) "transfer" else null,
                            onDismiss = { txFormTarget = null },
                            onSuccess = notifyOk
                        )
                    }

                        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }
}
