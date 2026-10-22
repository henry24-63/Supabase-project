package com.example.data

interface SupabaseAccountRepository {
    fun saveAccount(account: SupabaseAccount)
    fun getAllAccounts(): List<SupabaseAccount>
    fun deleteAccount(accountId: String)
}
