package com.ragaa.snapadb.feature.screenmirror

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ragaa.mirror.TouchInput
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MirrorPanel(viewModel: ScreenMirrorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is ScreenMirrorState.NoDevice -> NoDevicePanel()
        is ScreenMirrorState.Loading -> LoadingPanel()
        is ScreenMirrorState.Error -> ErrorPanel(s.message) { viewModel.onIntent(ScreenMirrorIntent.Retry) }
        is ScreenMirrorState.Ready -> MirrorPanelContent(
            state = s,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun MirrorPanelContent(
    state: ScreenMirrorState.Ready,
    onIntent: (ScreenMirrorIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mirror", style = MaterialTheme.typography.titleSmall)
            if (state.isMirroring) {
                Text(
                    "${state.mirrorFps} FPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Start/Stop button
        if (!state.isMirroring) {
            Button(
                onClick = { onIntent(ScreenMirrorIntent.StartMirror) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Mirror")
            }
        } else {
            Button(
                onClick = { onIntent(ScreenMirrorIntent.StopMirror) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop Mirror")
            }
        }

        // Mirror display
        state.mirrorFrame?.let { bitmap ->
            Spacer(modifier = Modifier.height(8.dp))

            var imageSize by remember { mutableStateOf(IntSize.Zero) }
            val deviceWidth = state.mirrorFrameWidth
            val deviceHeight = state.mirrorFrameHeight
            val aspectRatio = if (deviceWidth > 0 && deviceHeight > 0) {
                deviceWidth.toFloat() / deviceHeight
            } else {
                9f / 16f
            }

            var dragStart by remember { mutableStateOf(Offset.Zero) }
            var dragEnd by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .onSizeChanged { imageSize = it }
                    .pointerInput(deviceWidth, deviceHeight) {
                        detectTapGestures { offset ->
                            if (imageSize.width > 0 && imageSize.height > 0 && deviceWidth > 0) {
                                val scaleX = deviceWidth.toFloat() / imageSize.width
                                val scaleY = deviceHeight.toFloat() / imageSize.height
                                onIntent(
                                    ScreenMirrorIntent.MirrorTap(
                                        offset.x * scaleX,
                                        offset.y * scaleY,
                                    )
                                )
                            }
                        }
                    }
                    .pointerInput(deviceWidth, deviceHeight) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragEnd = offset
                            },
                            onDragEnd = {
                                if (imageSize.width > 0 && imageSize.height > 0 && deviceWidth > 0) {
                                    val scaleX = deviceWidth.toFloat() / imageSize.width
                                    val scaleY = deviceHeight.toFloat() / imageSize.height
                                    onIntent(
                                        ScreenMirrorIntent.MirrorSwipe(
                                            dragStart.x * scaleX,
                                            dragStart.y * scaleY,
                                            dragEnd.x * scaleX,
                                            dragEnd.y * scaleY,
                                        )
                                    )
                                }
                            },
                            onDragCancel = {},
                            onDrag = { change, _ ->
                                dragEnd = change.position
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Device screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            // Navigation buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FilledTonalIconButton(onClick = {
                    onIntent(ScreenMirrorIntent.MirrorKeyEvent(TouchInput.KeyEvent.KEYCODE_BACK))
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
                FilledTonalIconButton(onClick = {
                    onIntent(ScreenMirrorIntent.MirrorKeyEvent(TouchInput.KeyEvent.KEYCODE_HOME))
                }) {
                    Icon(Icons.Outlined.Home, contentDescription = "Home", modifier = Modifier.size(20.dp))
                }
                FilledTonalIconButton(onClick = {
                    onIntent(ScreenMirrorIntent.MirrorKeyEvent(TouchInput.KeyEvent.KEYCODE_RECENTS))
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = "Recents", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Hint when not mirroring
        if (!state.isMirroring && state.mirrorFrame == null) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Tap Start to stream\nyour device screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NoDevicePanel() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.DeviceUnknown,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
