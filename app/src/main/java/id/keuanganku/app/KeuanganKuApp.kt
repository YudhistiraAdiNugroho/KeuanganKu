package id.keuanganku.app

import android.app.Application
import androidx.room.Room
import androidx.work.*
import id.keuanganku.app.data.local.AppDatabase
import id.keuanganku.app.data.remote.*
import id.keuanganku.app.data.repo.AuthRepository
import id.keuanganku.app.data.repo.FinanceRepository
import id.keuanganku.app.worker.SyncWorker
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class KeuanganKuApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var session: SessionManager
        private set
    lateinit var authRepo: AuthRepository
        private set
    lateinit var financeRepo: FinanceRepository
        private set
    lateinit var nineRouterApi: NineRouterApi
        private set
    lateinit var storageApi: StorageApi
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)

        // Token dibaca dinamis tiap request (interceptor jalan di thread background OkHttp,
        // jadi runBlocking di sini aman dan token selalu fresh).
        val retrofit = NetworkModule.supabase { runBlocking { session.getAccess() } }
        val authApi = retrofit.create(AuthApi::class.java)
        val restApi = retrofit.create(RestApi::class.java)
        storageApi = retrofit.create(StorageApi::class.java)

        authRepo = AuthRepository(authApi, session)

        val db = Room.databaseBuilder(this, AppDatabase::class.java, "keuanganku.db")
            .fallbackToDestructiveMigration()
            .build()

        financeRepo = FinanceRepository(this, restApi, db.financeDao(), session)

        nineRouterApi = NetworkModule.nineRouter().create(NineRouterApi::class.java)

        // Sinkronisasi otomatis tiap 30 menit saat online
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "keuanganku-sync", ExistingPeriodicWorkPolicy.KEEP, syncRequest
        )

        // Proses jadwal berulang yang jatuh tempo saat app dibuka
        appScope.launch(Dispatchers.IO) {
            try { financeRepo.processDueRecurring() } catch (_: Exception) {}
        }
    }
}
