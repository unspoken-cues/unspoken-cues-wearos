package com.unspokencues.wear

import com.unspokencues.wear.ui.qrModules
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.Assert.*
import org.junit.Test

class QrTest {
    @Test fun watchQrDecodesToThePhoneCardLink() {
        val link = "unspokencues://card/0123456789abcdef0123456789abcdef"
        assertEquals(link, Decoder().decode(qrModules(link)!!).text)
    }
    @Test fun oversizedQrContentDoesNotCrashTheScreen() {
        assertNull(qrModules("x".repeat(5000)))
    }
}
