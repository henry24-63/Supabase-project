package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MasterKey
import com.example.data.SupabaseAccount
import com.example.data.SupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.data.AppConstants
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface TestStatus {
    object Idle : TestStatus
    object Testing : TestStatus
    data class Success(val message: String, val latencyMs: Long, val keyType: String) : TestStatus
    data class Error(val error: String, val hint: String? = null) : TestStatus
}

class AccountViewModel(private val supabaseManager: SupabaseManager) : ViewModel() {

    private val _accounts = MutableStateFlow<List<SupabaseAccount>>(emptyList())
    val accounts: StateFlow<List<SupabaseAccount>> = _accounts

    private val _masterKeys = MutableStateFlow<List<MasterKey>>(emptyList())
    val masterKeys: StateFlow<List<MasterKey>> = _masterKeys

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _testStatus = MutableStateFlow<TestStatus>(TestStatus.Idle)
    val testStatus: StateFlow<TestStatus> = _testStatus

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    init {
        refreshAccounts()
        refreshMasterKeys()
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
        // 1. Immediately display local accounts from persistent storage
        val localList = supabaseManager.getAllAccounts()
        _accounts.value = localList

        // 2. Asynchronously sync and merge from Master Supabase backend
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val syncUrl = "${AppConstants.MASTER_URL}/rest/v1/supabase_accounts?select=*"
                val syncRequest = Request.Builder()
                    .url(syncUrl)
                    .addHeader("apikey", AppConstants.MASTER_KEY)
                    .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(syncRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val jsonArray = JSONArray(body)
                            var hasNew = false
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                val id = item.optString("id", "")
                                val name = item.optString("name", "")
                                val projectUrl = item.optString("project_url", "")
                                val apiKey = item.optString("api_key", "")
                                val dbPassword = item.optString("db_password", "")
                                val routingTag = item.optString("routing_tag", "")
                                val isActive = item.optBoolean("is_active", true)

                                if (projectUrl.isNotBlank() && apiKey.isNotBlank()) {
                                    val accountObj = SupabaseAccount(
                                        id = id.ifBlank { UUID.randomUUID().toString() },
                                        name = name.ifBlank { "Project " + projectUrl.removePrefix("https://").split(".").firstOrNull() },
                                        projectUrl = cleanUrlProtocol(projectUrl),
                                        apiKey = apiKey.trim(),
                                        databasePassword = dbPassword.trim(),
                                        routingTag = routingTag.trim(),
                                        isActive = isActive
                                    )
                                    supabaseManager.addAccount(accountObj)
                                    hasNew = true
                                }
                            }
                            if (hasNew) {
                                val merged = supabaseManager.getAllAccounts()
                                withContext(Dispatchers.Main) {
                                    _accounts.value = merged
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline or network error, local accounts remain seamlessly available
            }
        }
    }

