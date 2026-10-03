package id.keuanganku.app.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import id.keuanganku.app.data.local.*
import id.keuanganku.app.data.remote.RestApi
import id.keuanganku.app.data.remote.SessionManager
import id.keuanganku.app.data.remote.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.random.Random

/** put() yang aman untuk nilai null -> JSON null (PostgREST: set kolom jadi NULL). */
private fun JsonObjectBuilder.putN(key: String, value: Long?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}
private fun JsonObjectBuilder.putN(key: String, value: String?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Domain models untuk UI (dipetakan dari entity lokal). */
data class Account(val id: Long, val name: String, val type: String, val balance: Double, val icon: String)
data class Category(val id: Long, val name: String, val type: String, val icon: String)
data class Txn(
    val id: Long, val accountId: Long, val toAccountId: Long?, val categoryId: Long?,
    val type: String, val amount: Double, val date: String, val note: String?,
    val receiptUrl: String?, val receiptLocalPath: String?,
    val accountName: String = "", val categoryName: String = "", val categoryIcon: String = ""
)
data class Budget(val id: Long, val categoryId: Long, val categoryName: String, val categoryIcon: String, val amount: Double, val month: String, val year: Int, val spent: Double)
data class Recurring(val id: Long, val accountId: Long, val accountName: String, val categoryId: Long?, val type: String, val amount: Double, val note: String?, val frequency: String, val nextDate: String, val active: Boolean)
data class SavingGoal(val id: Long, val name: String, val targetAmount: Double, val currentAmount: Double, val icon: String)

class FinanceRepository(
    private val ctx: Context,
    private val api: RestApi,
    private val dao: FinanceDao,
    private val session: SessionManager,
    private val authRepo: AuthRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    private val _txns = MutableStateFlow<List<Txn>>(emptyList())
    val txns: StateFlow<List<Txn>> = _txns.asStateFlow()
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()
    private val _recurring = MutableStateFlow<List<Recurring>>(emptyList())
    val recurring: StateFlow<List<Recurring>> = _recurring.asStateFlow()
    private val _goals = MutableStateFlow<List<SavingGoal>>(emptyList())
    val goals: StateFlow<List<SavingGoal>> = _goals.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val syncMutex = Mutex()

    private fun tempId() = -(abs(Random.nextLong()) % 1_000_000_000L + 1)

    // ---------- refresh dari Room ----------
    suspend fun reloadLocal() = withContext(Dispatchers.IO) {
        val accs = dao.accounts().map { Account(it.id, it.name, it.type, it.balance, it.icon) }
        val cats = dao.categories().map { Category(it.id, it.name, it.type, it.icon) }
        val accMap = accs.associateBy { it.id }
        val catMap = cats.associateBy { it.id }
        _accounts.value = accs
        _categories.value = cats
        _txns.value = dao.transactions().map {
            Txn(it.id, it.accountId, it.toAccountId, it.categoryId, it.type, it.amount, it.date,
                it.note, it.receiptUrl, it.receiptLocalPath,
                accMap[it.accountId]?.name ?: "?", catMap[it.categoryId]?.name ?: "Tanpa kategori",
                catMap[it.categoryId]?.icon ?: "📦")
        }
        val ym = YearMonth.now()
        val monthTx = dao.transactions(10000).filter {
            it.type == "expense" && it.date.startsWith(ym.format(DateTimeFormatter.ofPattern("yyyy-MM")))
        }
        _budgets.value = dao.budgets().filter { it.month == ym.format(DateTimeFormatter.ofPattern("yyyy-MM")) }.map { b ->
            val c = catMap[b.categoryId]
            Budget(b.id, b.categoryId, c?.name ?: "?", c?.icon ?: "📦", b.amount, b.month, b.year,
                monthTx.filter { it.categoryId == b.categoryId }.sumOf { it.amount })
        }
        _recurring.value = dao.recurring().map {
            Recurring(it.id, it.accountId, accMap[it.accountId]?.name ?: "?", it.categoryId,
                it.type, it.amount, it.note, it.frequency, it.nextDate, it.active)
        }
        _goals.value = dao.savingGoals().map { SavingGoal(it.id, it.name, it.targetAmount, it.currentAmount, it.icon) }
    }

    fun observe() { scope.launch { reloadLocal() } }

    suspend fun getUserName(): String? = session.getUserName()

    // ---------- pull dari server ----------
    suspend fun refreshAll(): OpResult<Unit> = withContext(Dispatchers.IO) {
        if (!isOnline(ctx)) { reloadLocal(); return@withContext OpResult.Ok(Unit) }
        _syncing.value = true
        try {
            val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir. Login lagi.")
            val accDtos = api.getAccounts()
            val catDtos = api.getCategories()
            val txnDtos = api.getTransactions()
            val budDtos = api.getBudgets()
            val recDtos = api.getRecurring()
            val goalDtos = api.getSavingGoals()

            // Simpan mapping serverId->localId yang sudah ada agar FK tetap konsisten
            val accIdMap = dao.accounts().associate { (it.serverId ?: it.id) to it.id }.toMutableMap()
            val catIdMap = dao.categories().associate { (it.serverId ?: it.id) to it.id }.toMutableMap()

            // Merge sederhana: server menang untuk data non-dirty
            val dirtyAccIds = dao.dirtyAccounts().map { it.id }.toSet()
            val freshAccs = accDtos.map { d ->
                val localId = accIdMap[d.id] ?: d.id!!
                AccountEntity(localId, d.id, uid, d.name, d.type, d.balance, d.icon,
                    dirty = localId in dirtyAccIds)
            }
            // Hapus lokal yang sudah tidak ada di server (kecuali dirty)
            val serverAccIds = accDtos.mapNotNull { it.id }.toSet()
            dao.accounts().filter { it.serverId != null && it.serverId !in serverAccIds && !it.dirty }
                .forEach { dao.upsertAccount(it.copy(deleted = true)) }
            dao.upsertAccounts(freshAccs)
            accDtos.forEach { accIdMap[it.id!!] = accIdMap[it.id] ?: it.id!! }

            val dirtyCatIds = dao.dirtyCategories().map { it.id }.toSet()
            dao.upsertCategories(catDtos.map { d ->
                val localId = catIdMap[d.id] ?: d.id!!
                CategoryEntity(localId, d.id, uid, d.name, d.type, d.icon, dirty = localId in dirtyCatIds)
            })

            val dirtyTxnIds = dao.dirtyTransactions().map { it.id }.toSet()
            dao.upsertTransactions(txnDtos.map { d ->
                val localA = accIdMap[d.accountId] ?: d.accountId
                val localTo = d.toAccountId?.let { accIdMap[it] ?: it }
                val localC = d.categoryId?.let { catIdMap[it] }
                TransactionEntity(d.id!!, d.id, uid, localA, localTo, localC, d.type, d.amount,
                    d.date, d.note, d.receiptUrl, null, dirty = d.id in dirtyTxnIds)
            })

            dao.upsertBudgets(budDtos.map { d ->
                BudgetEntity(d.id!!, d.id, uid, catIdMap[d.categoryId] ?: d.categoryId,
                    d.amount, d.month, d.year)
            })
            dao.upsertRecurring(recDtos.map { d ->
                RecurringEntity(d.id!!, d.id, uid, accIdMap[d.accountId] ?: d.accountId,
                    d.categoryId?.let { catIdMap[it] }, d.type, d.amount, d.note,
                    d.frequency, d.nextDate, d.active)
            })
            dao.upsertSavingGoals(goalDtos.map { d ->
                SavingGoalEntity(d.id!!, d.id, uid, d.name, d.targetAmount, d.currentAmount, d.icon)
            })
            dao.purgeDeleted()
            reloadLocal()
            OpResult.Ok(Unit)
        } catch (e: Exception) {
            reloadLocal()
            OpResult.Err(e.userMessage("Gagal memuat data."))
        } finally { _syncing.value = false }
    }

    // ---------- push antrean offline ----------
    /** Pull data bersih dari server, merge ke lokal (jangan timpa yang dirty). */
    private suspend fun pullFromServer(uid: String) {
        try {
            // Akun tidak di-pull (sistem akun dihapus, pakai lokal saja)
            // Pastikan ada akun lokal default untuk transaksi
            var localAccounts = dao.accounts()
            if (localAccounts.isEmpty()) {
                val newAcc = AccountEntity(
                    id = tempId(), serverId = null, userId = uid,
                    name = "Tunai", type = "cash", balance = 0.0,
                    icon = "\uD83D\uDCB0", dirty = false, deleted = false
                )
                dao.upsertAccount(newAcc)
                localAccounts = dao.accounts()
            }
            val defaultAccId = localAccounts.firstOrNull()?.id ?: return
            // 1. Pull categories untuk mapping
            val serverCategories = try { api.getCategories() } catch (_: Exception) { emptyList() }
            val localCategories = dao.categories()
            val catServerToLocal = mutableMapOf<Long, Long>()
            for (sc in serverCategories) {
                val sid = sc.id ?: continue
                val existing = localCategories.find { it.serverId == sid }
                if (existing != null) {
                    catServerToLocal[sid] = existing.id
                } else {
                    val newId = tempId()
                    dao.upsertCategory(CategoryEntity(
                        id = newId, serverId = sid, userId = uid,
                        name = sc.name, type = sc.type, icon = sc.icon,
                        dirty = false, deleted = false
                    ))
                    catServerToLocal[sid] = newId
                }
            }
            // 2. Pull transactions (pakai akun default lokal)
            val serverTxns = api.getTransactions()
            val localTxns = dao.transactions() // fetch sekali di luar loop
            for (st in serverTxns) {
                val sid = st.id ?: continue
                val localCatId = st.categoryId?.let { catServerToLocal[it] }
                val existing = localTxns.find { it.serverId == sid }
                if (existing != null) {
                    if (!existing.dirty) {
                        dao.upsertTransaction(existing.copy(
                            accountId = defaultAccId, toAccountId = null,
                            categoryId = localCatId,
                            type = st.type, amount = st.amount, date = st.date,
                            note = st.note, receiptUrl = st.receiptUrl,
                            dirty = false
                        ))
                    }
                } else {
                    dao.upsertTransaction(TransactionEntity(
                        id = tempId(), serverId = sid, userId = uid,
                        accountId = defaultAccId, toAccountId = null,
                        categoryId = localCatId, type = st.type, amount = st.amount,
                        date = st.date, note = st.note,
                        receiptUrl = st.receiptUrl, receiptLocalPath = null,
                        dirty = false, deleted = false
                    ))
                }
            }
        } catch (e: Exception) {
            // Pull gagal: lanjut dengan data lokal, jangan crash sync
        }
    }

    suspend fun syncPending(): OpResult<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (!isOnline(ctx)) return@withLock OpResult.Ok(Unit)
            // Refresh token sebelum sync agar tidak 401
            authRepo.refreshIfNeeded()
        _syncing.value = true
        try {
            val uid = session.getUserId() ?: return@withLock OpResult.Err("Sesi berakhir.")
            val accMap = mutableMapOf<Long, Long>() // localId -> serverId
            val catMap = mutableMapOf<Long, Long>()

            // 1. Akun: dilewati (sistem akun dihapus dari UI, hanya pakai lokal)
            // for (a in dao.dirtyAccounts()) {
            //     ...
            // }
            // 2. Kategori
            for (c in dao.dirtyCategories()) {
                if (c.deleted) {
                    c.serverId?.let { try { api.deleteCategory("eq.${it}") } catch (_: Exception) {} }
                    dao.upsertCategory(c.copy(dirty = false))
                } else if (c.serverId == null) {
                    val created = api.createCategory(CategoryDto(null, uid, c.name, c.type, c.icon)).first()
                    catMap[c.id] = created.id!!
                    dao.upsertCategory(c.copy(serverId = created.id, dirty = false))
                } else {
                    dao.upsertCategory(c.copy(dirty = false))
                }
            }
            // Lengkapi mapping dari data lokal yang sudah tersinkron
            dao.accounts().forEach { if (it.serverId != null) accMap[it.id] = it.serverId }
            dao.categories().forEach { if (it.serverId != null) catMap[it.id] = it.serverId }

            // Pastikan ada akun server default untuk transaksi (sistem akun dihapus dari UI)
            var defaultServerAccId: Long? = accMap.values.firstOrNull()
            if (defaultServerAccId == null) {
                try {
                    val existing = api.getAccounts().firstOrNull()
                    if (existing?.id != null) {
                        defaultServerAccId = existing.id
                    } else {
                        val created = api.createAccount(AccountDto(null, uid, "Tunai", "cash", 0.0, "\uD83D\uDCB0")).first()
                        defaultServerAccId = created.id
                    }
                    // Update akun lokal dengan serverId
                    dao.accounts().firstOrNull()?.let { localAcc ->
                        dao.upsertAccount(localAcc.copy(serverId = defaultServerAccId, dirty = false))
                        accMap[localAcc.id] = defaultServerAccId!!
                    }
                } catch (_: Exception) {}
            }

            // 3. Transaksi
            for (t in dao.dirtyTransactions()) {
                try {
                    val sAcc = accMap[t.accountId] ?: defaultServerAccId ?: continue
                    val sTo = t.toAccountId?.let { accMap[it] }
                    val sCat = t.categoryId?.let { catMap[it] }
                    if (t.deleted) {
                        t.serverId?.let { try { api.deleteTransaction("eq.${it}") } catch (_: Exception) {} }
                        dao.upsertTransaction(t.copy(dirty = false))
                    } else if (t.serverId == null) {
                        val created = api.createTransaction(TransactionDto(null, uid, sAcc, sTo, sCat, t.type, t.amount, t.date, t.note, t.receiptUrl)).first()
                        dao.upsertTransaction(t.copy(serverId = created.id, dirty = false))
                    } else {
                        api.updateTransaction("eq.${t.serverId}", buildJsonObject { put("account_id", sAcc); putN("to_account_id", sTo); putN("category_id", sCat); put("type", t.type); put("amount", t.amount); put("date", t.date); putN("note", t.note); putN("receipt_url", t.receiptUrl) })
                        dao.upsertTransaction(t.copy(dirty = false))
                    }
                } catch (_: Exception) {
                    // Satu transaksi gagal: lewati, lanjut ke berikutnya
                    continue
                }
            }
            // 4. Anggaran
            for (b in dao.dirtyBudgets()) {
                val sCat = catMap[b.categoryId] ?: continue
                if (b.deleted) { b.serverId?.let { try { api.deleteBudget("eq.${it}") } catch (_: Exception) {} } }
                else {
                    val created = api.upsertBudget(BudgetDto(b.serverId, uid, sCat, b.amount, b.month, b.year)).firstOrNull()
                    dao.upsertBudget(b.copy(serverId = created?.id ?: b.serverId, dirty = false))
                }
                if (b.deleted) dao.upsertBudget(b.copy(dirty = false))
            }
            // 5. Berulang
            for (r in dao.dirtyRecurring()) {
                val sAcc = accMap[r.accountId] ?: continue
                val sCat = r.categoryId?.let { catMap[it] }
                if (r.deleted) { r.serverId?.let { try { api.deleteRecurring("eq.${it}") } catch (_: Exception) {} } }
                else if (r.serverId == null) {
                    val created = api.createRecurring(RecurringDto(null, uid, sAcc, sCat, r.type, r.amount, r.note, r.frequency, r.nextDate, r.active)).first()
                    dao.upsertRecurringOne(r.copy(serverId = created.id, dirty = false))
                } else {
                    api.updateRecurring("eq.${r.serverId}", buildJsonObject { put("account_id", sAcc); putN("category_id", sCat); put("type", r.type); put("amount", r.amount); putN("note", r.note); put("frequency", r.frequency); put("next_date", r.nextDate); put("active", r.active) })
                    dao.upsertRecurringOne(r.copy(dirty = false))
                }
                if (r.deleted) dao.upsertRecurringOne(r.copy(dirty = false))
            }
            // 6. Tabungan
            for (s in dao.dirtySavingGoals()) {
                if (s.deleted) { s.serverId?.let { try { api.deleteSavingGoal("eq.${it}") } catch (_: Exception) {} } }
                else if (s.serverId == null) {
                    val created = api.createSavingGoal(SavingGoalDto(null, uid, s.name, s.targetAmount, s.currentAmount, s.icon)).first()
                    dao.upsertSavingGoal(s.copy(serverId = created.id, dirty = false))
                } else {
                    api.updateSavingGoal("eq.${s.serverId}", buildJsonObject { put("name", s.name); put("target_amount", s.targetAmount); put("current_amount", s.currentAmount); put("icon", s.icon) })
                    dao.upsertSavingGoal(s.copy(dirty = false))
                }
                if (s.deleted) dao.upsertSavingGoal(s.copy(dirty = false))
            }
            dao.purgeDeleted()
            // Pull data bersih dari server untuk perbaiki data lokal yang korup
            pullFromServer(uid)
            reloadLocal()
            // Perbaiki transaksi yatim dulu, baru hitung ulang saldo
            repairOrphanedTransactions()
            // Hitung ulang semua saldo akun SETELAH reload agar tidak ditimpa data server yang salah
            for (acc in dao.accounts()) {
                recalculateAccountBalance(acc.id)
            }
            // Reload lagi agar UI tampilkan saldo yang sudah dihitung ulang
            reloadLocal()
            OpResult.Ok(Unit)
        } catch (e: Exception) {
            OpResult.Err(e.userMessage("Sinkronisasi gagal, dicoba lagi nanti."))
        } finally { _syncing.value = false }
        }
    }

    // ---------- CRUD: Akun ----------
    suspend fun addAccount(name: String, type: String, balance: Double, icon: String): OpResult<Unit> =
        withContext(Dispatchers.IO) {
            val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
            val e = AccountEntity(tempId(), null, uid, name, type, balance, icon, dirty = true)
            dao.upsertAccount(e); reloadLocal()
            if (isOnline(ctx)) syncPending()
            OpResult.Ok(Unit)
        }

    suspend fun updateAccount(id: Long, name: String, type: String, balance: Double, icon: String): OpResult<Unit> =
        withContext(Dispatchers.IO) {
            val cur = dao.accountById(id) ?: return@withContext OpResult.Err("Akun tidak ditemukan.")
            dao.upsertAccount(cur.copy(name = name, type = type, balance = balance, icon = icon, dirty = true))
            reloadLocal(); if (isOnline(ctx)) syncPending()
            OpResult.Ok(Unit)
        }

    suspend fun deleteAccount(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        if (dao.countTxByAccount(id) > 0 || dao.countRecurringByAccount(id) > 0)
            return@withContext OpResult.Err("Akun masih dipakai transaksi/jadwal. Tidak bisa dihapus.")
        val cur = dao.accountById(id) ?: return@withContext OpResult.Err("Akun tidak ditemukan.")
        dao.upsertAccount(cur.copy(deleted = true, dirty = true))
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    // ---------- CRUD: Kategori ----------
    suspend fun addCategory(name: String, type: String, icon: String): OpResult<Unit> =
        withContext(Dispatchers.IO) {
            val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
            dao.upsertCategory(CategoryEntity(tempId(), null, uid, name, type, icon, dirty = true))
            reloadLocal(); if (isOnline(ctx)) syncPending()
            OpResult.Ok(Unit)
        }

    suspend fun deleteCategory(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        // Transaksi & jadwal jadi "Tanpa kategori"
        dao.transactions(10000).filter { it.categoryId == id }
            .forEach { dao.upsertTransaction(it.copy(categoryId = null, dirty = true)) }
        dao.recurring().filter { it.categoryId == id }
            .forEach { dao.upsertRecurringOne(it.copy(categoryId = null, dirty = true)) }
        dao.categories().find { it.id == id }?.let { dao.upsertCategory(it.copy(deleted = true, dirty = true)) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    // ---------- CRUD: Transaksi ----------
    suspend fun addTransaction(
        accountId: Long, toAccountId: Long?, categoryId: Long?, type: String,
        amount: Double, date: String, note: String?, receiptUrl: String?, receiptLocalPath: String?
    ): OpResult<Unit> = withContext(Dispatchers.IO) {
        if (type == "transfer" && (toAccountId == null || toAccountId == accountId))
            return@withContext OpResult.Err("Transfer butuh akun tujuan yang berbeda.")
        val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
        val t = TransactionEntity(tempId(), null, uid, accountId, toAccountId, categoryId, type,
            amount, date, note?.ifBlank { null }, receiptUrl, receiptLocalPath, dirty = true)
        dao.upsertTransaction(t)
        applyBalanceEffect(t, +1)
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun updateTransaction(
        id: Long, accountId: Long, toAccountId: Long?, categoryId: Long?, type: String,
        amount: Double, date: String, note: String?
    ): OpResult<Unit> = withContext(Dispatchers.IO) {
        val old = dao.transactionById(id) ?: return@withContext OpResult.Err("Transaksi tidak ditemukan.")
        applyBalanceEffect(old, -1)
        val neu = old.copy(accountId = accountId, toAccountId = toAccountId, categoryId = categoryId,
            type = type, amount = amount, date = date, note = note?.ifBlank { null }, dirty = true)
        dao.upsertTransaction(neu)
        applyBalanceEffect(neu, +1)
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun deleteTransaction(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        val old = dao.transactionById(id) ?: return@withContext OpResult.Err("Transaksi tidak ditemukan.")
        dao.upsertTransaction(old.copy(deleted = true, dirty = true))
        recalculateAccountBalance(old.accountId)
        old.toAccountId?.let { recalculateAccountBalance(it) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    /** Hitung ulang saldo akun dari semua transaksi yang tidak dihapus. */
    /** Perbaiki transaksi yatim: accountId tidak ada di tabel akun -> pindahkan ke akun pertama. */
    private suspend fun repairOrphanedTransactions() {
        var accounts = dao.accounts()
        // Jika tidak ada akun sama sekali tapi ada transaksi: buat akun default "Tunai"
        if (accounts.isEmpty()) {
            val txns = dao.transactions().filter { !it.deleted }
            if (txns.isNotEmpty()) {
                val uid = session.getUserId() ?: return
                val newAcc = AccountEntity(
                    id = tempId(), serverId = null, userId = uid,
                    name = "Tunai", type = "cash", balance = 0.0,
                    icon = "\uD83D\uDCB0", dirty = true, deleted = false
                )
                dao.upsertAccount(newAcc)
                accounts = dao.accounts()
            } else {
                return
            }
        }
        if (accounts.isEmpty()) return
        val validIds = accounts.map { it.id }.toSet()
        val defaultId = accounts.first().id
        var repaired = 0
        for (t in dao.transactions()) {
            if (t.deleted) continue
            var fixed = t
            if (t.accountId !in validIds) {
                fixed = fixed.copy(accountId = defaultId, dirty = true)
            }
            if (t.toAccountId != null && t.toAccountId !in validIds) {
                fixed = fixed.copy(toAccountId = defaultId, dirty = true)
            }
            if (fixed != t) {
                dao.upsertTransaction(fixed)
                repaired++
            }
        }
    }

    private suspend fun recalculateAccountBalance(accountId: Long) {
        val txns = dao.transactions().filter { !it.deleted && (it.accountId == accountId || it.toAccountId == accountId) }
        var balance = 0.0
        for (t in txns) {
            when {
                t.type == "income" && t.accountId == accountId -> balance += t.amount
                t.type == "expense" && t.accountId == accountId -> balance -= t.amount
                t.type == "transfer" && t.accountId == accountId -> balance -= t.amount
                t.type == "transfer" && t.toAccountId == accountId -> balance += t.amount
            }
        }
        dao.accountById(accountId)?.let {
            dao.upsertAccount(it.copy(balance = balance, dirty = true))
        }
    }

    /** Terapkan/batalkan efek saldo akun dari sebuah transaksi. */
    private suspend fun applyBalanceEffect(t: TransactionEntity, sign: Int) {
        val from = dao.accountById(t.accountId)
        val to = t.toAccountId?.let { dao.accountById(it) }
        when (t.type) {
            "income" -> from?.let { dao.upsertAccount(it.copy(balance = it.balance + sign * t.amount, dirty = true)) }
            "expense" -> from?.let { dao.upsertAccount(it.copy(balance = it.balance - sign * t.amount, dirty = true)) }
            "transfer" -> {
                from?.let { dao.upsertAccount(it.copy(balance = it.balance - sign * t.amount, dirty = true)) }
                to?.let { dao.upsertAccount(it.copy(balance = it.balance + sign * t.amount, dirty = true)) }
            }
        }
    }

    // ---------- CRUD: Anggaran ----------
    suspend fun upsertBudget(categoryId: Long, amount: Double): OpResult<Unit> =
        withContext(Dispatchers.IO) {
            val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
            val ym = YearMonth.now()
            val month = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val existing = dao.budgets().find { it.categoryId == categoryId && it.month == month }
            if (existing != null) dao.upsertBudget(existing.copy(amount = amount, dirty = true))
            else dao.upsertBudget(BudgetEntity(tempId(), null, uid, categoryId, amount, month, ym.year, dirty = true))
            reloadLocal(); if (isOnline(ctx)) syncPending()
            OpResult.Ok(Unit)
        }

    suspend fun deleteBudget(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        dao.budgets().find { it.id == id }?.let { dao.upsertBudget(it.copy(deleted = true, dirty = true)) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    // ---------- CRUD: Berulang ----------
    suspend fun addRecurring(
        accountId: Long, categoryId: Long?, type: String, amount: Double,
        note: String?, frequency: String, nextDate: String
    ): OpResult<Unit> = withContext(Dispatchers.IO) {
        val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
        dao.upsertRecurringOne(RecurringEntity(tempId(), null, uid, accountId, categoryId, type,
            amount, note?.ifBlank { null }, frequency, nextDate, true, dirty = true))
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun toggleRecurring(id: Long, active: Boolean): OpResult<Unit> = withContext(Dispatchers.IO) {
        dao.recurring().find { it.id == id }?.let { dao.upsertRecurringOne(it.copy(active = active, dirty = true)) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun deleteRecurring(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        dao.recurring().find { it.id == id }?.let { dao.upsertRecurringOne(it.copy(deleted = true, dirty = true)) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    /** Buat transaksi dari jadwal yang jatuh tempo hari ini (dipanggil saat app dibuka). */
    suspend fun processDueRecurring(): Int = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        var count = 0
        for (r in dao.recurring().filter { it.active && it.nextDate <= today }) {
            val t = TransactionEntity(tempId(), null, r.userId, r.accountId, null, r.categoryId,
                r.type, r.amount, today, (r.note ?: "") + " (otomatis)", null, null, dirty = true)
            dao.upsertTransaction(t)
            applyBalanceEffect(t, +1)
            val next = when (r.frequency) {
                "daily" -> LocalDate.parse(r.nextDate).plusDays(1)
                "weekly" -> LocalDate.parse(r.nextDate).plusWeeks(1)
                "yearly" -> LocalDate.parse(r.nextDate).plusYears(1)
                else -> LocalDate.parse(r.nextDate).plusMonths(1)
            }.format(DateTimeFormatter.ISO_DATE)
            dao.bumpRecurring(r.id, next)
            count++
        }
        if (count > 0) { reloadLocal(); if (isOnline(ctx)) syncPending() }
        count
    }

    // ---------- CRUD: Tabungan ----------
    suspend fun addSavingGoal(name: String, target: Double, icon: String): OpResult<Unit> =
        withContext(Dispatchers.IO) {
            val uid = session.getUserId() ?: return@withContext OpResult.Err("Sesi berakhir.")
            dao.upsertSavingGoal(SavingGoalEntity(tempId(), null, uid, name, target, 0.0, icon, dirty = true))
            reloadLocal(); if (isOnline(ctx)) syncPending()
            OpResult.Ok(Unit)
        }

    suspend fun moveSaving(id: Long, delta: Double): OpResult<Unit> = withContext(Dispatchers.IO) {
        val g = dao.savingGoals().find { it.id == id } ?: return@withContext OpResult.Err("Target tidak ditemukan.")
        val neu = (g.currentAmount + delta).coerceAtLeast(0.0)
        dao.upsertSavingGoal(g.copy(currentAmount = neu, dirty = true))
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun deleteSavingGoal(id: Long): OpResult<Unit> = withContext(Dispatchers.IO) {
        dao.savingGoals().find { it.id == id }?.let { dao.upsertSavingGoal(it.copy(deleted = true, dirty = true)) }
        reloadLocal(); if (isOnline(ctx)) syncPending()
        OpResult.Ok(Unit)
    }

    suspend fun updateReceiptUrl(localId: Long, url: String) = withContext(Dispatchers.IO) {
        dao.transactionById(localId)?.let { dao.upsertTransaction(it.copy(receiptUrl = url, dirty = true)) }
        reloadLocal()
    }
}
