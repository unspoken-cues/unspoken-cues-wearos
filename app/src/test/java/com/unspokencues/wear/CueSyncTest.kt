package com.unspokencues.wear

import com.unspokencues.wear.model.*
import org.junit.Assert.*
import org.junit.Test

class CueSyncTest {
    private val request = CueRequest("session", 5, "new", CueStatus.PURPLE, "watch", 3)
    private val initial = WatchState(status = CueStatus.PURPLE, authority = CueAuthority("session", 5),
        pending = request)

    private fun receive(revision: Long, cue: CueStatus, receipt: CueReceipt? = null, generation: String = "session") =
        initial.receive(PhoneSnapshot(cue, null, CueAuthority(generation, revision, receipt)), 100)

    @Test fun profileOnlyUpdatesAndLateReceiptsKeepLatestSelection() {
        val oldReceipt = CueReceipt("old", "watch", 5, 1)
        for (state in listOf(receive(5, CueStatus.GREEN), receive(6, CueStatus.YELLOW, oldReceipt))) {
            assertEquals(request, state.pending)
            assertEquals(CueStatus.PURPLE, state.status)
            assertNull(state.result)
        }
    }

    @Test fun exactReceiptConfirmsSameValueAndNewerPhoneChoiceSupersedes() {
        val saved = receive(6, CueStatus.PURPLE, CueReceipt("new", "watch", 5, 3))
        assertNull(saved.pending)
        assertEquals(CueResult.SAVED, saved.result)
        val replaced = receive(6, CueStatus.RED)
        assertNull(replaced.pending)
        assertEquals(CueResult.SUPERSEDED, replaced.result)
        assertEquals(CueStatus.RED, replaced.status)
        assertEquals(CueResult.SUPERSEDED, receive(6, CueStatus.PURPLE,
            CueReceipt("different", "watch", 5, 3)).result)
    }

    @Test fun changedGenerationAndLegacySnapshotsRetirePendingOperation() {
        val replaced = receive(1, CueStatus.GREEN, generation = "next")
        assertNull(replaced.pending)
        assertNull(replaced.result)
        assertEquals(CueStatus.GREEN, replaced.status)
        val legacy = initial.receive(PhoneSnapshot(CueStatus.RED, null), 100)
        assertNull(legacy.pending)
        assertNull(legacy.authority)
    }
}
