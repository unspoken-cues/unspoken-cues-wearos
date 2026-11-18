package com.unspokencues.wear.alerts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val PERIODIC_JOB = 14820
internal const val BASELINE_JOB = 14821
internal const val POLL_INTERVAL_MS = 15L * 60 * 1000

internal fun ensurePeriodicPoll(context: Context) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    if (scheduler.getPendingJob(PERIODIC_JOB) == null) {
        val job = JobInfo.Builder(PERIODIC_JOB, ComponentName(context, AlertPollJob::class.java))
            .setPeriodic(POLL_INTERVAL_MS).setPersisted(true).build()
        check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS)
    }
}

internal fun scheduleAlertPolls(context: Context) {
    ensurePeriodicPoll(context)
    val job = JobInfo.Builder(BASELINE_JOB, ComponentName(context, AlertPollJob::class.java))
        .setMinimumLatency(0).build()
    check(context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS)
}

internal fun cancelAlertPolls(context: Context) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    scheduler.cancel(BASELINE_JOB)
    scheduler.cancel(PERIODIC_JOB)
}

class AlertPollJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch {
            val retry = try {
                withTimeout(45_000) { poll() }
                false
            } catch (_: TimeoutCancellationException) {
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            jobs.remove(params.jobId)
            jobFinished(params, retry)
        }
        return true
    }

    private suspend fun poll() {
        val alerts = AnnouncementAlerts(this)
        val subscription = alerts.read() ?: return
        if (!alerts.allowed()) { alerts.disable(); return }
        val capability = Wearable.getCapabilityClient(this)
            .getCapability("uc_announcement_source_v1", CapabilityClient.FILTER_REACHABLE).awaitValue()
        // Do not combine results from multiple phones. Try again when there is one reachable source.
        val phone = capability.nodes.single()
        val result = Wearable.getMessageClient(this)
            .sendRequest(phone.id, ALERT_PATH, subscription.cursor?.encode() ?: byteArrayOf()).awaitValue()
        alerts.receive(subscription, AlertCursor.decode(result))
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
