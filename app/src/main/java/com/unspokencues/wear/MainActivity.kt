package com.unspokencues.wear

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.Wearable
import com.unspokencues.wear.data.WatchStore
import com.unspokencues.wear.sync.publishPending
import com.unspokencues.wear.sync.receiveDataItem
import com.unspokencues.wear.ui.WatchApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = WatchStore(this)
        // Recover already-synced data even if the background service wasn't running at delivery.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    publishPending(this@MainActivity)
                    Wearable.getDataClient(this@MainActivity).dataItems
                        .addOnSuccessListener { items ->
                            try { items.forEach { receiveDataItem(this@MainActivity, it) } }
                            finally { items.release() }
                        }.addOnFailureListener { /* Cached status remains usable offline. */ }
                    delay(30_000)
                }
            }
        }
        setContent { StoredWatchApp(store) { publishPending(this) } }
    }
}

@Composable
internal fun StoredWatchApp(store: WatchStore, onPending: () -> Unit) {
    var state by remember { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state = store.read() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        // Close the gap between initial read and listener registration.
        state = store.read()
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    WatchApp(state, onStateChange = store::saveLock, onCueSelected = { cue ->
        if (store.select(state, cue)) onPending()
    })
}
