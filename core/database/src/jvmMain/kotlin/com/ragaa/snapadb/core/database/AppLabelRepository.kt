package com.ragaa.snapadb.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ragaa.snapadb.database.SnapAdbDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppLabelRepository(
    private val db: SnapAdbDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeAllLabels(): Flow<Map<String, String>> =
        db.snapAdbQueries.getAllLabels()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.associate { it.package_name to it.label } }

    fun getLabel(packageName: String): String? =
        db.snapAdbQueries.getLabel(packageName).executeAsOneOrNull()

    suspend fun upsertLabels(labels: Map<String, String>) {
        if (labels.isEmpty()) return
        withContext(ioDispatcher) {
            db.snapAdbQueries.transaction {
                for ((pkg, label) in labels) {
                    db.snapAdbQueries.upsertLabel(pkg, label)
                }
            }
        }
    }
}
