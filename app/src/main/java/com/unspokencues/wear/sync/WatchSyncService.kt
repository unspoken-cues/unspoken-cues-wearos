package com.unspokencues.wear.sync

import android.content.Context
import com.google.android.gms.wearable.*
import com.unspokencues.wear.data.WatchStore
import com.unspokencues.wear.model.PhoneSnapshot

const val STATE_PATH = "/unspoken-cues/state"

fun receiveDataItem(context: Context, item: DataItem) {
    if (item.uri.path != STATE_PATH) return
    val map = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
    if (map.getInt("schema") != 1) return
    val snapshot = PhoneSnapshot.fromWire(map.getString("status"), map.getString("name").orEmpty(),
        map.getString("token").orEmpty(), map.getBoolean("public"), map.getBoolean("active")) ?: return
    WatchStore(context).receive(snapshot, map.getLong("revision"), map.getString("publisher").orEmpty())
}

class WatchSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { if (it.type == DataEvent.TYPE_CHANGED) receiveDataItem(this, it.dataItem) }
    }
}
