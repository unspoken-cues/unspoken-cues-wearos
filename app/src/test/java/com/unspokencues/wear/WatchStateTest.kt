package com.unspokencues.wear

import com.unspokencues.wear.model.*
import org.junit.Assert.*
import org.junit.Test

class WatchStateTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val connected = WatchState(status = CueStatus.YELLOW,
        profile = SharedProfile("Alex", token, true), receivedAt = 100)

    @Test fun lockedColorRejectsSelection() {
        assertEquals(CueStatus.YELLOW, connected.select(CueStatus.RED).status)
    }
    @Test fun confirmedUnlockAllowsSelectionAndRelocks() {
        val selected = connected.unlock().select(CueStatus.RED)
        assertEquals(CueStatus.RED, selected.status)
        assertTrue(selected.locked)
    }
    @Test fun openingSharingDoesNotUnlockOrChangeStatus() {
        assertEquals("unspokencues://card/$token", connected.shareLink)
        assertTrue(connected.locked)
        assertEquals(CueStatus.YELLOW, connected.status)
    }
    @Test fun privateMissingAndInvalidProfilesCannotBeShared() {
        assertNull(connected.copy(profile = connected.profile!!.copy(isPublic = false)).shareLink)
        assertNull(connected.copy(profile = null).shareLink)
        assertNull(connected.copy(profile = SharedProfile("Alex", "bad", true)).shareLink)
    }
    @Test fun phoneUpdateAppliesWhileLockedAndKeepsLock() {
        val updated = connected.receive(PhoneSnapshot(CueStatus.PURPLE, connected.profile), 200)
        assertEquals(CueStatus.PURPLE, updated.status)
        assertTrue(updated.locked)
        assertEquals(200L, updated.receivedAt)
    }
    @Test fun signOutClearsPreviousIdentityAndStatus() {
        val updated = connected.unlock().receive(PhoneSnapshot(null, null), 200)
        assertNull(updated.status)
        assertNull(updated.shareLink)
        assertTrue(updated.locked)
    }
    @Test fun invalidWireStatusIsRejectedRatherThanDefaultingToGreen() {
        assertNull(PhoneSnapshot.fromWire("NOT_A_CUE", "Alex", token, true, true))
    }
    @Test fun uppercaseTokensAreNormalizedAndWrongLengthTokensAreRejected() {
        assertEquals("unspokencues://card/$token", SharedProfile("Alex", token.uppercase(), true).link)
        assertNull(SharedProfile("Alex", token + "0", true).link)
    }
    @Test fun privateSnapshotWithoutTokenExplainsWhySharingIsUnavailable() {
        val snapshot = PhoneSnapshot.fromWire("RED", "Alex", "", false, true)!!
        assertEquals(false, snapshot.profile?.isPublic)
        assertNull(connected.receive(snapshot, 200).shareLink)
    }
    @Test fun resetPhonePublisherCanReplaceFutureDatedSnapshot() {
        val gate = SnapshotOrder("old-install", 9_999_999, emptySet())
        assertTrue(gate.accepts("new-install", 1))
        assertFalse(gate.accepts("old-install", 1))
    }
    @Test fun retiredPublisherCannotRestoreStaleIdentity() {
        val gate = SnapshotOrder("new-install", 2, setOf("old-install"))
        assertFalse(gate.accepts("old-install", 10_000_000))
        assertFalse(gate.accepts("", 10))
        assertFalse(gate.accepts("new-install", 2))
        assertTrue(gate.accepts("new-install", 3))
    }
}
