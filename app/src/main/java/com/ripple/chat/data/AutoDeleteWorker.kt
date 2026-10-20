package com.ripple.chat.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ripple.chat.data.finance.FinanceCategorizer
import kotlinx.coroutines.flow.first

class AutoDeleteWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            AutoDeleteManager.executeAutoDelete(appContext)
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
