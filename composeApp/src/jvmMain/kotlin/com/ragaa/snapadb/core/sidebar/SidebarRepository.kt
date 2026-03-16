package com.ragaa.snapadb.core.sidebar

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.ui.sidebar.defaultPinnedRoutes
import com.ragaa.snapadb.database.SnapAdbDatabase
import kotlinx.coroutines.withContext

class SidebarRepository(
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun getPinnedRoutes(): List<String> = withContext(dispatchers.io) {
        val value = database.snapAdbQueries.getValue(KEY_PINNED_ROUTES)
            .executeAsOneOrNull()
        if (value.isNullOrBlank()) {
            defaultPinnedRoutes.toList()
        } else {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    suspend fun setPinnedRoutes(routes: List<String>) = withContext(dispatchers.io) {
        database.snapAdbQueries.setValue(KEY_PINNED_ROUTES, routes.joinToString(","))
    }

    private companion object {
        const val KEY_PINNED_ROUTES = "sidebar_pinned_routes"
    }
}
