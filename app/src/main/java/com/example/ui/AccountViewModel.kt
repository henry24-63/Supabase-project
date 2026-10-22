package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.data.SupabaseAccount
import com.example.data.SupabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class AccountViewModel(private val supabaseManager: SupabaseManager) : ViewModel() {

    private val _accounts = MutableStateFlow<List<SupabaseAccount>>(emptyList())
    val accounts: StateFlow<List<SupabaseAccount>> = _accounts

    init {
        refreshAccounts()
    }

    fun refreshAccounts() {
        _accounts.value = supabaseManager.getAllAccounts()
    }

    fun addAccount(name: String, url: String, key: String, pass: String) {
        val newAccount = SupabaseAccount(
            id = UUID.randomUUID().toString(),
            name = name,
            projectUrl = url,
            apiKey = key,
            databasePassword = pass
        )
        supabaseManager.addAccount(newAccount)
        refreshAccounts()
    }

    fun parseAndAddAccount(rawData: String) {
        // Simple regex/parsing logic to extract relevant fields
        val nameMatch = Regex("(?i)project name\\s*(.*)").find(rawData)
        val urlMatch = Regex("https://[\\w\\.]+.supabase\\.co").find(rawData)
        val keyMatch = Regex("sb_[a-zA-Z0-9_]+").find(rawData)
        val passMatch = Regex("Adnan!@24\\?{2,4}").find(rawData)
        
        val name = nameMatch?.groupValues?.get(1)?.trim() ?: "New Account"
        val url = urlMatch?.value ?: ""
        val key = keyMatch?.value ?: ""
        val pass = passMatch?.value ?: ""
        
        if (url.isNotEmpty() && key.isNotEmpty()) {
            addAccount(name, url, key, pass)
        }
    }

    fun removeAccount(accountId: String) {
        supabaseManager.removeAccount(accountId)
        refreshAccounts()
    }
}
