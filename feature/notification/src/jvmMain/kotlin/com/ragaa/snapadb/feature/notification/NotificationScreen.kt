package com.ragaa.snapadb.feature.notification

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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ragaa.snapadb.feature.notification.model.NotificationTemplate
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationScreen(viewModel: NotificationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is NotificationState.NoDevice -> NoDeviceState("Connect a device to test notifications")
        is NotificationState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: NotificationState.Ready,
    actionResult: NotificationResult?,
    onIntent: (NotificationIntent) -> Unit,
) {
    var tagInput by remember { mutableStateOf("test_notification") }
    var titleInput by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }
    var templateNameInput by remember { mutableStateOf("") }

    // Load template payload into form
    state.loadedPayload?.let { payload ->
        LaunchedEffect(payload) {
            tagInput = payload.tag
            titleInput = payload.title
            textInput = payload.text
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Text("Notification Tester", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Compose form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tag") },
                    placeholder = { Text("Notification tag identifier") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    placeholder = { Text("Notification title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text") },
                    placeholder = { Text("Notification body text") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onIntent(NotificationIntent.Send(tagInput, titleInput, textInput)) },
                        enabled = tagInput.isNotBlank() && titleInput.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send")
                    }
                    OutlinedButton(
                        onClick = { onIntent(NotificationIntent.Cancel(tagInput)) },
                        enabled = tagInput.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Save as template
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = templateNameInput,
                        onValueChange = { templateNameInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Template name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Button(
                        onClick = {
                            onIntent(NotificationIntent.SaveTemplate(templateNameInput, tagInput, titleInput, textInput))
                            templateNameInput = ""
                        },
                        enabled = templateNameInput.isNotBlank() && tagInput.isNotBlank() && titleInput.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Template")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Templates list
            Text(
                "Saved Templates (${state.templates.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state.templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No saved templates yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.templates, key = { it.id }) { template ->
                        TemplateRow(template, onIntent)
                    }
                }
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(NotificationIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(NotificationIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is NotificationResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is NotificationResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is NotificationResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is NotificationResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is NotificationResult.Success -> result.message
                        is NotificationResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun TemplateRow(template: NotificationTemplate, onIntent: (NotificationIntent) -> Unit) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                template.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${template.payload.title} - ${template.payload.tag}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onIntent(NotificationIntent.LoadTemplate(template.id)) }) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Load", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { showDeleteConfirmation = true }) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Template") },
            text = { Text("Are you sure you want to delete \"${template.name}\"?") },
            confirmButton = {
                Button(onClick = {
                    onIntent(NotificationIntent.DeleteTemplate(template.id))
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}
