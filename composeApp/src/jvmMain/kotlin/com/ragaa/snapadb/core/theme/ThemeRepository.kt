package com.ragaa.snapadb.core.theme

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.database.SnapAdbDatabase
import kotlinx.coroutines.withContext

class ThemeRepository(
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun getThemeMode(): ThemeMode = withContext(dispatchers.io) {
        val value = database.snapAdbQueries.getValue(KEY_THEME_MODE)
            .executeAsOneOrNull()
        when (value) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = withContext(dispatchers.io) {
        database.snapAdbQueries.setValue(KEY_THEME_MODE, mode.name)
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
