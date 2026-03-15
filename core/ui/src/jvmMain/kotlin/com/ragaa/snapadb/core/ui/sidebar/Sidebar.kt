package com.ragaa.snapadb.core.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.navigation.Route

private data class NavItem(val route: Route, val icon: ImageVector)

private val overviewGroup = listOf(
    NavItem(Route.Dashboard, Icons.Outlined.Dashboard),
    NavItem(Route.MultiDevice, Icons.Outlined.Devices),
)

private val manageGroup = listOf(
    NavItem(Route.AppManager, Icons.Outlined.Apps),
    NavItem(Route.FileExplorer, Icons.Outlined.Folder),
    NavItem(Route.AppData, Icons.Outlined.Storage),
    NavItem(Route.Shell, Icons.Outlined.Terminal),
    NavItem(Route.Logcat, Icons.Outlined.BugReport),
)

private val toolsGroup = listOf(
    NavItem(Route.ScreenMirror, Icons.AutoMirrored.Outlined.ScreenShare),
    NavItem(Route.Performance, Icons.Outlined.Speed),
    NavItem(Route.DeviceControls, Icons.Outlined.SettingsRemote),
)

private val testGroup = listOf(
    NavItem(Route.DeepLink, Icons.Outlined.Link),
    NavItem(Route.Notification, Icons.Outlined.Notifications),
    NavItem(Route.BugReporter, Icons.Outlined.Summarize),
)

private val allGroups = listOf(overviewGroup, manageGroup, toolsGroup, testGroup)

@Composable
fun Sidebar(
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        allGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                GroupDivider()
            }
            group.forEach { item ->
                SidebarItem(
                    icon = item.icon,
                    label = item.route.title,
                    selected = currentRoute == item.route,
                    onClick = { onRouteSelected(item.route) },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row {
            // Left accent bar for selected item
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
    }
}
