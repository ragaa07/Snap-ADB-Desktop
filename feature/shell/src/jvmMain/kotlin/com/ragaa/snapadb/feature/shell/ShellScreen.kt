package com.ragaa.snapadb.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.feature.shell.logcat.LogcatScreen
import com.ragaa.snapadb.feature.shell.logcat.LogcatViewModel
import com.ragaa.snapadb.feature.shell.terminal.TerminalScreen
import com.ragaa.snapadb.feature.shell.terminal.TerminalViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Standalone Terminal screen — accessed via its own sidebar item.
 */
@Composable
fun TerminalOnlyScreen(
    viewModel: TerminalViewModel = koinViewModel(),
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TerminalScreen(viewModel)
    }
}

/**
 * Standalone Logcat screen — accessed via its own sidebar item.
 */
@Composable
fun LogcatOnlyScreen(
    viewModel: LogcatViewModel = koinViewModel(),
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LogcatScreen(viewModel)
    }
}
