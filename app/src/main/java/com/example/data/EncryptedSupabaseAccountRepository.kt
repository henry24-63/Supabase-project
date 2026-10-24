package com.example.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey as CryptoMasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EncryptedSupabaseAccountRepository(private val context: Context) : SupabaseAccountRepository {

    private val tag = "SupabaseRepo"
    private val prefKeyLegacyRescueDone = "legacy_rescue_done_v2"
    private val prefKeyDeletedIds = "deleted_account_ids_v2"

    // Primary Plain SharedPreferences (Fast, indestructible across Keystore rotations)
    private val plainPrefs = context.getSharedPreferences("supabase_accounts_plain", Context.MODE_PRIVATE)
    
    // Backup Plain SharedPreferences (Dual redundancy)
    private val backupPrefs = context.getSharedPreferences("supabase_accounts_backup", Context.MODE_PRIVATE)

    // Legacy standard SharedPreferences
    private val legacyPrefs = context.getSharedPreferences("supabase_accounts", Context.MODE_PRIVATE)

    // Optional EncryptedSharedPreferences (Used when Keystore is healthy)
    private val encryptedPrefs by lazy {
        try {
            val cryptoKey = CryptoMasterKey.Builder(context)
                .setKeyScheme(CryptoMasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "supabase_accounts_encrypted",
                cryptoKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(tag, "EncryptedSharedPreferences unavailable: ${e.message}")
            null
        }
    }

    // Direct Disk File Store (Preserved across APK updates, never lost on Keystore reset)
    private val accountsFile = File(context.filesDir, "supabase_accounts_v1.json")
    private val accountsBackupFile = File(context.filesDir, "supabase_accounts_v1.bak")
    private val masterKeysFile = File(context.filesDir, "master_keys_v1.json")

    private val moshi = Moshi.Builder().build()
    private val accountListType = Types.newParameterizedType(List::class.java, SupabaseAccount::class.java)
    private val accountAdapter = moshi.adapter<List<SupabaseAccount>>(accountListType)

    private val masterKeyListType = Types.newParameterizedType(List::class.java, MasterKey::class.java)
    private val masterKeyAdapter = moshi.adapter<List<MasterKey>>(masterKeyListType)

    init {
        // Run initial synchronization and rescue scan
        syncAndRescueData()
    }

    private fun getDeletedAccountIds(): Set<String> {
        return plainPrefs.getStringSet(prefKeyDeletedIds, emptySet())?.toSet() ?: emptySet()
    }

    private fun saveDeletedAccountIds(set: Set<String>) {
        plainPrefs.edit().putStringSet(prefKeyDeletedIds, set).commit()
        backupPrefs.edit().putStringSet(prefKeyDeletedIds, set).commit()
    }

    private fun parseAccountsJson(json: String?): List<SupabaseAccount> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            accountAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed parsing accounts json", e)
            emptyList()
        }
    }

    private fun parseMasterKeysJson(json: String?): List<MasterKey> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            masterKeyAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed parsing master keys json", e)
            emptyList()
        }
    }

    // ================= ACCOUNT OPERATIONS =================
    override fun saveAccount(account: SupabaseAccount) {
        // Remove from deleted blacklist if re-adding
        val deletedSet = getDeletedAccountIds().toMutableSet()
        deletedSet.remove(account.id)
        deletedSet.remove(account.projectUrl.trim())
        deletedSet.remove(account.effectiveTag)
        saveDeletedAccountIds(deletedSet)

        val currentAccounts = getAllAccounts().toMutableList()
        // Replace if already exists with same ID or same URL+Key, otherwise append
        val existingIndex = currentAccounts.indexOfFirst { 
            it.id == account.id || (it.projectUrl.trim().equals(account.projectUrl.trim(), ignoreCase = true) && it.apiKey.trim() == account.apiKey.trim())
        }
        if (existingIndex != -1) {
            currentAccounts[existingIndex] = account
        } else {
            currentAccounts.add(account)
        }
        writeAccountsToAllStorages(currentAccounts)
    }

    override fun getAllAccounts(): List<SupabaseAccount> {
        val combined = mutableListOf<SupabaseAccount>()
        val deletedIds = getDeletedAccountIds()

        // 1. Read from primary disk file
        if (accountsFile.exists()) {
            try {
                val json = accountsFile.readText()
                combined.addAll(parseAccountsJson(json))
            } catch (e: Exception) {
                Log.w(tag, "Error reading accounts file", e)
            }
        }

        // 2. Read from plain SharedPreferences
        val plainJson = plainPrefs.getString("accounts", null)
        if (!plainJson.isNullOrBlank()) {
            combined.addAll(parseAccountsJson(plainJson))
        }

        // 3. Read from backup SharedPreferences
        val backupJson = backupPrefs.getString("accounts", null)
        if (!backupJson.isNullOrBlank()) {
            combined.addAll(parseAccountsJson(backupJson))
        }

        // 4. Read from legacy SharedPreferences
        val legacyJson = legacyPrefs.getString("accounts", null)
        if (!legacyJson.isNullOrBlank()) {
            combined.addAll(parseAccountsJson(legacyJson))
        }

        // 5. Read from EncryptedSharedPreferences (if active)
        try {
            val encJson = encryptedPrefs?.getString("accounts", null)
            if (!encJson.isNullOrBlank()) {
                combined.addAll(parseAccountsJson(encJson))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading from encryptedPrefs", e)
        }

        // 6. Read from file backup
        if (combined.isEmpty() && accountsBackupFile.exists()) {
            try {
                val json = accountsBackupFile.readText()
                combined.addAll(parseAccountsJson(json))
            } catch (e: Exception) {
                Log.w(tag, "Error reading accounts backup file", e)
            }
        }

        // 7. Dynamic scan of all existing shared_prefs XML files ONLY if rescue was never done
        val isRescueDone = plainPrefs.getBoolean(prefKeyLegacyRescueDone, false)
        if (combined.isEmpty() && !isRescueDone) {
            combined.addAll(scanLegacyXmlForAccounts())
            plainPrefs.edit().putBoolean(prefKeyLegacyRescueDone, true).commit()
        }

        // Filter out any explicitly deleted accounts
        combined.removeAll { 
            it.id in deletedIds || it.projectUrl.trim() in deletedIds || it.effectiveTag in deletedIds
        }

        // Deduplicate accounts by ID, or fallback to projectUrl + apiKey
        val distinctAccounts = combined.distinctBy { 
            if (it.id.isNotBlank()) it.id else "${it.projectUrl.trim().lowercase()}_${it.apiKey.trim()}" 
        }

        return distinctAccounts
    }

    override fun deleteAccount(accountId: String) {
        val targetId = accountId.trim()
        val deletedSet = getDeletedAccountIds().toMutableSet()
        deletedSet.add(targetId)

        val currentAccounts = getAllAccounts().toMutableList()
        val toRemove = currentAccounts.filter { 
            it.id == targetId || 
            it.projectUrl.trim().equals(targetId, ignoreCase = true) ||
            it.name.trim().equals(targetId, ignoreCase = true) ||
            it.effectiveTag.equals(targetId, ignoreCase = true)
        }
        for (item in toRemove) {
            deletedSet.add(item.id)
            deletedSet.add(item.projectUrl.trim())
            deletedSet.add(item.effectiveTag)
        }
        currentAccounts.removeAll(toRemove)

        saveDeletedAccountIds(deletedSet)
        writeAccountsToAllStorages(currentAccounts)

        // Also clean up any master key associations
        cleanUpDeletedAccountFromMasterKeys(deletedSet)
    }

    override fun toggleAccountStatus(accountId: String): SupabaseAccount? {
        val targetId = accountId.trim()
        val currentAccounts = getAllAccounts().toMutableList()
        val index = currentAccounts.indexOfFirst {
            it.id == targetId ||
            it.name.trim().equals(targetId, ignoreCase = true) ||
            it.effectiveTag.equals(targetId, ignoreCase = true)
        }
        if (index == -1) return null

        val updated = currentAccounts[index].copy(isActive = !currentAccounts[index].isActive)
        currentAccounts[index] = updated
        writeAccountsToAllStorages(currentAccounts)
        return updated
    }

    private fun cleanUpDeletedAccountFromMasterKeys(deletedIds: Set<String>) {
        val keys = getAllMasterKeys().toMutableList()
        var changed = false
        for (i in keys.indices) {
            val key = keys[i]
            if (key.connectedAccountIds.any { it in deletedIds }) {
                val updatedIds = key.connectedAccountIds.filter { it !in deletedIds }
                val updatedNames = key.connectedAccountNames // keep or filter
                keys[i] = key.copy(connectedAccountIds = updatedIds)
                changed = true
            }
        }
        if (changed) {
            writeMasterKeysToAllStorages(keys)
        }
    }

    private fun writeAccountsToAllStorages(accounts: List<SupabaseAccount>) {
        val json = accountAdapter.toJson(accounts)

        // 1. Direct synchronous file write
        try {
            accountsFile.writeText(json)
            accountsBackupFile.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving accounts to disk file", e)
        }

        // 2. Plain SharedPreferences synchronous commit
        plainPrefs.edit().putString("accounts", json).commit()

        // 3. Backup SharedPreferences synchronous commit
        backupPrefs.edit().putString("accounts", json).commit()

        // 4. Legacy SharedPreferences synchronous commit
        legacyPrefs.edit().putString("accounts", json).commit()

        // 5. Encrypted SharedPreferences commit
        try {
            encryptedPrefs?.edit()?.putString("accounts", json)?.commit()
        } catch (e: Exception) {
            Log.w(tag, "Error writing to encryptedPrefs", e)
        }
    }

    // ================= MASTER KEY OPERATIONS =================
    fun getAllMasterKeys(): List<MasterKey> {
        val combined = mutableListOf<MasterKey>()

        // 1. Disk file
        if (masterKeysFile.exists()) {
            try {
                val json = masterKeysFile.readText()
                combined.addAll(parseMasterKeysJson(json))
            } catch (e: Exception) {
                Log.w(tag, "Error reading master keys file", e)
            }
        }

        // 2. Plain prefs
        val plainJson = plainPrefs.getString("master_keys", null)
        if (!plainJson.isNullOrBlank()) {
            combined.addAll(parseMasterKeysJson(plainJson))
        }

        // 3. Backup prefs
        val backupJson = backupPrefs.getString("master_keys", null)
        if (!backupJson.isNullOrBlank()) {
            combined.addAll(parseMasterKeysJson(backupJson))
        }

        // 4. Encrypted prefs
        try {
            val encJson = encryptedPrefs?.getString("master_keys", null)
            if (!encJson.isNullOrBlank()) {
                combined.addAll(parseMasterKeysJson(encJson))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading master_keys from encryptedPrefs", e)
        }

        // Filter out legacy default key if present
        combined.removeAll { it.key == "omni_live_master_default_2026" }

        val distinctKeys = combined.distinctBy { it.key }.toMutableList()

        if (distinctKeys.isNotEmpty()) {
            writeMasterKeysToAllStorages(distinctKeys)
        }

        return distinctKeys
    }

    fun saveMasterKey(masterKey: MasterKey) {
        val keys = getAllMasterKeys().toMutableList()
        keys.removeAll { it.key == masterKey.key }
        keys.add(0, masterKey)
        writeMasterKeysToAllStorages(keys)
        syncMasterKeyToRemote(masterKey, "POST")
    }

    fun deleteMasterKey(key: String) {
        val keys = getAllMasterKeys().toMutableList()
        val toDelete = keys.find { it.key == key }
        keys.removeAll { it.key == key }
        writeMasterKeysToAllStorages(keys)
        if (toDelete != null) {
            syncMasterKeyToRemote(toDelete, "DELETE")
        } else {
            syncMasterKeyToRemote(MasterKey(key = key, name = "", created = ""), "DELETE")
        }
    }

    fun toggleMasterKeyStatus(key: String): MasterKey? {
        val keys = getAllMasterKeys().toMutableList()
        val index = keys.indexOfFirst { it.key == key }
        if (index != -1) {
            val current = keys[index]
            val updated = current.copy(
                status = if (current.status == "ACTIVE") "REVOKED" else "ACTIVE"
            )
            keys[index] = updated
            writeMasterKeysToAllStorages(keys)
            syncMasterKeyToRemote(updated, "PATCH")
            return updated
        }
        return null
    }

    fun generateNewMasterKey(
        name: String,
        connectedAccountIds: List<String> = emptyList(),
        connectedAccountNames: List<String> = emptyList()
    ): MasterKey {
        val rng = SecureRandom()
        val bytes = ByteArray(12)
        rng.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val key = "omni_live_$hex"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val record = MasterKey(
            key = key,
            name = name.ifBlank { "Master Router Key " + (getAllMasterKeys().size + 1) },
            created = today,
            status = "ACTIVE",
            rateLimit = "UNLIMITED",
            connectedAccountIds = connectedAccountIds,
            connectedAccountNames = connectedAccountNames
        )
        val keys = getAllMasterKeys().toMutableList()
        keys.add(0, record)
        writeMasterKeysToAllStorages(keys)
        syncMasterKeyToRemote(record, "POST")
        return record
    }

    private fun syncMasterKeyToRemote(masterKey: MasterKey, action: String) {
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val jsonMediaType = "application/json; charset=utf-8".toMediaType()

                val jsonBody = masterKeyAdapter.toJson(listOf(masterKey))
                val singleJson = if (jsonBody.startsWith("[") && jsonBody.endsWith("]")) {
                    jsonBody.substring(1, jsonBody.length - 1)
                } else jsonBody

                if (action == "POST" || action == "UPSERT") {
                    try {
                        val req1 = Request.Builder()
                            .url(AppConstants.API_KEYS_URL)
                            .post(singleJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req1).execute().close()
                    } catch (e: Exception) {}

                    try {
                        val req2 = Request.Builder()
                            .url("${AppConstants.MASTER_URL}/rest/v1/supabase_master_keys")
                            .header("apikey", AppConstants.MASTER_KEY)
                            .header("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                            .header("Prefer", "return=representation")
                            .post(singleJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req2).execute().close()
                    } catch (e: Exception) {}
                } else if (action == "DELETE") {
                    try {
                        val req1 = Request.Builder()
                            .url("${AppConstants.API_KEYS_URL}?key=${URLEncoder.encode(masterKey.key, "UTF-8")}")
                            .delete()
                            .build()
                        client.newCall(req1).execute().close()
                    } catch (e: Exception) {}

                    try {
                        val req2 = Request.Builder()
                            .url("${AppConstants.MASTER_URL}/rest/v1/supabase_master_keys?key=eq.${URLEncoder.encode(masterKey.key, "UTF-8")}")
                            .header("apikey", AppConstants.MASTER_KEY)
                            .header("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                            .delete()
                            .build()
                        client.newCall(req2).execute().close()
                    } catch (e: Exception) {}
                } else if (action == "PATCH") {
                    val statusJson = "{\"status\":\"${masterKey.status}\"}"
                    try {
                        val req1 = Request.Builder()
                            .url("${AppConstants.API_KEYS_URL}?key=${URLEncoder.encode(masterKey.key, "UTF-8")}")
                            .patch(statusJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req1).execute().close()
                    } catch (e: Exception) {}

                    try {
                        val req2 = Request.Builder()
                            .url("${AppConstants.MASTER_URL}/rest/v1/supabase_master_keys?key=eq.${URLEncoder.encode(masterKey.key, "UTF-8")}")
                            .header("apikey", AppConstants.MASTER_KEY)
                            .header("Authorization", "Bearer ${AppConstants.MASTER_KEY}")
                            .header("Content-Type", "application/json")
                            .patch(statusJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req2).execute().close()
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(tag, "syncMasterKeyToRemote exception: ${e.message}")
            }
        }.start()
    }

    private fun writeMasterKeysToAllStorages(keys: List<MasterKey>) {
        val json = masterKeyAdapter.toJson(keys)

        try {
            masterKeysFile.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving master keys to disk file", e)
        }

        plainPrefs.edit().putString("master_keys", json).commit()
        backupPrefs.edit().putString("master_keys", json).commit()
        legacyPrefs.edit().putString("master_keys", json).commit()

        try {
            encryptedPrefs?.edit()?.putString("master_keys", json)?.commit()
        } catch (e: Exception) {
            Log.w(tag, "Error writing master_keys to encryptedPrefs", e)
        }
    }

    // Dynamic rescue scanner for previous accounts saved in any XML file in shared_prefs/
    private fun scanLegacyXmlForAccounts(): List<SupabaseAccount> {
        val results = mutableListOf<SupabaseAccount>()
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                val xmlFiles = prefsDir.listFiles { _, fileName -> fileName.endsWith(".xml") } ?: emptyArray()
                for (file in xmlFiles) {
                    try {
                        val prefName = file.name.removeSuffix(".xml")
                        val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        val rawAccounts = sp.getString("accounts", null)
                        if (!rawAccounts.isNullOrBlank()) {
                            val parsed = parseAccountsJson(rawAccounts)
                            if (parsed.isNotEmpty()) {
                                results.addAll(parsed)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore individual file error
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Legacy shared_prefs rescue scan failed", e)
        }
        return results
    }

    private fun syncAndRescueData() {
        val accounts = getAllAccounts()
        if (accounts.isNotEmpty()) {
            writeAccountsToAllStorages(accounts)
        }
        // Mark legacy rescue as done so deleted accounts don't resurrect
        plainPrefs.edit().putBoolean(prefKeyLegacyRescueDone, true).commit()

        val masterKeys = getAllMasterKeys()
        if (masterKeys.isNotEmpty()) {
            writeMasterKeysToAllStorages(masterKeys)
        }
    }
}
