package com.ragaa.snapadb.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.feature.shell.logcat.LogcatScreen
import com.ragaa.snapadb.feature.shell.logcat.LogcatViewModel
import com.ragaa.snapadb.feature.shell.terminal.TerminalScreen
import com.ragaa.snapadb.feature.shell.terminal.TerminalViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ShellScreen(
    terminalViewModel: TerminalViewModel = koinViewModel(),
    logcatViewModel: LogcatViewModel = koinViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Terminal", "Logcat")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        val contentModifier = Modifier
            .fillMaxSize()
            .padding(16.dp)

        when (selectedTab) {
            0 -> Box(modifier = contentModifier) { TerminalScreen(terminalViewModel) }
            1 -> Box(modifier = contentModifier) { LogcatScreen(logcatViewModel) }
        }
    }
}
