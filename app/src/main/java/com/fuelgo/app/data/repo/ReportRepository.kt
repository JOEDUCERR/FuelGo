package com.fuelgo.app.data.repo

import android.content.Context
import com.fuelgo.app.data.db.ReportDao
import com.fuelgo.app.data.db.ReportEntity
import com.fuelgo.app.data.prefs.UserPrefs
import com.fuelgo.app.domain.Geo
import com.fuelgo.app.domain.ReportRules
import com.fuelgo.app.domain.model.Report
import com.fuelgo.app.domain.model.ReportStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v2 crowdsourced reports. Backed by Firestore when the app ships with Firebase
 * configured; fully optional at runtime — when unconfigured, [available] is
 * false and all operations degrade gracefully (v1 hours-derived status only).
 */
class ReportRepository(
    private val context: Context,
    private val reportDao: ReportDao,
    private val prefs: UserPrefs,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    val available: Boolean
        get() = runCatching { FirebaseApp.getApps(context) }.getOrDefault(emptyList()).isNotEmpty()

    private fun firestore(): FirebaseFirestore? =
        if (available) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    /**
     * Submits a report after client-side sanity checks (distance + rate limit).
     * Returns null on success, or a user-facing rejection reason.
     */
    suspend fun submit(
        stationId: String,
        status: ReportStatus,
        userLat: Double?,
        userLon: Double?,
        stationLat: Double,
        stationLon: Double,
    ): String? = withContext(Dispatchers.IO) {
        val now = clock()

        if (userLat != null && userLon != null) {
            val distance = Geo.haversineMeters(userLat, userLon, stationLat, stationLon)
            if (!ReportRules.withinRange(distance)) {
                return@withContext "Too far from this pump to report"
            }
        }

        val last = prefs.lastReportAt(stationId)
        if (!ReportRules.canReport(last, now)) {
            return@withContext "Already reported recently — thanks!"
        }

        val report = Report(stationId, status, now)
        // Always cache locally so the status shows immediately, even offline.
        reportDao.upsert(ReportEntity(report.stationId, report.atMs, report.status.name))
        prefs.markReported(stationId, now)

        val fs = firestore()
        if (fs != null) {
            val deviceId = prefs.deviceId()
            val ok = runCatching {
                fs.collection(COLLECTION)
                    .add(
                        mapOf(
                            "stationId" to stationId,
                            "deviceId" to deviceId,
                            "status" to status.name,
                            "timestamp" to now,
                        ),
                    )
                    .awaitTask()
            }.isSuccess
            if (!ok) return@withContext "Saved on this device — couldn't reach the network"
        }
        null
    }

    /**
     * Fresh reports for the given stations: Firestore when available (chunked
     * to respect `whereIn` limits), else the local cache. Results are mirrored
     * into Room so the widget and offline reads work.
     */
    suspend fun recentReports(stationIds: List<String>): Map<String, List<Report>> =
        withContext(Dispatchers.IO) {
            if (stationIds.isEmpty()) return@withContext emptyMap()
            val since = clock() - com.fuelgo.app.domain.StatusAggregator.DEFAULT_WINDOW_MS
            val fs = firestore()
            val remote: List<Report> = if (fs != null) {
                stationIds.chunked(10).flatMap { chunk ->
                    runCatching {
                        fs.collection(COLLECTION)
                            .whereGreaterThan("timestamp", since)
                            .whereIn("stationId", chunk)
                            .limit(200)
                            .get()
                            .awaitTask()
                            .documents
                            .mapNotNull { doc ->
                                Report(
                                    stationId = doc.getString("stationId") ?: return@mapNotNull null,
                                    status = doc.getString("status")
                                        ?.let { s -> runCatching { ReportStatus.valueOf(s) }.getOrNull() }
                                        ?: return@mapNotNull null,
                                    atMs = doc.getLong("timestamp") ?: return@mapNotNull null,
                                )
                            }
                    }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }
            if (remote.isNotEmpty()) {
                reportDao.upsertAll(remote.map { ReportEntity(it.stationId, it.atMs, it.status.name) })
            }
            val cached = reportDao.forStationsSince(stationIds, since)
                .map { Report(it.stationId, ReportStatus.valueOf(it.status), it.atMs) }
            (remote + cached).distinctBy { it.stationId to it.atMs }.groupBy { it.stationId }
        }

    suspend fun pruneOld() = withContext(Dispatchers.IO) {
        reportDao.pruneOlderThan(clock() - 24 * 60 * 60 * 1000L)
    }

    private companion object {
        const val COLLECTION = "reports"
    }
}

/** Minimal Task await without adding the coroutines-play-services dependency. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
