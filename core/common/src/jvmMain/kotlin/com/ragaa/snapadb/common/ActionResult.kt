package com.ragaa.snapadb.common

sealed class ActionResult {
    data object Loading : ActionResult()
    data class Success(val message: String) : ActionResult()
    data class Error(val message: String) : ActionResult()
}
