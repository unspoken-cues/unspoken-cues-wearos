package com.unspokencues.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.unspokencues.wear.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class Screen { STATUS, UNLOCK, SELECT, SHARE }

@Composable
fun WatchApp(state: WatchState, onStateChange: (WatchState) -> Unit) {
    MaterialTheme {
        var screen by rememberSaveable { mutableStateOf(Screen.STATUS) }
        val link = state.shareLink
        // New phone data closes obsolete QR/selection screens, including sign-out and privacy changes.
        LaunchedEffect(state.publisherId, state.revision) { screen = Screen.STATUS }
        LaunchedEffect(screen, link) {
            if (screen == Screen.SHARE) {
                if (link == null) screen = Screen.STATUS
                else { delay(30_000); screen = Screen.STATUS }
            }
        }
        BackHandler(screen != Screen.STATUS) {
            onStateChange(state.copy(locked = true))
            screen = Screen.STATUS
        }
        when (screen) {
            Screen.STATUS -> StatusScreen(state,
                onShare = { screen = Screen.SHARE },
                onChange = { screen = if (state.locked) Screen.UNLOCK else Screen.SELECT })
            Screen.UNLOCK -> WatchColumn {
                Heading("Change your color?")
                Body("Swipes never change your cue. Unlock to choose a new color.")
                Action("Unlock color") {
                    onStateChange(state.unlock())
                    screen = Screen.SELECT
                }
                Action("Keep locked") { screen = Screen.STATUS }
            }
            Screen.SELECT -> WatchColumn {
                Heading("Watch color")
                Body("Phone updates replace this choice.")
                // Neutral buttons: never show four competing status colors together.
                CueStatus.entries.forEach { cue -> Action(cue.label) {
                    onStateChange(state.select(cue)); screen = Screen.STATUS
                } }
                Action("Cancel") {
                    onStateChange(state.copy(locked = true)); screen = Screen.STATUS
                }
            }
            Screen.SHARE -> {
                if (link != null) ShareScreen(link, state.profile?.name.orEmpty()) { screen = Screen.STATUS }
            }
        }
    }
}

@Composable
private fun StatusScreen(state: WatchState, onShare: () -> Unit, onChange: () -> Unit) {
    val cue = state.status
    WatchColumn(background = cue?.let { Color(it.argb) } ?: Color(0xFF15171C),
        modifier = Modifier.testTag("status")) {
        if (cue == null) {
            Heading("Your cue, on your wrist")
            Body("Pair your watch, then open Unspoken Cues on your phone and sign in.")
            Body("Your status and profile will appear here.")
        } else {
            val ink = Color(cue.ink)
            Text(cue.label, color = ink, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(cue.meaning, color = ink, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Action("Share profile", enabled = state.shareLink != null, onClick = onShare)
            Action(if (state.locked) "Color locked" else "Change color", onClick = onChange)
            if (state.shareLink == null) Text(
                if (state.profile?.isPublic == false) "Profile private · change on phone" else "Open phone to sync your profile",
                color = ink, fontSize = 11.sp, textAlign = TextAlign.Center)
            if (state.receivedAt > 0) Text(
                "Last phone update ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.receivedAt))}",
                color = ink, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ShareScreen(link: String, name: String, onClose: () -> Unit) {
    // The square stays inside the circle. Keep instructions outside the QR quiet zone.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        val nameWidth = maxWidth * .65f
        val qrSize = minOf(maxWidth, maxHeight) * .56f
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(name.ifBlank { "Your profile" }, color = Color.Black, fontSize = 11.sp,
                maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(nameWidth))
            ProfileQr(link, Modifier.size(qrSize))
            Text("Scan in Unspoken Cues", color = Color.Black, fontSize = 10.sp)
            Button(onClick = onClose, modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black)) {
                Text("Close", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun WatchColumn(modifier: Modifier = Modifier, background: Color = Color(0xFF15171C),
                        content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content)
}

@Composable private fun Heading(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center)
}
@Composable private fun Body(text: String) {
    Text(text, color = Color(0xFFEDEAE4), fontSize = 13.sp, textAlign = TextAlign.Center)
}
@Composable private fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Chip(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF15171C), contentColor = Color.White),
        label = { Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
}
