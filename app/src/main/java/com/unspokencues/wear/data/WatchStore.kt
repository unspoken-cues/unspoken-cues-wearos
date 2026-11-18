package com.unspokencues.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.unspokencues.wear.model.*
import java.util.UUID

/** One atomic preference snapshot shared by the activity and background sync service. */
class WatchStore(val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("watch_state", Context.MODE_PRIVATE))

    fun read(): WatchState = synchronized(monitor) {
        val profile = SharedProfile(preferences.getString("name", "").orEmpty(),
            preferences.getString("token", "").orEmpty(), preferences.getBoolean("public", false))
        val status = CueStatus.fromWire(preferences.getString("status", null))
        val generation = preferences.getString("generation", null)
        val authority = generation?.let { CueAuthority(it, preferences.getLong("cue_revision", 0)) }
        val operation = preferences.getString("pending_operation", null)
        val pending = if (operation != null && generation != null && status != null) {
            CueRequest(generation, preferences.getLong("pending_base", 0), operation, status,
                preferences.getString("writer", "").orEmpty(), preferences.getLong("sequence", 0))
        } else null
        WatchState(status, profile.takeIf { preferences.getBoolean("has_profile", false) },
            preferences.getBoolean("locked", true), preferences.getLong("received", 0),
            preferences.getLong("revision", 0), preferences.getString("publisher", "").orEmpty(),
            authority, pending, CueResult.entries.firstOrNull { it.name == preferences.getString("result", null) })
    }

    fun saveLock(state: WatchState) = synchronized(monitor) {
        if (sameSnapshot(state, read())) preferences.edit().putBoolean("locked", state.locked).apply()
    }

    fun select(origin: WatchState, cue: CueStatus): Boolean = synchronized(monitor) {
        val current = read()
        if (current.locked || origin.locked || !sameSnapshot(origin, current)) return@synchronized false
        val edit = preferences.edit().putString("status", cue.name).putBoolean("locked", true).remove("result")
        if (current.authority != null) {
            val writer = preferences.getString("writer", null) ?: UUID.randomUUID().toString()
            val sequence = Math.addExact(preferences.getLong("sequence", 0), 1)
            edit.putString("writer", writer).putLong("sequence", sequence)
                .putString("pending_operation", UUID.randomUUID().toString())
                .putLong("pending_base", current.pending?.baseRevision ?: current.authority.revision)
        }
        // Persist the choice and its identity before offering it to the Data Layer.
        edit.commit()
    }

    fun receive(snapshot: PhoneSnapshot, revision: Long, publisherId: String) = synchronized(monitor) {
        val current = read()
        val retired = preferences.getStringSet("retired_publishers", emptySet()).orEmpty().toMutableSet()
        if (!SnapshotOrder(current.publisherId, current.revision, retired).accepts(publisherId, revision)) return@synchronized
        val previous = current.authority
        val next = snapshot.authority
        if (previous != null && next != null && previous.generation == next.generation &&
            next.revision < previous.revision) return@synchronized
        if (current.publisherId.isNotBlank() && publisherId != current.publisherId) retired.add(current.publisherId)
        val state = current.receive(snapshot, System.currentTimeMillis(), revision)
        preferences.edit().putString("status", state.status?.name)
            .putBoolean("locked", true).putBoolean("has_profile", state.profile != null)
            .putString("name", state.profile?.name)
            .putString("token", state.profile?.token).putBoolean("public", state.profile?.isPublic == true)
            .putString("generation", next?.generation).putLong("cue_revision", next?.revision ?: 0)
            .putString("result", state.result?.name)
            .putString("pending_operation", state.pending?.operation).putLong("pending_base", state.pending?.baseRevision ?: 0)
            .putLong("received", state.receivedAt).putLong("revision", revision)
            .putString("publisher", publisherId).putStringSet("retired_publishers", retired).apply()
    }

    private fun sameSnapshot(a: WatchState, b: WatchState) =
        a.revision == b.revision && a.publisherId == b.publisherId && a.pending == b.pending

    private companion object { val monitor = Any() }
}
