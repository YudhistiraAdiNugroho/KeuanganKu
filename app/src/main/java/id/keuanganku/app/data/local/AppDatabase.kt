package id.keuanganku.app.data.local

import androidx.room.*

// Entitas lokal = cermin tabel Supabase + kolom sinkronisasi.
// id lokal: id server jika sudah tersinkron, negatif (sementara) jika baru offline.
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val name: String,
    val type: String,
    val balance: Double,
    val icon: String,
    val dirty: Boolean = false,   // true = belum tersinkron ke server
    val deleted: Boolean = false  // soft-delete untuk antrean hapus
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val name: String,
    val type: String,
    val icon: String,
    val dirty: Boolean = false,
    val deleted: Boolean = false
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val accountId: Long,      // id lokal akun
    val toAccountId: Long?,
    val categoryId: Long?,
    val type: String,
    val amount: Double,
    val date: String,
    val note: String?,
    val receiptUrl: String?,
    val receiptLocalPath: String?,
    val dirty: Boolean = false,
    val deleted: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val categoryId: Long,
    val amount: Double,
    val month: String,
    val year: Int,
    val dirty: Boolean = false,
    val deleted: Boolean = false
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val accountId: Long,
    val categoryId: Long?,
    val type: String,
    val amount: Double,
    val note: String?,
    val frequency: String,
    val nextDate: String,
    val active: Boolean,
    val dirty: Boolean = false,
    val deleted: Boolean = false
)

@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
    @PrimaryKey val id: Long,
    val serverId: Long?,
    val userId: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val icon: String,
    val dirty: Boolean = false,
    val deleted: Boolean = false
)

@Dao
interface FinanceDao {
    // ---- accounts ----
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY id ASC")
    suspend fun accounts(): List<AccountEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(list: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(a: AccountEntity): Long
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun accountById(id: Long): AccountEntity?
    @Query("SELECT * FROM accounts WHERE dirty = 1")
    suspend fun dirtyAccounts(): List<AccountEntity>

    // ---- categories ----
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC")
    suspend fun categories(): List<CategoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: CategoryEntity): Long
    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirtyCategories(): List<CategoryEntity>

    // ---- transactions ----
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun transactions(limit: Int = 500): List<TransactionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(list: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(t: TransactionEntity): Long
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transactionById(id: Long): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun dirtyTransactions(): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND accountId = :accountId")
    suspend fun countTxByAccount(accountId: Long): Int

    // ---- budgets ----
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun budgets(): List<BudgetEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgets(list: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(b: BudgetEntity): Long
    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun dirtyBudgets(): List<BudgetEntity>

    // ---- recurring ----
    @Query("SELECT * FROM recurring WHERE deleted = 0 ORDER BY nextDate ASC")
    suspend fun recurring(): List<RecurringEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurring(list: List<RecurringEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringOne(r: RecurringEntity): Long
    @Query("SELECT * FROM recurring WHERE dirty = 1")
    suspend fun dirtyRecurring(): List<RecurringEntity>
    @Query("SELECT COUNT(*) FROM recurring WHERE deleted = 0 AND accountId = :accountId")
    suspend fun countRecurringByAccount(accountId: Long): Int
    @Query("UPDATE recurring SET nextDate = :next, dirty = 1 WHERE id = :id")
    suspend fun bumpRecurring(id: Long, next: String)

    // ---- saving goals ----
    @Query("SELECT * FROM saving_goals WHERE deleted = 0 ORDER BY id ASC")
    suspend fun savingGoals(): List<SavingGoalEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavingGoals(list: List<SavingGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavingGoal(s: SavingGoalEntity): Long
    @Query("SELECT * FROM saving_goals WHERE dirty = 1")
    suspend fun dirtySavingGoals(): List<SavingGoalEntity>

    @Query("DELETE FROM accounts WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()
}

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
        BudgetEntity::class, RecurringEntity::class, SavingGoalEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}
