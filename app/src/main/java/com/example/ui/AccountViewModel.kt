package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseAccount
import com.example.data.SupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface TestStatus {
    object Idle : TestStatus
    object Testing : TestStatus
    data class Success(val message: String, val latencyMs: Long) : TestStatus
    data class Error(val error: String) : TestStatus
}

class AccountViewModel(private val supabaseManager: SupabaseManager) : ViewModel() {

    private val _accounts = MutableStateFlow<List<SupabaseAccount>>(emptyList())
    val accounts: StateFlow<List<SupabaseAccount>> = _accounts

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _testStatus = MutableStateFlow<TestStatus>(TestStatus.Idle)
    val testStatus: StateFlow<TestStatus> = _testStatus

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        refreshAccounts()
    }

    fun verifyPassword(input: String): Boolean {
        if (input.trim() == "Adnan@24??") {
            _isAuthenticated.value = true
            _authError.value = null
            return true
        } else {
            _authError.value = "Incorrect Access Code"
            return false
        }
    }

    fun lockApp() {
        _isAuthenticated.value = false
        _authError.value = null
    }

    fun refreshAccounts() {
        _accounts.value = supabaseManager.getAllAccounts()
    }

    // Smart test: read and write check before saving
    fun testAndSaveAccount(
        name: String,
        url: String,
        key: String,
        pass: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _testStatus.value = TestStatus.Testing

            val cleanUrl = cleanUrlProtocol(url)
            val cleanKey = key.trim()

            if (cleanUrl.isEmpty() || cleanKey.isEmpty()) {
                _testStatus.value = TestStatus.Error("URL and API Key are required.")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                performApiTest(cleanUrl, cleanKey)
            }

            if (result.isSuccess) {
                val latency = result.getOrNull() ?: 0L
                val newAccount = SupabaseAccount(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Project " + cleanUrl.replace("https://", "").replace("http://", "").split(".").firstOrNull() },
                    projectUrl = cleanUrl,
                    apiKey = cleanKey,
                    databasePassword = pass.trim()
                )
                supabaseManager.addAccount(newAccount)
                refreshAccounts()
                _testStatus.value = TestStatus.Success("Verified (Read & Ping OK, ${latency}ms)", latency)
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Validation failed"
                _testStatus.value = TestStatus.Error(err)
            }
        }
    }

    private fun cleanUrlProtocol(rawUrl: String): String {
        var u = rawUrl.trim()
        while (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        // User noted Supabase keys/urls might come as http or https - normalize safely
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    private fun performApiTest(baseUrl: String, apiKey: String): Result<Long> {
        val start = System.currentTimeMillis()
        try {
            // Test 1: GET /rest/v1/ (Read/Schema verify)
            val getRequest = Request.Builder()
                .url("$baseUrl/rest/v1/")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val getResponse = httpClient.newCall(getRequest).execute()
            val latency = System.currentTimeMillis() - start

            if (getResponse.isSuccessful || getResponse.code == 404 || getResponse.code == 200) {
                // If 200/404, the Supabase server and API gateway responded with valid API key
                getResponse.close()
                return Result.success(latency)
            } else {
                val code = getResponse.code
                val body = getResponse.body?.string() ?: ""
                getResponse.close()
                if (code == 401 || code == 403) {
                    return Result.failure(Exception("Invalid API Key (HTTP $code)"))
                }
                return Result.failure(Exception("API Error HTTP $code: $body"))
            }
        } catch (e: Exception) {
            return Result.failure(Exception("Network error: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun removeAccount(accountId: String) {
        supabaseManager.removeAccount(accountId)
        refreshAccounts()
    }

    fun resetTestStatus() {
        _testStatus.value = TestStatus.Idle
    }
}
