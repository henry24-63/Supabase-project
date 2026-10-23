package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MasterKey(
    val key: String,
    val name: String,
    val created: String,
    val status: String = "ACTIVE",
    val rateLimit: String = "UNLIMITED",
    @param:Json(name = "connected_account_ids")
    val connectedAccountIds: List<String> = emptyList(),
    @param:Json(name = "connected_account_names")
    val connectedAccountNames: List<String> = emptyList()
) {
    val isAllAccountsConnected: Boolean
        get() = connectedAccountIds.isEmpty() || connectedAccountIds.contains("ALL")

    fun getConnectedSummary(availableAccounts: List<SupabaseAccount> = emptyList()): String {
        if (isAllAccountsConnected) {
            val count = if (availableAccounts.isNotEmpty()) availableAccounts.size else 0
            return if (count > 0) "All Accounts ($count connected)" else "All Accounts (Universal Router)"
        }
        val names = if (connectedAccountNames.isNotEmpty()) {
            connectedAccountNames
        } else {
            availableAccounts.filter { it.id in connectedAccountIds }.map { it.name }
        }
        return if (names.isEmpty()) {
            "${connectedAccountIds.size} Accounts Connected"
        } else {
            "${names.size} Accounts: ${names.joinToString(", ")}"
        }
    }
}
