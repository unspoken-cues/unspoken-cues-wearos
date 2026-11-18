package com.unspokencues.wear.sync

import android.content.Context
import com.google.android.gms.wearable.*
import com.unspokencues.wear.data.WatchStore

const val STATE_PATH = "/unspoken-cues/state"

fun receiveDataItem(context: Context, item: DataItem) {
    if (item.uri.path != STATE_PATH) return
    val map = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
    receiveSnapshot(WatchStore(context), map)
    publishPending(context)
}

class WatchSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { if (it.type == DataEvent.TYPE_CHANGED) receiveDataItem(this, it.dataItem) }
    }
}
