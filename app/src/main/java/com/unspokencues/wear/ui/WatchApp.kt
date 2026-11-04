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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.unspokencues.wear.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class Screen { STATUS, OPTIONS, GUIDE, UNLOCK, SELECT, SHARE }

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
            Screen.STATUS -> StatusScreen(state) { screen = Screen.OPTIONS }
            Screen.OPTIONS -> WatchColumn {
                Heading("Options")
                Action("Share profile", enabled = state.shareLink != null) { screen = Screen.SHARE }
                Action(if (state.locked) "Color locked" else "Change color") {
                    screen = if (state.locked) Screen.UNLOCK else Screen.SELECT
                }
                Action("Cue guide") { screen = Screen.GUIDE }
                if (state.shareLink == null) Body(
                    if (state.profile?.isPublic == false) "Profile private. Edit on phone."
                    else "Sync your profile from your phone.")
                if (state.receivedAt > 0) Body(
                    "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.receivedAt))}")
                Action("Back to cue") { screen = Screen.STATUS }
            }
            Screen.GUIDE -> WatchColumn {
                Heading("Cue guide")
                CueStatus.entries.forEach { cue ->
                    CueIdentity(cue, Color(0xFFFCFBF8), compact = true)
                    Spacer(Modifier.height(12.dp))
                }
                Action("Back to cue") { screen = Screen.STATUS }
            }
            Screen.UNLOCK -> WatchColumn {
                Heading("Change color?")
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
                CueStatus.entries.forEach { cue ->
                    Chip(onClick = { onStateChange(state.select(cue)); screen = Screen.STATUS },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF252831)),
                        icon = { CueSymbol(cue, Color(0xFFFCFBF8), Modifier.size(24.dp)) },
                        label = { Text(cue.label) }, secondaryLabel = { Text(cue.shortMeaning) })
                }
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
private fun StatusScreen(state: WatchState, onOptions: () -> Unit) {
    val cue = state.status
    Box(Modifier.fillMaxSize().testTag("status")
        .background(cue?.let { Color(it.argb) } ?: Color(0xFF15171C))) {
        // Scroll only when needed (for example, large system text). Keep all content
        // inside the round display and preserve normal system swipe navigation.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            if (cue == null) {
                Heading("Waiting for phone")
                Spacer(Modifier.height(8.dp))
                Body("Pair your watch, then sync in Unspoken Cues.")
                Spacer(Modifier.height(12.dp))
                Action("Options", onClick = onOptions)
            } else {
                val ink = Color(cue.ink)
                CueIdentity(cue, ink)
                Spacer(Modifier.height(12.dp))
                Chip(onClick = onOptions, modifier = Modifier.width(112.dp).heightIn(min = 48.dp),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF15171C),
                        contentColor = Color(0xFFFCFBF8)),
                    label = { Text("Options", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
            }
        }
    }
}

@Composable
private fun CueIdentity(cue: CueStatus, ink: Color, compact: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "${cue.label}. ${cue.meaning}. ${cue.symbolName} symbol."
        }) {
        CueSymbol(cue, ink, Modifier.size(if (compact) 32.dp else 52.dp).testTag("cue-symbol-${cue.name}"))
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(cue.label, color = ink, fontSize = if (compact) 20.sp else 28.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(if (compact) cue.meaning else cue.shortMeaning, color = ink,
            fontSize = 13.sp, textAlign = TextAlign.Center)
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
        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF252831), contentColor = Color(0xFFFCFBF8)),
        label = { Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
}
