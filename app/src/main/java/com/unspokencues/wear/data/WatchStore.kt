package com.unspokencues.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.unspokencues.wear.model.*

/** One atomic preference snapshot shared by the activity and background sync service. */
class WatchStore(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("watch_state", Context.MODE_PRIVATE)

    fun read(): WatchState = synchronized(monitor) {
        val profile = SharedProfile(preferences.getString("name", "").orEmpty(),
            preferences.getString("token", "").orEmpty(), preferences.getBoolean("public", false))
        WatchState(CueStatus.fromWire(preferences.getString("status", null)),
            profile.takeIf { preferences.getBoolean("has_profile", false) }, preferences.getBoolean("locked", true),
            preferences.getLong("received", 0), preferences.getLong("revision", 0),
            preferences.getString("publisher", "").orEmpty())
    }

    fun saveLocal(state: WatchState) = synchronized(monitor) {
        // Don't let an old UI callback overwrite a newer phone snapshot.
        val current = read()
        if (state.revision != current.revision || state.publisherId != current.publisherId) return@synchronized
        preferences.edit().putString("status", state.status?.name)
            .putBoolean("locked", state.locked).apply()
    }

    fun receive(snapshot: PhoneSnapshot, revision: Long, publisherId: String) = synchronized(monitor) {
        val current = read()
        val retired = preferences.getStringSet("retired_publishers", emptySet()).orEmpty().toMutableSet()
        if (!SnapshotOrder(current.publisherId, current.revision, retired).accepts(publisherId, revision)) return@synchronized
        if (current.publisherId.isNotBlank() && publisherId != current.publisherId) retired.add(current.publisherId)
        val state = current.receive(snapshot, System.currentTimeMillis(), revision)
        preferences.edit().putString("status", state.status?.name)
            .putBoolean("locked", state.locked).putBoolean("has_profile", state.profile != null)
            .putString("name", state.profile?.name)
            .putString("token", state.profile?.token).putBoolean("public", state.profile?.isPublic == true)
            .putLong("received", state.receivedAt).putLong("revision", revision)
            .putString("publisher", publisherId).putStringSet("retired_publishers", retired).apply()
    }

    private companion object { val monitor = Any() }
}
