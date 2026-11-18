package com.unspokencues.wear.sync

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.unspokencues.wear.data.WatchStore
import com.unspokencues.wear.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val CUE_REQUEST_PATH = "/unspoken-cues/cue-request"

internal fun CueRequest.toDataRequest() = PutDataMapRequest.create(CUE_REQUEST_PATH).apply {
    dataMap.putInt("schema", 1)
    dataMap.putString("generation", generation)
    dataMap.putLong("baseRevision", baseRevision)
    dataMap.putString("operation", operation)
    dataMap.putString("status", status.name)
    dataMap.putString("writer", writer)
    dataMap.putLong("sequence", sequence)
}.asPutDataRequest().setUrgent()

/** Serialize puts to the same DataItem path, including when the activity and service overlap. */
internal class CueOutbox {
    private val mutex = Mutex()
    suspend fun flush(store: WatchStore, send: suspend (CueRequest) -> Unit) = mutex.withLock {
        store.read().pending?.let { send(it) }
    }
}

private val publisher = CueOutbox()
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun publishPending(context: Context) {
    val app = context.applicationContext
    scope.launch {
        try {
            publisher.flush(WatchStore(app)) {
                Wearable.getDataClient(app).putDataItem(it.toDataRequest()).awaitResult()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The persisted operation remains pending until an authoritative snapshot resolves it.
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal fun receiveSnapshot(store: WatchStore, map: DataMap) {
    if (map.getInt("schema") != 1) return
    val active = map.get<Any?>("active") as? Boolean ?: return
    val legacy = PhoneSnapshot.fromWire(map.getString("status"), map.getString("name").orEmpty(),
        map.getString("token").orEmpty(), map.getBoolean("public"), active) ?: return
    val hasSyncFields = map.keySet().any { it in syncFields }
    val authority = if (active && hasSyncFields) {
        val generation = map.get<Any?>("generation") as? String ?: return
        val revision = map.get<Any?>("cueRevision") as? Long ?: return
        if (!validCueId(generation) || revision <= 0) return
        val operation = map.get<Any?>("receipt") as? String ?: return
        val writer = map.get<Any?>("receiptWriter") as? String ?: return
        val base = map.get<Any?>("receiptBase") as? Long ?: return
        val sequence = map.get<Any?>("receiptSequence") as? Long ?: return
        val receipt = when {
            operation.isEmpty() && writer.isEmpty() && base == 0L && sequence == 0L -> null
            validCueId(operation) && validCueId(writer) && base in 1 until revision && sequence > 0 ->
                CueReceipt(operation, writer, base, sequence)
            else -> return
        }
        CueAuthority(generation, revision, receipt)
    } else null
    store.receive(legacy.copy(authority = authority), map.getLong("revision"), map.getString("publisher").orEmpty())
}

private val syncFields = setOf("generation", "cueRevision", "receipt", "receiptWriter", "receiptBase", "receiptSequence")
