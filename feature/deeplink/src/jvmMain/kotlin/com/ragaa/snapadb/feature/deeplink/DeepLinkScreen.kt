package com.ragaa.snapadb.feature.deeplink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.feature.deeplink.model.DeepLinkItem
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeepLinkScreen(viewModel: DeepLinkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is DeepLinkState.NoDevice -> NoDeviceState("Connect a device to test deep links")
        is DeepLinkState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: DeepLinkState.Ready,
    actionResult: DeepLinkResult?,
    onIntent: (DeepLinkIntent) -> Unit,
) {
    var uriInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Text("Deep Link Tester", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Input form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = uriInput,
                    onValueChange = { uriInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("myapp://path or https://example.com/path") },
                    label = { Text("URI") },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Label (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Package (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onIntent(DeepLinkIntent.Fire(uriInput, packageInput.takeIf { it.isNotBlank() }))
                        },
                        enabled = uriInput.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fire")
                    }
                    Button(
                        onClick = {
                            onIntent(DeepLinkIntent.Save(uriInput, labelInput, packageInput))
                            uriInput = ""
                            labelInput = ""
                            packageInput = ""
                        },
                        enabled = uriInput.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Saved Links (${state.links.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.showFavoritesOnly,
                        onClick = { onIntent(DeepLinkIntent.ShowFavoritesOnly(false)) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = state.showFavoritesOnly,
                        onClick = { onIntent(DeepLinkIntent.ShowFavoritesOnly(true)) },
                        label = { Text("Favorites") },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Links list
            if (state.links.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.showFavoritesOnly) "No favorite links" else "No saved links yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.links, key = { it.id }) { link ->
                        LinkRow(link, onIntent)
                    }
                }
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(DeepLinkIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(DeepLinkIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is DeepLinkResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is DeepLinkResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is DeepLinkResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is DeepLinkResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is DeepLinkResult.Success -> result.message
                        is DeepLinkResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun LinkRow(link: DeepLinkItem, onIntent: (DeepLinkIntent) -> Unit) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (!link.label.isNullOrBlank()) {
                Text(
                    link.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                link.uri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!link.targetPackage.isNullOrBlank()) {
                Text(
                    link.targetPackage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onIntent(DeepLinkIntent.FireExisting(link.id)) }) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = "Fire", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onIntent(DeepLinkIntent.ToggleFavorite(link.id)) }) {
            Icon(
                if (link.isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Toggle Favorite",
                modifier = Modifier.size(18.dp),
                tint = if (link.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { showDeleteConfirmation = true }) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Link") },
            text = { Text("Are you sure you want to delete this deep link?") },
            confirmButton = {
                Button(onClick = {
                    onIntent(DeepLinkIntent.Delete(link.id))
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}
