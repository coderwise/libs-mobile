package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.permissions.PermissionStatus
import com.coderwise.libs.permissions.isDeadEndAfter
import com.coderwise.libs.permissions.rememberAppSettingsLauncher
import com.coderwise.libs.permissions.rememberCameraPermissionState
import com.coderwise.libs.permissions.rememberNotificationPermissionState
import com.coderwise.libs.permissions.shouldShowRationale

@Composable
internal fun PermissionsExample() {
    val camera = rememberCameraPermissionState()
    val notifications = rememberNotificationPermissionState()
    val appSettings = rememberAppSettingsLauncher()
    // Which permission, if any, the OS has stopped prompting for. A request that came back
    // unchanged and with no rationale left showed the user nothing at all — the button that
    // appears to do nothing, and the only case that needs the settings page.
    var deadEnd by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PermissionRow(
            name = "Camera",
            status = camera.status,
            onRequest = {
                val before = camera.status
                camera.launchPermissionRequest { after ->
                    deadEnd = if (after.isDeadEndAfter(before)) "Camera" else null
                }
            }
        )
        PermissionRow(
            name = "Notifications",
            status = notifications.status,
            onRequest = {
                val before = notifications.status
                notifications.launchPermissionRequest { after ->
                    deadEnd = if (after.isDeadEndAfter(before)) "Notifications" else null
                }
            }
        )
        deadEnd?.let { stuck ->
            DemoSection("Nowhere left to ask") {
                Text(
                    "$stuck was answered from a decision already on file — the OS showed the " +
                        "user nothing. The settings page is the only way forward."
                )
                OutlinedButton(onClick = { appSettings.launch() }) { Text("Open app settings") }
            }
        }
        PlatformNote(
            "The sample app declares CAMERA and POST_NOTIFICATIONS itself; the library declares " +
                "no Android permission, so an app using one state here never ships the others. " +
                "Desktop has no runtime permission model at all — both read Granted, and the " +
                "settings launcher does nothing. The browser has one for the camera, where " +
                "requesting means opening a media stream, but not for notifications. A grant " +
                "made over in the settings app arrives back as a status change on resume."
        )
    }
}

@Composable
private fun PermissionRow(name: String, status: PermissionStatus, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name)
            Text(status.describe(), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onRequest) { Text("Request") }
    }
}

private fun PermissionStatus.describe(): String = when (this) {
    is PermissionStatus.Granted -> "Granted"
    is PermissionStatus.Denied ->
        if (shouldShowRationale) "Denied — a rationale is still worth showing"
        else "Denied — no rationale to show"
}
