package id.keuanganku.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.keuanganku.app.KeuanganKuApp

/** Mendorong antrean offline ke Supabase saat internet kembali. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KeuanganKuApp
        return try {
            app.financeRepo.syncPending()
            app.financeRepo.refreshAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
