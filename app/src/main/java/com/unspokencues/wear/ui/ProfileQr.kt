package com.unspokencues.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

// Same encoder/error correction as the phone, independent of UI for scanner round-trip tests.
internal fun qrModules(content: String): Array<BooleanArray>? = try {
    val matrix = Encoder.encode(content, ErrorCorrectionLevel.M).matrix
    Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 } }
} catch (_: WriterException) { null }

@Composable
fun ProfileQr(link: String, modifier: Modifier = Modifier) {
    val modules = remember(link) { qrModules(link) } ?: return
    Canvas(modifier.aspectRatio(1f).background(Color.White).testTag("profile-qr")
        .semantics { contentDescription = "Profile QR code. Scan with the Unspoken Cues phone app." }) {
        // Exactly four quiet modules on every edge; draw integer cells to avoid blurry seams.
        val cell = (size.minDimension / (modules.size + 8)).toInt().coerceAtLeast(1).toFloat()
        val origin = (size.minDimension - modules.size * cell) / 2f
        modules.forEachIndexed { y, row -> row.forEachIndexed { x, dark ->
            if (dark) drawRect(Color.Black, Offset(origin + x * cell, origin + y * cell), Size(cell, cell))
        } }
    }
}
