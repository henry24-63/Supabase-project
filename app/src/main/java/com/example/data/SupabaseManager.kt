package com.example.data

import android.content.Context

class SupabaseManager(context: Context) {
    private val repository = EncryptedSupabaseAccountRepository(context)

    fun getAllAccounts(): List<SupabaseAccount> {
        return repository.getAllAccounts()
    }

    fun addAccount(account: SupabaseAccount) {
        repository.saveAccount(account)
    }

    fun removeAccount(accountId: String) {
        repository.deleteAccount(accountId)
    }

    // This is where the "merged" logic will reside.
    // In a real app, this would use a Supabase client factory
    // to create clients dynamically for each account.
}
