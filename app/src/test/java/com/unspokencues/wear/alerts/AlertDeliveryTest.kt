package com.unspokencues.wear.alerts

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AlertDeliveryTest {
    private val current = AlertCursor(UUID.randomUUID(), 100)
    @Test fun baselineDuplicatesDelayedRepliesAndAccountChangesAreSilent() {
        assertFalse(shouldPost(null, current))
        assertFalse(shouldPost(current, current))
        assertFalse(shouldPost(current, current.copy(micros = 99)))
        assertFalse(shouldPost(current, current.copy(generation = UUID.randomUUID(), micros = 200)))
        assertFalse(shouldPost(current, null))
        assertTrue(shouldPost(current, current.copy(micros = 101)))
    }

    @Test fun compatibleWithPhoneFrame() {
        val sample = ByteArray(24) { it.toByte() }
        val cursor = AlertCursor.decode(sample)!!
        assertEquals("00010203-0405-0607-0809-0a0b0c0d0e0f", cursor.generation.toString())
        assertEquals(0x1011121314151617L, cursor.micros)
        assertArrayEquals(sample, cursor.encode())
        assertNull(AlertCursor.decode(byteArrayOf()))
        assertThrows(IllegalArgumentException::class.java) { AlertCursor.decode(ByteArray(25)) }
    }
}
