package com.photomap.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MediaPermissionGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val requestedPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaPermissions + Manifest.permission.POST_NOTIFICATIONS
        } else {
            mediaPermissions
        }
    }
    var granted by remember {
        mutableStateOf(
            mediaPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    var notificationRequestAttempted by remember { mutableStateOf(false) }
    var locationRequestAttempted by remember { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        locationRequestAttempted = true
        if (allowed) onGranted()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationRequestAttempted = true
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        notificationRequestAttempted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        granted = mediaPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(granted) {
        if (granted) {
            onGranted()
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !locationRequestAttempted &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                locationRequestAttempted = true
                locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !notificationRequestAttempted &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationRequestAttempted = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Text(
                "Allow media access to discover and sync your local photos and videos.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 20.dp),
            )
            Button(
                onClick = {
                    notificationRequestAttempted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    launcher.launch(requestedPermissions)
                },
            ) {
                Text("Allow media access")
            }
        }
    }
}