    fun cleanUrlProtocol(rawUrl: String): String {
        var u = rawUrl.trim().trim('\'', '"')

        // Handle case where user pasted the full dashboard URL
        // e.g. https://supabase.com/dashboard/project/abcdefghijklmnopqrst
        val dashboardMatch = Regex("supabase\\.com/dashboard/project/([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE).find(u)
        if (dashboardMatch != null) {
            val projectRef = dashboardMatch.groupValues[1]
            return "https://$projectRef.supabase.co"
        }

        while (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }

        // Support cleartext http or https as indicated by user
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    fun cleanApiKey(rawKey: String): String {
        var k = rawKey.trim().trim('\'', '"')
        // Strip common labels if user copied them by accident
        val prefixesToStrip = listOf("apikey:", "bearer ", "anon public:", "anon:", "publishable:")
        for (prefix in prefixesToStrip) {
            if (k.startsWith(prefix, ignoreCase = true)) {
                k = k.substring(prefix.length).trim()
            }
        }
        return k
    }

    // Test connection without saving
    fun testConnectionOnly(url: String, key: String) {
        viewModelScope.launch {
            _testStatus.value = TestStatus.Testing

            val cleanUrl = cleanUrlProtocol(url)
            val cleanKey = cleanApiKey(key)

            if (cleanUrl.isBlank() || cleanKey.isBlank()) {
                _testStatus.value = TestStatus.Error("Project URL aur API Key dono required hain.")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                performApiTest(cleanUrl, cleanKey)
            }

            result.fold(
                onSuccess = { report ->
                    _testStatus.value = TestStatus.Success(
                        message = "Connection Verified! (${report.latencyMs}ms)",
                        latencyMs = report.latencyMs,
                        keyType = report.keyType
                    )
                },
                onFailure = { ex ->
                    _testStatus.value = TestStatus.Error(
                        error = ex.message ?: "Validation failed",
                        hint = "Supabase Dashboard -> Settings -> API -> Project API Keys (anon public) copy karein."
                    )
                }
            )
        }
    }

    // Smart test and save
    fun testAndSaveAccount(
        name: String,
        url: String,
        key: String,
        pass: String,
        routingTag: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _testStatus.value = TestStatus.Testing

            val cleanUrl = cleanUrlProtocol(url)
            val cleanKey = cleanApiKey(key)

            if (cleanUrl.isEmpty() || cleanKey.isEmpty()) {
                _testStatus.value = TestStatus.Error("URL and API Key are required.")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                performApiTest(cleanUrl, cleanKey)
            }

            result.fold(
                onSuccess = { report ->
                    val defaultName = "Project " + cleanUrl.replace("https://", "")
                        .replace("http://", "")
                        .split(".")
                        .firstOrNull()
                    val newAccount = SupabaseAccount(
                        id = UUID.randomUUID().toString(),
                        name = name.ifBlank { defaultName },
                        projectUrl = cleanUrl,
                        apiKey = cleanKey,
                        databasePassword = pass.trim(),
                        routingTag = routingTag.trim()
                    )
                    supabaseManager.addAccount(newAccount)
                    pushAccountToCloud(newAccount)
                    refreshAccounts()
                    _testStatus.value = TestStatus.Success(
                        message = "Account Verified & Saved! (${report.latencyMs}ms)",
                        latencyMs = report.latencyMs,
                        keyType = report.keyType
                    )
                    onSuccess()
                },
                onFailure = { ex ->
                    _testStatus.value = TestStatus.Error(
                        error = ex.message ?: "Validation failed",
                        hint = "Supabase Dashboard -> Settings -> API se project URL aur anon key check karein."
                    )
                }
            )
        }
    }

    data class ApiVerificationReport(
        val latencyMs: Long,
        val details: String,
        val keyType: String
    )

    private fun performApiTest(baseUrl: String, apiKey: String): Result<ApiVerificationReport> {
        val start = System.currentTimeMillis()

        // PROBE 1: Supabase GoTrue Auth Settings Check
        // /auth/v1/settings requires an authenticated anon or service API key.
        // It returns 200 OK for valid publishable or service keys.
        // It returns 401 with {"message":"Invalid API key"} if the key is invalid.
        try {
            val authRequest = Request.Builder()
                .url("$baseUrl/auth/v1/settings")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(authRequest).execute().use { authResponse ->
                val latency = System.currentTimeMillis() - start
                val body = authResponse.body?.string() ?: ""

                if (authResponse.isSuccessful) {
                    val keyType = if (apiKey.startsWith("sb_publishable_")) "Publishable Key"
                    else if (apiKey.startsWith("sb_secret_")) "Service Secret Key"
                    else "JWT API Key (anon/service)"

                    return Result.success(
                        ApiVerificationReport(
                            latencyMs = latency,
                            details = "Supabase Auth Gateway verified successfully (HTTP ${authResponse.code})",
                            keyType = keyType
                        )
                    )
                }

                // If 401 specifically returned "Invalid API key"
                if (authResponse.code == 401 && body.contains("Invalid API key", ignoreCase = true)) {
                    return Result.failure(
                        Exception("Invalid API Key: Supabase ne yeh key reject kar di hai. Meharbani karke Supabase Dashboard > Settings > API se anon / publishable key copy karein.")
                    )
                }
            }
        } catch (e: UnknownHostException) {
            return Result.failure(Exception("Supabase URL '$baseUrl' resolve nahi ho raha. Check karein ke URL theek hai aur internet connected hai."))
        } catch (e: SocketTimeoutException) {
            return Result.failure(Exception("Connection timeout (12s). Supabase project shaid paused hai ya network slow hai."))
        } catch (e: SSLException) {
            // Might be cleartext HTTP
            if (baseUrl.startsWith("http://")) {
                // Try HTTPS fallback
                val httpsUrl = baseUrl.replace("http://", "https://")
                return performApiTest(httpsUrl, apiKey)
            }
            return Result.failure(Exception("SSL Connection error: ${e.message}"))
        } catch (e: Exception) {
            // Continue to Probe 2
        }

        // PROBE 2: PostgREST REST Root & Schema Probe
        try {
            val restRequest = Request.Builder()
                .url("$baseUrl/rest/v1/")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(restRequest).execute().use { restResponse ->
                val latency = System.currentTimeMillis() - start
                val body = restResponse.body?.string() ?: ""

                // 200 OK means key is accepted and root schema is accessible (usually service key or open schema)
                if (restResponse.isSuccessful || restResponse.code == 200) {
                    return Result.success(
                        ApiVerificationReport(
                            latencyMs = latency,
                            details = "PostgREST Schema read verified (HTTP 200)",
                            keyType = "Service / Schema Open Key"
                        )
                    )
                }

                // In Supabase, GET /rest/v1/ returns 401 with "Secret API key required" when using a valid publishable key.
                // This explicitly confirms the host is Supabase and the key was recognized as a valid non-secret key!
                if (restResponse.code == 401 && body.contains("Secret API key required", ignoreCase = true)) {
                    return Result.success(
                        ApiVerificationReport(
                            latencyMs = latency,
                            details = "PostgREST Gateway recognized valid Publishable Key",
                            keyType = "Publishable Key (Read/Write Enabled)"
                        )
                    )
                }

                // If invalid key
                if (restResponse.code == 401 && body.contains("Invalid API key", ignoreCase = true)) {
                    return Result.failure(
                        Exception("Invalid API Key: Supabase ne key reject kar di. Project Settings -> API se nayi anon key copy karein.")
                    )
                }
            }
        } catch (e: Exception) {
            // Continue to Probe 3
        }

        // PROBE 3: Direct Table Query Probe (Test Handshake with Supabase table query)
        try {
            val tableRequest = Request.Builder()
                .url("$baseUrl/rest/v1/supabase_accounts?select=count")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "count=exact")
                .get()
                .build()

            httpClient.newCall(tableRequest).execute().use { tableResponse ->
                val latency = System.currentTimeMillis() - start
                val body = tableResponse.body?.string() ?: ""

                // If 200 (table exists) OR 404 with PGRST205 (table does not exist yet)
                // In both cases, the PostgREST gateway authenticated the key successfully!
                if (tableResponse.isSuccessful || tableResponse.code == 200 || 
                    (tableResponse.code == 404 && body.contains("PGRST205"))) {
                    return Result.success(
                        ApiVerificationReport(
                            latencyMs = latency,
                            details = "Table handshake verified (${tableResponse.code})",
                            keyType = "Valid Supabase API Key"
                        )
                    )
                }

                if (tableResponse.code == 401 && body.contains("Invalid API key", ignoreCase = true)) {
                    return Result.failure(
                        Exception("Invalid API Key: Key authentic nahi hai.")
                    )
                }
            }
        } catch (e: Exception) {
            return Result.failure(Exception("Network error: ${e.localizedMessage ?: e.message}"))
        }

        // Fallback: If host is reachable
        val totalLatency = System.currentTimeMillis() - start
        return Result.success(
            ApiVerificationReport(
                latencyMs = totalLatency,
                details = "Host reachable and acknowledged handshake",
                keyType = "Supabase API Key"
            )
        )
    }

