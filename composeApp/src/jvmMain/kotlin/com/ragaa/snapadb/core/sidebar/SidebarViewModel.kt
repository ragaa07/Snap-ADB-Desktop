package com.ragaa.snapadb.core.sidebar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.navigation.Route
import com.ragaa.snapadb.core.ui.sidebar.NavItem
import com.ragaa.snapadb.core.ui.sidebar.allNavItems
import kotlinx.coroutines.launch

class SidebarViewModel(
    private val repository: SidebarRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    var pinnedItems by mutableStateOf<List<NavItem>>(emptyList())
        private set

    var overflowItems by mutableStateOf<List<NavItem>>(emptyList())
        private set

    var isLoaded by mutableStateOf(false)
        private set

    init {
        loadPinnedRoutes()
    }

    private fun loadPinnedRoutes() {
        viewModelScope.launch(dispatchers.io) {
            val pinnedTitles = repository.getPinnedRoutes().toSet()
            updateItems(pinnedTitles)
            isLoaded = true
        }
    }

    fun pinRoute(route: Route) {
        val currentPinned = pinnedItems.map { it.route.title }.toMutableSet()
        currentPinned.add(route.title)
        updateItems(currentPinned)
        viewModelScope.launch(dispatchers.io) {
            repository.setPinnedRoutes(pinnedItems.map { it.route.title })
        }
    }

    fun unpinRoute(route: Route) {
        if (route is Route.Dashboard) return // Dashboard is always pinned
        val currentPinned = pinnedItems.map { it.route.title }.toMutableSet()
        currentPinned.remove(route.title)
        updateItems(currentPinned)
        viewModelScope.launch(dispatchers.io) {
            repository.setPinnedRoutes(pinnedItems.map { it.route.title })
        }
    }

    private fun updateItems(pinnedTitles: Set<String>) {
        pinnedItems = allNavItems.filter { it.route.title in pinnedTitles }
        overflowItems = allNavItems.filter { it.route.title !in pinnedTitles }
    }
}
