package com.unspokencues.wear.model

import java.util.UUID

/** The phone assigns a new opaque generation to each authenticated account session. */
data class CueAuthority(val generation: String, val revision: Long, val receipt: CueReceipt? = null)

data class CueReceipt(val operation: String, val writer: String, val baseRevision: Long, val sequence: Long)

enum class CueResult { SAVED, SUPERSEDED }

data class CueRequest(
    val generation: String,
    val baseRevision: Long,
    val operation: String,
    val status: CueStatus,
    val writer: String,
    val sequence: Long,
) {
    fun confirmedBy(snapshot: PhoneSnapshot): Boolean = snapshot.status == status &&
        snapshot.authority?.generation == generation &&
        snapshot.authority.receipt == CueReceipt(operation, writer, baseRevision, sequence)

    /** Preserve a newer local selection while a receipt for its predecessor is in transit. */
    fun awaitsCommit(authority: CueAuthority): Boolean {
        if (generation != authority.generation) return false
        if (baseRevision == authority.revision) return true
        val receipt = authority.receipt ?: return false
        return receipt.writer == writer && receipt.baseRevision == baseRevision && receipt.sequence < sequence
    }
}

fun validCueId(value: String): Boolean = value.length == 36 &&
    runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
