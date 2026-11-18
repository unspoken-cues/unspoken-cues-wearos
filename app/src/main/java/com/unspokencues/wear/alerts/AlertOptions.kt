package com.unspokencues.wear.alerts

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text

@Composable
internal fun AlertOptions() {
    val context = LocalContext.current
    val alerts = remember(context) { AnnouncementAlerts(context) }
    val owner = LocalLifecycleOwner.current
    var subscription by remember { mutableStateOf(alerts.read()) }
    var blocked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && alerts.allowed()) alerts.enable() else blocked = true
    }
    DisposableEffect(alerts, owner) {
        alerts.createChannel()
        val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> subscription = alerts.read() }
        val resumed = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alerts.reconcile()
                blocked = !alerts.allowed()
            }
        }
        alerts.prefs.registerOnSharedPreferenceChangeListener(changed)
        owner.lifecycle.addObserver(resumed)
        subscription = alerts.read()
        onDispose {
            alerts.prefs.unregisterOnSharedPreferenceChangeListener(changed)
            owner.lifecycle.removeObserver(resumed)
        }
    }

    Text(when {
        subscription == null -> "Get a discreet notification when a host posts in an event you’ve joined."
        subscription?.cursor == null -> "Waiting for your signed-in phone to start checking."
        else -> "Checking for new announcements about every 15 minutes."
    }, textAlign = TextAlign.Center)
    Chip(onClick = {
        if (subscription != null) alerts.disable()
        else if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (alerts.allowed()) alerts.enable() else blocked = true
    }, modifier = Modifier.fillMaxWidth(), label = { Text(if (subscription == null) "Enable alerts" else "Disable alerts") })
    if (blocked && subscription == null) {
        Chip(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth(), label = { Text("Notification settings") })
    }
}
