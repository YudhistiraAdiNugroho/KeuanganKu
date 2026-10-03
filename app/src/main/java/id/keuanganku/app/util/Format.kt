package id.keuanganku.app.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale("id", "ID")

fun formatRp(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(idLocale)
    nf.maximumFractionDigits = 0
    return "Rp${nf.format(amount)}"
}

fun todayStr(): String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

fun currentMonthStr(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

fun prettyDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd MMM yyyy", idLocale))
} catch (_: Exception) { iso }

fun prettyDateTime(iso: String): String = try {
    // Format baru: "yyyy-MM-dd HH:mm", format lama: "yyyy-MM-dd"
    if (iso.length > 10) {
        val dt = java.time.LocalDateTime.parse(iso, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy || HH:mm", idLocale))
    } else {
        prettyDate(iso)
    }
} catch (_: Exception) { iso }

fun nowDateTimeStr(): String =
    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

fun monthName(yyyyMm: String): String = try {
    val (y, m) = yyyyMm.split("-")
    val names = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    "${names[m.toInt()]} $y"
} catch (_: Exception) { yyyyMm }

fun formatTanggalHariIni(): String = try {
    val days = listOf("", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val months = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    val now = LocalDate.now()
    "${days[now.dayOfWeek.value]}, ${now.dayOfMonth} ${months[now.monthValue]} ${now.year}"
} catch (_: Exception) { "" }
