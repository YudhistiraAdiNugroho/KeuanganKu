package id.keuanganku.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.keuanganku.app.ui.components.*
import id.keuanganku.app.ui.theme.*

@Composable
fun AuthScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 72.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("💰", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onLoggedIn: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    LaunchedEffect(state.done) { if (state.done) { vm.reset(); onLoggedIn() } }
    DisposableEffect(Unit) { onDispose { vm.reset() } }

    AuthScaffold("Selamat datang kembali", "Masuk untuk kelola keuanganmu") {
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AppTextField(password, { password = it }, "Kata sandi", isPassword = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgot) { Text("Lupa kata sandi?", color = Brand700) }
        }
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        AppButton("Masuk", { vm.login(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(), loading = state.loading)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Belum punya akun? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegister, contentPadding = PaddingValues(0.dp)) {
                Text("Daftar", color = Brand700, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onOtpSent: (String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    LaunchedEffect(state.done) { if (state.done) { vm.reset(); onOtpSent(email, name) } }
    DisposableEffect(Unit) { onDispose { vm.reset() } }

    AuthScaffold("Buat akun baru", "Kode OTP dikirim ke email kamu") {
        AppTextField(name, { name = it }, "Nama")
        Spacer(Modifier.height(12.dp))
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        AppButton("Kirim kode OTP", { vm.requestRegisterOtp(email) },
            enabled = name.isNotBlank() && email.isNotBlank(), loading = state.loading)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali masuk", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OtpField(value: String, onChange: (String) -> Unit, label: String = "Kode OTP") {
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 8.sp
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    )
}

@Composable
fun VerifyRegisterScreen(
    vm: AuthViewModel, email: String, name: String, onDone: () -> Unit, onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()
    val valid = code.length >= 6 && password.length >= 6 && password == confirm

    LaunchedEffect(state.done) { if (state.done) { vm.reset(); onDone() } }
    DisposableEffect(Unit) { onDispose { vm.reset() } }

    AuthScaffold("Verifikasi email", "Kode dikirim ke $email") {
        OtpField(code, { code = it })
        Spacer(Modifier.height(12.dp))
        AppTextField(password, { password = it }, "Kata sandi (min. 6 karakter)", isPassword = true)
        Spacer(Modifier.height(12.dp))
        AppTextField(confirm, { confirm = it }, "Ulangi kata sandi", isPassword = true)
        if (confirm.isNotEmpty() && password != confirm)
            Text("Kata sandi tidak sama", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        AppButton("Buat akun", { vm.verifyRegister(email, code, password, name) },
            enabled = valid, loading = state.loading)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ForgotScreen(vm: AuthViewModel, onOtpSent: (String) -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    LaunchedEffect(state.done) { if (state.done) { vm.reset(); onOtpSent(email) } }
    DisposableEffect(Unit) { onDispose { vm.reset() } }

    AuthScaffold("Lupa kata sandi", "Kode pemulihan dikirim ke email kamu") {
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        AppButton("Kirim kode", { vm.requestRecovery(email) },
            enabled = email.isNotBlank(), loading = state.loading)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali masuk", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VerifyForgotScreen(vm: AuthViewModel, email: String, onDone: () -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()
    val valid = code.length >= 6 && password.length >= 6 && password == confirm

    LaunchedEffect(state.done) { if (state.done) { vm.reset(); onDone() } }
    DisposableEffect(Unit) { onDispose { vm.reset() } }

    AuthScaffold("Atur kata sandi baru", "Kode dikirim ke $email") {
        OtpField(code, { code = it })
        Spacer(Modifier.height(12.dp))
        AppTextField(password, { password = it }, "Kata sandi baru (min. 6 karakter)", isPassword = true)
        Spacer(Modifier.height(12.dp))
        AppTextField(confirm, { confirm = it }, "Ulangi kata sandi baru", isPassword = true)
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        AppButton("Simpan kata sandi", { vm.resetPassword(email, code, password) },
            enabled = valid, loading = state.loading)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
