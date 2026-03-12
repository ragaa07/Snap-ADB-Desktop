package com.ragaa.snapadb.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Router {
    private val _currentRoute = MutableStateFlow<Route>(Route.Dashboard)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    private val backStack = mutableListOf<Route>()
    private val lock = Any()

    fun navigateTo(route: Route) {
        synchronized(lock) {
            if (_currentRoute.value == route) return
            backStack.add(_currentRoute.value)
            if (backStack.size > MAX_BACK_STACK_SIZE) {
                backStack.removeFirst()
            }
            _currentRoute.value = route
        }
    }

    fun back(): Boolean {
        synchronized(lock) {
            val previous = backStack.removeLastOrNull() ?: return false
            _currentRoute.value = previous
            return true
        }
    }

    private companion object {
        const val MAX_BACK_STACK_SIZE = 50
    }
}
