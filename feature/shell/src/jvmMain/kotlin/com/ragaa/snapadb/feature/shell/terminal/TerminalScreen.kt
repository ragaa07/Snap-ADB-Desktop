package com.ragaa.snapadb.feature.shell.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalScreen(viewModel: TerminalViewModel) {
    val state by viewModel.state.collectAsState()
    val input by viewModel.input.collectAsState()

    when (val s = state) {
        is TerminalState.NoDevice -> NoDeviceState("Connect a device to use the terminal")
        is TerminalState.Ready -> TerminalContent(
            output = s.output,
            input = input,
            isExecuting = s.isExecuting,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun TerminalContent(
    output: List<OutputLine>,
    input: String,
    isExecuting: Boolean,
    onIntent: (TerminalIntent) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) {
            listState.animateScrollToItem(output.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Output area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        ) {
            items(output, key = { it.id }) { line ->
                Text(
                    text = line.text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = when (line.type) {
                            OutputType.COMMAND -> SnapAdbTheme.colors.terminalCommand
                            OutputType.STDOUT -> SnapAdbTheme.colors.terminalStdout
                            OutputType.STDERR -> SnapAdbTheme.colors.terminalStderr
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { onIntent(TerminalIntent.UpdateInput(it)) },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> {
                                onIntent(TerminalIntent.ExecuteCommand)
                                true
                            }
                            Key.DirectionUp -> {
                                onIntent(TerminalIntent.NavigateHistory(HistoryDirection.UP))
                                true
                            }
                            Key.DirectionDown -> {
                                onIntent(TerminalIntent.NavigateHistory(HistoryDirection.DOWN))
                                true
                            }
                            else -> false
                        }
                    },
                enabled = !isExecuting,
                placeholder = {
                    Text(
                        if (isExecuting) "Executing..." else "Enter shell command...",
                        fontFamily = FontFamily.Monospace,
                    )
                },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onIntent(TerminalIntent.ExecuteCommand) },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shape = RoundedCornerShape(8.dp),
            )

            IconButton(
                onClick = { onIntent(TerminalIntent.ExecuteCommand) },
                enabled = !isExecuting,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Execute")
            }

            IconButton(onClick = { onIntent(TerminalIntent.ClearOutput) }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear")
            }
        }
    }
}

