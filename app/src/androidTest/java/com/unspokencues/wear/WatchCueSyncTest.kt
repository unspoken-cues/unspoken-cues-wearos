package com.unspokencues.wear

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wearable.DataMap
import com.unspokencues.wear.data.WatchStore
import com.unspokencues.wear.model.*
import com.unspokencues.wear.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class WatchCueSyncTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("cue_sync_test", Context.MODE_PRIVATE)
    private val store get() = WatchStore(prefs)
    private val generation = UUID.randomUUID().toString()

    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun clean() { prefs.edit().clear().commit() }

    private fun snapshot(revision: Long, cueRevision: Long, cue: CueStatus, receipt: CueRequest? = null) = DataMap().apply {
        putInt("schema", 1)
        putString("publisher", "test-install")
        putLong("revision", revision)
        putBoolean("active", true)
        putString("status", cue.name)
        putString("name", "Test")
        putBoolean("public", false)
        putString("token", "")
        putString("generation", generation)
        putLong("cueRevision", cueRevision)
        putString("receipt", receipt?.operation.orEmpty())
        putString("receiptWriter", receipt?.writer.orEmpty())
        putLong("receiptBase", receipt?.baseRevision ?: 0)
        putLong("receiptSequence", receipt?.sequence ?: 0)
    }

    @Test fun everyPickerChoicePassesThroughOutboxAndWaitsForReceipt() {
        val durableStore = store
        val outbox = CueOutbox()
        val transmitted = mutableListOf<DataMap>()
        receiveSnapshot(store, snapshot(1, 1, CueStatus.GREEN))
        compose.setContent {
            StoredWatchApp(durableStore) {
                runBlocking {
                    outbox.flush(durableStore) { request ->
                        val put = request.toDataRequest()
                        assertEquals(CUE_REQUEST_PATH, put.uri.path)
                        transmitted += DataMap.fromByteArray(put.data!!)
                    }
                }
            }
        }

        CueStatus.entries.forEachIndexed { index, cue ->
            for (label in listOf("Options", "Color locked", "Unlock color", cue.label)) {
                compose.onNodeWithText(label).performScrollTo().performClick()
            }
            compose.onNodeWithText("Waiting for phone to save").assertExists()
            compose.runOnIdle {
                val data = transmitted.last()
                val request = store.read().pending!!
                assertEquals(index + 1, transmitted.size)
                assertEquals(cue.name, data.getString("status"))
                assertEquals(setOf("schema", "generation", "operation", "status", "writer",
                    "baseRevision", "sequence"), data.keySet())
                assertEquals(generation, data.getString("generation"))
                assertEquals(request.operation, data.getString("operation"))
                assertEquals(request.writer, data.getString("writer"))
                assertEquals(index + 1L, data.getLong("baseRevision"))
                assertEquals(index + 1L, data.getLong("sequence"))
                receiveSnapshot(store, snapshot(index + 2L, index + 2L, cue, request))
                assertNull(store.read().pending)
                assertEquals(CueResult.SAVED, store.read().result)
                assertNull(store.read().shareLink)
            }
            compose.onNodeWithText("Waiting for phone to save").assertDoesNotExist()
            compose.onNodeWithContentDescription(cue.label + ".", substring = true).assertExists()
        }
    }

    private fun select(cue: CueStatus) {
        store.saveLock(store.read().unlock())
        assertTrue(store.select(store.read(), cue))
    }

    @Test fun failedTransportRestoresLatestIntentAfterRestartAndSupersedesOutOfOrderReceipts() = runBlocking {
        receiveSnapshot(store, snapshot(1, 1, CueStatus.GREEN))
        select(CueStatus.RED)
        val first = store.read().pending!!
        assertTrue(runCatching { CueOutbox().flush(store) { throw IOException("offline") } }.isFailure)
        assertNull(store.read().result)
        select(CueStatus.YELLOW)
        select(CueStatus.PURPLE)
        val reopened = WatchStore(prefs)
        val latest = reopened.read().pending!!
        CueOutbox().flush(reopened) { assertEquals(latest, it) }
        assertEquals(first.baseRevision, latest.baseRevision)
        assertEquals(first.writer, latest.writer)
        assertEquals(first.sequence + 2, latest.sequence)

        receiveSnapshot(store, snapshot(2, 2, CueStatus.RED, first))
        assertEquals(latest, store.read().pending)
        receiveSnapshot(store, snapshot(3, 2, CueStatus.RED, first))
        assertEquals(CueStatus.PURPLE, store.read().status)
        receiveSnapshot(store, snapshot(4, 3, CueStatus.PURPLE, latest))
        assertNull(store.read().pending)
        receiveSnapshot(store, snapshot(2, 2, CueStatus.RED, first))
        assertEquals(CueStatus.PURPLE, store.read().status)
        CueOutbox().flush(store) { fail("A confirmed operation was retried") }
        Unit
    }

    @Test fun malformedAndLegacyExtensionsCannotAuthorizeRequestsAndSignOutClearsPending() {
        val legacy = snapshot(1, 1, CueStatus.YELLOW).apply {
            listOf("generation", "cueRevision", "receipt", "receiptBase", "receiptWriter", "receiptSequence").forEach { remove(it) }
        }
        receiveSnapshot(store, legacy)
        assertEquals(CueStatus.YELLOW, store.read().status)
        select(CueStatus.GREEN)
        assertNull(store.read().pending)
        receiveSnapshot(store, snapshot(2, 1, CueStatus.YELLOW).apply { putString("receiptBase", "0") })
        assertNull(store.read().authority)
        receiveSnapshot(store, snapshot(3, 1, CueStatus.YELLOW))
        select(CueStatus.PURPLE)
        receiveSnapshot(store, snapshot(4, 1, CueStatus.YELLOW).apply { putString("generation", UUID.randomUUID().toString()) })
        assertNull(store.read().pending)
        assertEquals(CueStatus.YELLOW, store.read().status)
        select(CueStatus.RED)
        val stale = store.read().unlock()
        receiveSnapshot(store, snapshot(5, 1, CueStatus.YELLOW).apply { putBoolean("active", false) })
        assertNull(store.read().status)
        assertNull(store.read().pending)
        assertNull(store.read().authority)
        store.saveLock(stale)
        assertFalse(store.select(stale, CueStatus.RED))
    }
}
