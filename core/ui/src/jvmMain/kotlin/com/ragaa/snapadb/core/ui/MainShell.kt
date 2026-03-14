package com.ragaa.snapadb.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.navigation.Router
import com.ragaa.snapadb.core.theme.ThemeMode
import com.ragaa.snapadb.core.ui.sidebar.RightToolBar
import com.ragaa.snapadb.core.ui.sidebar.Sidebar

@Composable
fun MainShell(
    router: Router,
    deviceStatusText: String,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    isMirrorPanelOpen: Boolean,
    onToggleMirrorPanel: () -> Unit,
    mirrorPanelContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentRoute by router.currentRoute.collectAsState()

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            // Left sidebar
            Sidebar(
                currentRoute = currentRoute,
                onRouteSelected = { router.navigateTo(it) },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
            )

            // Main content area
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }

            // Right mirror panel (collapsible)
            AnimatedVisibility(
                visible = isMirrorPanelOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        mirrorPanelContent()
                    }
                }
            }

            // Right tool bar
            VerticalDivider()
            RightToolBar(
                isMirrorOpen = isMirrorPanelOpen,
                onToggleMirror = onToggleMirrorPanel,
            )
        }

        // Status bar
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deviceStatusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
