package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class EncryptedSupabaseAccountRepository(context: Context) : SupabaseAccountRepository {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "supabase_accounts",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, SupabaseAccount::class.java)
    private val adapter = moshi.adapter<List<SupabaseAccount>>(listType)

    override fun saveAccount(account: SupabaseAccount) {
        val accounts = getAllAccounts().toMutableList()
        accounts.add(account)
        val json = adapter.toJson(accounts)
        sharedPreferences.edit().putString("accounts", json).apply()
    }

    override fun getAllAccounts(): List<SupabaseAccount> {
        val json = sharedPreferences.getString("accounts", null)
        return if (json != null) {
            adapter.fromJson(json) ?: emptyList()
        } else {
            emptyList()
        }
    }

    override fun deleteAccount(accountId: String) {
        val accounts = getAllAccounts().toMutableList()
        accounts.removeAll { it.id == accountId }
        val json = adapter.toJson(accounts)
        sharedPreferences.edit().putString("accounts", json).apply()
    }
}
