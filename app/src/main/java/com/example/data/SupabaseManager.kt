package com.example.data

import android.content.Context

class SupabaseManager(context: Context) {
    private val repository = EncryptedSupabaseAccountRepository(context)

    // ================= ACCOUNT OPERATIONS =================
    fun getAllAccounts(): List<SupabaseAccount> {
        return repository.getAllAccounts()
    }

    fun addAccount(account: SupabaseAccount) {
        repository.saveAccount(account)
    }

    fun removeAccount(accountId: String) {
        repository.deleteAccount(accountId)
    }

    fun toggleAccountStatus(accountId: String): SupabaseAccount? {
        return repository.toggleAccountStatus(accountId)
    }

    // ================= MASTER KEY OPERATIONS =================
    fun getAllMasterKeys(): List<MasterKey> {
        return repository.getAllMasterKeys()
    }

    fun createMasterKey(
        name: String,
        connectedAccountIds: List<String> = emptyList(),
        connectedAccountNames: List<String> = emptyList()
    ): MasterKey {
        return repository.generateNewMasterKey(name, connectedAccountIds, connectedAccountNames)
    }

    fun deleteMasterKey(key: String) {
        repository.deleteMasterKey(key)
    }

    fun toggleMasterKeyStatus(key: String): MasterKey? {
        return repository.toggleMasterKeyStatus(key)
    }
}
