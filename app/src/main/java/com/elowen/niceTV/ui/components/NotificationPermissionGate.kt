package com.elowen.niceTV.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberNotificationPermissionGate(
    deniedMessage: String = "未授予通知权限，后台状态通知可能不可见"
): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (!granted) {
            Toast.makeText(context, deniedMessage, Toast.LENGTH_SHORT).show()
        }
        action?.invoke()
    }

    return { action ->
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
