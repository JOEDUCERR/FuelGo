package com.fuelgo.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fuelgo.app.widget.WidgetRefresher
import java.util.concurrent.TimeUnit

/**
 * Periodic widget refresh: location + best-effort network refresh, then a
 * cache-only widget render. Never blocks the widget on the network — the
 * widget itself always renders from the last cached state.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { WidgetRefresher.fullRefresh(applicationContext) }
        return Result.success()
    }
}

private const val WIDGET_WORK_NAME = "fuelgo_widget_refresh"

/** (Re)schedules the periodic widget refresh; KEEP-safe to call repeatedly. */
fun scheduleWidgetWork(context: Context, intervalMinutes: Int) {
    val clamped = intervalMinutes.coerceAtLeast(15)
    val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(clamped.toLong(), TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WIDGET_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
    )
}
