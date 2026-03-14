package com.ragaa.snapadb.core.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

@Composable
fun RightToolBar(
    isMirrorOpen: Boolean,
    onToggleMirror: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onToggleMirror,
            colors = if (isMirrorOpen) {
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                IconButtonDefaults.iconButtonColors()
            },
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ScreenShare,
                contentDescription = "Toggle Mirror Panel",
            )
        }
        Text(
            "Mirror",
            style = MaterialTheme.typography.labelSmall,
            color = if (isMirrorOpen) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.rotate(-90f).padding(top = 4.dp),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}
