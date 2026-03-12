package com.ragaa.snapadb.feature.deeplink.model

data class DeepLinkItem(
    val id: Long,
    val uri: String,
    val label: String?,
    val targetPackage: String?,
    val isFavorite: Boolean,
    val lastUsed: Long?,
    val createdAt: Long,
)
