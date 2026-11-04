package com.unspokencues.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.unspokencues.wear.model.CueStatus

internal val CueStatus.shortMeaning: String
    get() = when (this) {
        CueStatus.GREEN -> "Open to connect"
        CueStatus.YELLOW -> "Proceed with care"
        CueStatus.RED -> "Give me space"
        CueStatus.PURPLE -> "Maybe open to connect"
    }

internal val CueStatus.symbolName: String
    get() = when (this) {
        CueStatus.GREEN -> "Check mark"
        CueStatus.YELLOW -> "Caution triangle"
        CueStatus.RED -> "Stop octagon"
        CueStatus.PURPLE -> "Question mark"
    }

/** Monochrome shapes stay distinct without color vision and do not rely on emoji fonts.
 * The parent supplies the complete spoken cue, so these marks are decorative to TalkBack.
 */
@Composable
internal fun CueSymbol(cue: CueStatus, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension
        val stroke = Stroke(unit * .075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(vararg points: Pair<Float, Float>, closed: Boolean = false): Path = Path().apply {
            moveTo(points.first().first * size.width, points.first().second * size.height)
            points.drop(1).forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
            if (closed) close()
        }
        when (cue) {
            CueStatus.GREEN -> drawPath(path(.18f to .52f, .41f to .75f, .82f to .25f), ink, style = stroke)
            CueStatus.YELLOW -> {
                drawPath(path(.5f to .12f, .91f to .85f, .09f to .85f, closed = true), ink, style = stroke)
                drawLine(ink, Offset(size.width * .5f, size.height * .39f),
                    Offset(size.width * .5f, size.height * .58f), stroke.width, StrokeCap.Round)
                drawCircle(ink, unit * .04f, Offset(size.width * .5f, size.height * .72f))
            }
            CueStatus.RED -> {
                drawPath(path(.33f to .1f, .67f to .1f, .9f to .33f, .9f to .67f,
                    .67f to .9f, .33f to .9f, .1f to .67f, .1f to .33f, closed = true), ink, style = stroke)
                drawLine(ink, Offset(size.width * .32f, size.height * .5f),
                    Offset(size.width * .68f, size.height * .5f), stroke.width, StrokeCap.Round)
            }
            CueStatus.PURPLE -> {
                val question = Path().apply {
                    moveTo(size.width * .28f, size.height * .3f)
                    cubicTo(size.width * .28f, size.height * .05f, size.width * .76f,
                        size.height * .05f, size.width * .76f, size.height * .32f)
                    cubicTo(size.width * .76f, size.height * .5f, size.width * .5f,
                        size.height * .48f, size.width * .5f, size.height * .65f)
                }
                drawPath(question, ink, style = stroke)
                drawCircle(ink, unit * .045f, Offset(size.width * .5f, size.height * .85f))
            }
        }
    }
}