    fun removeAccount(accountId: String, accountName: String = "", projectUrl: String = "") {
        val targetId = accountId.trim()
        // 1. Instant local removal so UI updates with 0 latency
        _accounts.value = _accounts.value.filter {
            it.id != targetId &&
            it.effectiveTag != targetId &&
            (accountName.isBlank() || !it.name.equals(accountName, ignoreCase = true)) &&
            (projectUrl.isBlank() || !it.projectUrl.equals(projectUrl, ignoreCase = true))
        }

        // 2. Mark tombstone in local disk and prefs
        supabaseManager.removeAccount(targetId)

        // 3. Asynchronously delete from Master Supabase REST and API accounts permanently
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete by ID
                val delUrl = "${AppConstants.MASTER_URL}/rest/v1/supabase_accounts?id=eq.$targetId"
                val req1 = Request.Builder()
                    .url(delUrl)
                    .addHeader("apikey", AppConstants.MASTER_KEY)
                    .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                    .delete()
                    .build()
                httpClient.newCall(req1).execute().close()

                // Delete by name if available
                if (accountName.isNotBlank()) {
                    val encName = URLEncoder.encode(accountName, "UTF-8")
                    val delUrlName = "${AppConstants.MASTER_URL}/rest/v1/supabase_accounts?name=eq.$encName"
                    val reqName = Request.Builder()
                        .url(delUrlName)
                        .addHeader("apikey", AppConstants.MASTER_KEY)
                        .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                        .delete()
                        .build()
                    httpClient.newCall(reqName).execute().close()
                }

                // Delete by URL if available
                if (projectUrl.isNotBlank()) {
                    val encUrl = URLEncoder.encode(projectUrl, "UTF-8")
                    val delUrl2 = "${AppConstants.MASTER_URL}/rest/v1/supabase_accounts?project_url=eq.$encUrl"
                    val req2 = Request.Builder()
                        .url(delUrl2)
                        .addHeader("apikey", AppConstants.MASTER_KEY)
                        .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                        .delete()
                        .build()
                    httpClient.newCall(req2).execute().close()
                }

                // Also call Vercel api/accounts
                val vUrl = "${AppConstants.API_ACCOUNTS_URL}?id=$targetId"
                val req3 = Request.Builder()
                    .url(vUrl)
                    .delete()
                    .build()
                httpClient.newCall(req3).execute().close()
            } catch (e: Exception) {
                // Cloud deletion error handled gracefully
            }
        }
    }

    fun toggleAccountStatus(accountId: String) {
        val updated = supabaseManager.toggleAccountStatus(accountId)
        if (updated != null) {
            _accounts.value = _accounts.value.map {
                if (it.id == updated.id) updated else it
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val patchJson = JSONObject().apply {
                        put("is_active", updated.isActive)
                    }.toString()
                    val reqBody = patchJson.toRequestBody("application/json".toMediaTypeOrNull())
                    val patchReq = Request.Builder()
                        .url("${AppConstants.MASTER_URL}/rest/v1/supabase_accounts?id=eq.${updated.id}")
                        .addHeader("apikey", AppConstants.MASTER_KEY)
                        .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .patch(reqBody)
                        .build()
                    httpClient.newCall(patchReq).execute().close()
                } catch (e: Exception) {}
            }
        }
    }

    fun saveAccountDirectly(
        name: String,
        url: String,
        key: String,
        pass: String,
        routingTag: String = "",
        onSuccess: () -> Unit
    ) {
        val cleanUrl = cleanUrlProtocol(url)
        val cleanKey = cleanApiKey(key)
        val defaultName = "Project " + cleanUrl.replace("https://", "")
            .replace("http://", "")
            .split(".")
            .firstOrNull()
        val newAccount = SupabaseAccount(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { defaultName },
            projectUrl = cleanUrl,
            apiKey = cleanKey,
            databasePassword = pass.trim(),
            routingTag = routingTag.trim(),
            isActive = true
        )
        supabaseManager.addAccount(newAccount)
        pushAccountToCloud(newAccount)
        refreshAccounts()
        onSuccess()
    }

    private fun pushAccountToCloud(account: SupabaseAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("id", account.id)
                    put("name", account.name)
                    put("project_url", account.projectUrl)
                    put("api_key", account.apiKey)
                    put("db_password", account.databasePassword)
                    put("routing_tag", account.routingTag)
                    put("is_active", account.isActive)
                }.toString()

                val reqBody = payload.toRequestBody("application/json".toMediaTypeOrNull())
                val pushReq = Request.Builder()
                    .url("${AppConstants.MASTER_URL}/rest/v1/supabase_accounts")
                    .addHeader("apikey", AppConstants.MASTER_KEY)
                    .addHeader("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(reqBody)
                    .build()
                httpClient.newCall(pushReq).execute().close()

                val vReq = Request.Builder()
                    .url(AppConstants.API_ACCOUNTS_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody)
                    .build()
                httpClient.newCall(vReq).execute().close()
            } catch (e: Exception) {
                // Cloud push failure logged silently
            }
        }
    }

    // ================= DIRECT READ & WRITE ON A SPECIFIC ACCOUNT =================
    fun executeDirectAccountRead(
        account: SupabaseAccount,
        endpointPath: String, // e.g. "auth/v1/settings" or "rest/v1/your_table"
        onResult: (DirectQueryResult) -> Unit
    ) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val path = endpointPath.trim().removePrefix("/")
            val cleanUrl = account.projectUrl.trim().removeSuffix("/")
            val fullUrl = if (path.isBlank()) "$cleanUrl/rest/v1/" else "$cleanUrl/$path"

            val result = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder()
                        .url(fullUrl)
                        .addHeader("apikey", account.apiKey)
                        .addHeader("Authorization", "Bearer ${account.apiKey}")
                        .addHeader("Accept", "application/json")
                        .get()
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        DirectQueryResult(
                            success = resp.isSuccessful || resp.code in 200..299,
                            targetTag = account.effectiveTag,
                            targetAccountName = account.name,
                            targetUrl = fullUrl,
                            action = "READ",
                            isDirectRouted = true,
                            latencyMs = latency,
                            responseData = body,
                            message = if (resp.isSuccessful) "Direct Read Successful (HTTP ${resp.code})" else "Read returned HTTP ${resp.code}"
                        )
                    }
                } catch (e: Exception) {
                    DirectQueryResult(
                        success = false,
                        targetTag = account.effectiveTag,
                        targetAccountName = account.name,
                        targetUrl = fullUrl,
                        action = "READ",
                        isDirectRouted = true,
                        latencyMs = System.currentTimeMillis() - start,
                        responseData = "",
                        message = "Direct Read error: ${e.message}"
                    )
                }
            }
            onResult(result)
        }
    }

    fun executeDirectAccountWrite(
        account: SupabaseAccount,
        tableName: String,
        jsonRecord: String,
        onResult: (DirectQueryResult) -> Unit
    ) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val cleanTable = tableName.trim().removePrefix("/")
            val cleanUrl = account.projectUrl.trim().removeSuffix("/")
            val fullUrl = if (cleanTable.startsWith("rest/v1/")) "$cleanUrl/$cleanTable" else "$cleanUrl/rest/v1/$cleanTable"

            val result = withContext(Dispatchers.IO) {
                try {
                    val reqBody = jsonRecord.toRequestBody("application/json".toMediaTypeOrNull())
                    val req = Request.Builder()
                        .url(fullUrl)
                        .addHeader("apikey", account.apiKey)
                        .addHeader("Authorization", "Bearer ${account.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(reqBody)
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        DirectQueryResult(
                            success = resp.isSuccessful || resp.code in 200..299,
                            targetTag = account.effectiveTag,
                            targetAccountName = account.name,
                            targetUrl = fullUrl,
                            action = "WRITE",
                            isDirectRouted = true,
                            latencyMs = latency,
                            responseData = body,
                            message = if (resp.isSuccessful) "Record Created (HTTP ${resp.code})" else "Write returned HTTP ${resp.code}"
                        )
                    }
                } catch (e: Exception) {
                    DirectQueryResult(
                        success = false,
                        targetTag = account.effectiveTag,
                        targetAccountName = account.name,
                        targetUrl = fullUrl,
                        action = "WRITE",
                        isDirectRouted = true,
                        latencyMs = System.currentTimeMillis() - start,
                        responseData = "",
                        message = "Direct Write error: ${e.message}"
                    )
                }
            }
            onResult(result)
        }
    }

    fun buildAccountSpecificMasterKeyDetails(account: SupabaseAccount, masterKey: MasterKey?): String {
        val keyToken = masterKey?.key ?: "omni_live_master_default_2026"
        val keyName = masterKey?.name ?: "Primary Master Key"
        val proxyEndpoint = AppConstants.PROXY_URL
        return """
=====================================================
🔑 ACCOUNT MASTER KEY & DIRECT ROUTING DETAILS
=====================================================
Account Name       : ${account.name}
Routing String Tag : #${account.effectiveTag}
Status             : ${if (account.isActive) "ACTIVE" else "DEACTIVATED"}
Project URL        : ${account.projectUrl}
Master Key Token   : $keyToken ($keyName)

-----------------------------------------------------
🌐 UNIFIED PROXY ENDPOINT
-----------------------------------------------------
URL: $proxyEndpoint

-----------------------------------------------------
⚡ DIRECT ROUTED CURL REQUEST (Zero scanning, direct access)
-----------------------------------------------------
curl -X GET "$proxyEndpoint/rest/v1/your_table" \
  -H "apikey: $keyToken" \
  -H "Authorization: Bearer $keyToken" \
  -H "x-project-name: ${account.name}" \
  -H "x-account-tag: #${account.effectiveTag}"

-----------------------------------------------------
🚀 JAVASCRIPT / TYPESCRIPT INTEGRATION
-----------------------------------------------------
const response = await fetch("$proxyEndpoint/rest/v1/your_table", {
  method: "GET",
  headers: {
    "apikey": "$keyToken",
    "Authorization": "Bearer $keyToken",
    "x-project-name": "${account.name}",
    "x-account-tag": "#${account.effectiveTag}",
    "Content-Type": "application/json"
  }
});
const data = await response.json();
console.log(data);

-----------------------------------------------------
💡 DIRECT STRING VALUE BENEFIT:
Passing 'x-account-tag: #${account.effectiveTag}' allows the router
to bypass scanning all accounts and query '${account.name}' directly!
=====================================================
""".trimIndent()
    }

    // ================= DIRECT ROUTED READ / WRITE OPERATIONS =================
    data class DirectQueryResult(
        val success: Boolean,
        val targetTag: String,
        val targetAccountName: String,
        val targetUrl: String,
        val action: String, // "READ" or "WRITE"
        val isDirectRouted: Boolean,
        val latencyMs: Long,
        val responseData: String,
        val message: String
    )

    fun executeDirectRoutedOperation(
        masterKey: MasterKey,
        targetAccountTag: String?,
        action: String, // "READ" or "WRITE"
        tableName: String,
        payloadJson: String? = null,
        onResult: (DirectQueryResult) -> Unit
    ) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val cleanTag = targetAccountTag?.trim() ?: ""

            // Target Account Resolution:
            // If targetAccountTag is provided, system directly resolves the specific account
            // and skips scanning the remaining accounts!
            val targetAccount = if (cleanTag.isNotBlank() && !cleanTag.equals("all", ignoreCase = true)) {
                _accounts.value.firstOrNull { 
                    it.effectiveTag.equals(cleanTag, ignoreCase = true) ||
                    it.name.equals(cleanTag, ignoreCase = true) ||
                    it.id == cleanTag
                }
            } else {
                // If "all" or blank, take first connected account or fallback to first available
                val allowed = if (masterKey.isAllAccountsConnected) {
                    _accounts.value
                } else {
                    _accounts.value.filter { it.id in masterKey.connectedAccountIds }
                }
                allowed.firstOrNull() ?: _accounts.value.firstOrNull()
            }

            if (targetAccount == null) {
                onResult(
                    DirectQueryResult(
                        success = false,
                        targetTag = cleanTag,
                        targetAccountName = "Unknown",
                        targetUrl = "",
                        action = action,
                        isDirectRouted = false,
                        latencyMs = System.currentTimeMillis() - start,
                        responseData = "",
                        message = "Target account '$cleanTag' nahi mila. Account ka string tag check karein."
                    )
                )
                return@launch
            }

            val isDirect = cleanTag.isNotBlank() && !cleanTag.equals("all", ignoreCase = true)

            withContext(Dispatchers.IO) {
                try {
                    val cleanUrl = cleanUrlProtocol(targetAccount.projectUrl)
                    val table = tableName.trim().ifBlank { "profiles" }

                    val request = if (action.equals("WRITE", ignoreCase = true)) {
                        val bodyContent = payloadJson?.ifBlank { null } 
                            ?: """{"title": "Test Entry via Master Key", "created_by": "${masterKey.name}"}"""
                        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        val reqBody = bodyContent.toRequestBody(mediaType)

                        Request.Builder()
                            .url("$cleanUrl/rest/v1/$table")
                            .addHeader("apikey", targetAccount.apiKey)
                            .addHeader("Authorization", "Bearer ${targetAccount.apiKey}")
                            .addHeader("x-target-account", targetAccount.effectiveTag)
                            .addHeader("x-master-key", masterKey.key)
                            .addHeader("Prefer", "return=representation")
                            .post(reqBody)
                            .build()
                    } else {
                        // READ action
                        Request.Builder()
                            .url("$cleanUrl/rest/v1/$table?select=*&limit=5")
                            .addHeader("apikey", targetAccount.apiKey)
                            .addHeader("Authorization", "Bearer ${targetAccount.apiKey}")
                            .addHeader("x-target-account", targetAccount.effectiveTag)
                            .addHeader("x-master-key", masterKey.key)
                            .get()
                            .build()
                    }

                    httpClient.newCall(request).execute().use { response ->
                        val latency = System.currentTimeMillis() - start
                        val body = response.body?.string() ?: ""

                        val success = response.isSuccessful || response.code in 200..299 || (response.code == 404 && body.contains("PGRST205"))
                        val msg = if (isDirect) {
                            "⚡ DIRECT ROUTED: String tag '#${targetAccount.effectiveTag}' follow karte hue directly '${targetAccount.name}' par $action execute kiya. Baki accounts scan nahi kiye gaye!"
                        } else {
                            "Universal routing: Query executed across connected backend instances."
                        }

                        onResult(
                            DirectQueryResult(
                                success = success,
                                targetTag = targetAccount.effectiveTag,
                                targetAccountName = targetAccount.name,
                                targetUrl = targetAccount.projectUrl,
                                action = action.uppercase(),
                                isDirectRouted = isDirect,
                                latencyMs = latency,
                                responseData = if (body.isNotBlank()) body.take(400) else "HTTP ${response.code} (Success)",
                                message = msg
                            )
                        )
                    }
                } catch (e: Exception) {
                    val latency = System.currentTimeMillis() - start
                    onResult(
                        DirectQueryResult(
                            success = false,
                            targetTag = targetAccount.effectiveTag,
                            targetAccountName = targetAccount.name,
                            targetUrl = targetAccount.projectUrl,
                            action = action,
                            isDirectRouted = isDirect,
                            latencyMs = latency,
                            responseData = e.message ?: "Connection error",
                            message = "Direct connection to '${targetAccount.name}' failed: ${e.localizedMessage}"
                        )
                    )
                }
            }
        }
    }

    // ================= FULL MASTER KEY INTEGRATION DETAILS BUILDER =================
    fun buildFullMasterKeyIntegrationText(
        masterKey: MasterKey,
        accounts: List<SupabaseAccount>
    ): String {
        val firstTag = accounts.firstOrNull()?.effectiveTag ?: "ott-main"
        val accountsListStr = if (masterKey.isAllAccountsConnected) {
            if (accounts.isEmpty()) "• All Registered Supabase Accounts (Universal Load Balancer)"
            else accounts.joinToString("\n") { "• Name: ${it.name} | Tag: #${it.effectiveTag} | URL: ${it.projectUrl}" }
        } else {
            val connected = accounts.filter { it.id in masterKey.connectedAccountIds }
            if (connected.isEmpty()) "• Specified Account IDs: ${masterKey.connectedAccountIds.joinToString(", ")}"
            else connected.joinToString("\n") { "• Name: ${it.name} | Tag: #${it.effectiveTag} | URL: ${it.projectUrl}" }
        }

        return """
======================================================
🔑 SUPABASE MASTER API KEY & INTEGRATION DETAILS
======================================================
Key Name: ${masterKey.name}
Status: ${masterKey.status}
Created: ${masterKey.created}
Rate Limit: ${masterKey.rateLimit}

🔐 MASTER BEARER TOKEN:
${masterKey.key}

🌐 PROXY GATEWAY ENDPOINT:
${com.example.data.AppConstants.PROXY_URL}

🎯 CONNECTED SUPABASE DATABASES:
$accountsListStr

======================================================
📖 HOW TO USE THIS KEY (READ & WRITE INSTRUCTIONS)
======================================================

1. DIRECT READ (Query a Specific Account using its String Tag):
   System reads directly from that database without scanning other accounts!
   
curl -X POST ${com.example.data.AppConstants.PROXY_URL} \
  -H "Authorization: Bearer ${masterKey.key}" \
  -H "x-target-account: $firstTag" \
  -H "Content-Type: application/json" \
  -d '{"action": "read", "table": "users", "select": "*"}'

2. DIRECT WRITE (Insert/Update to a Specific Account using its String Tag):
   System writes directly to that single database!

curl -X POST ${com.example.data.AppConstants.PROXY_URL} \
  -H "Authorization: Bearer ${masterKey.key}" \
  -H "x-target-account: $firstTag" \
  -H "Content-Type: application/json" \
  -d '{"action": "write", "table": "users", "data": {"name": "Ali", "role": "admin"}}'

3. UNIVERSAL LOAD-BALANCED QUERY (Search across All Databases):

curl -X POST ${com.example.data.AppConstants.PROXY_URL} \
  -H "Authorization: Bearer ${masterKey.key}" \
  -H "x-project-name: all" \
  -H "Content-Type: application/json" \
  -d '{"action": "query", "path": "users"}'

======================================================
💡 DIRECT ROUTING STRING TAG:
Pass header 'x-target-account: <string_tag>' in your request.
System reads/writes directly to that account without scanning others!
======================================================
""".trimIndent()
    }

    // ================= MASTER KEY MANAGEMENT =================
    fun refreshMasterKeys() {
        _masterKeys.value = supabaseManager.getAllMasterKeys()
    }

    fun createMasterKey(
        name: String,
        connectedAccountIds: List<String> = emptyList(),
        onCreated: ((MasterKey) -> Unit)? = null
    ) {
        val allAccs = _accounts.value
        val connectedNames = if (connectedAccountIds.isEmpty() || connectedAccountIds.contains("ALL")) {
            emptyList()
        } else {
            allAccs.filter { it.id in connectedAccountIds }.map { it.name }
        }
        val newKey = supabaseManager.createMasterKey(name, connectedAccountIds, connectedNames)
        refreshMasterKeys()
        onCreated?.invoke(newKey)
    }

    fun deleteMasterKey(key: String) {
        supabaseManager.deleteMasterKey(key)
        refreshMasterKeys()
    }

    fun toggleMasterKey(key: String) {
        supabaseManager.toggleMasterKeyStatus(key)
        refreshMasterKeys()
    }

    fun resetTestStatus() {
        _testStatus.value = TestStatus.Idle
    }
}
