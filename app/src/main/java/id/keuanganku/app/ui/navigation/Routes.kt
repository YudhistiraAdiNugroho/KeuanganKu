package id.keuanganku.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val VERIFY_REGISTER = "verify_register/{email}/{name}"
    const val FORGOT = "forgot"
    const val VERIFY_FORGOT = "verify_forgot/{email}"
    const val MAIN = "main"
    const val RECEIPT_CAPTURE = "receipt_capture"
    const val RECEIPT_CONFIRM = "receipt_confirm"
    const val UPDATE = "update"

    fun verifyRegister(email: String, name: String) = "verify_register/$email/$name"
    fun verifyForgot(email: String) = "verify_forgot/$email"
}

object MainTabs {
    const val DASHBOARD = "tab_dashboard"
    const val TRANSACTIONS = "tab_transactions"
    const val SCAN = "tab_scan" // tombol tengah: pindai struk
    const val REPORTS = "tab_reports"
    const val SETTINGS = "tab_settings"
}
