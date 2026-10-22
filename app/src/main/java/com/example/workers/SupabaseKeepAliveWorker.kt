package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.EncryptedSupabaseAccountRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class SupabaseKeepAliveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository by lazy { EncryptedSupabaseAccountRepository(context) }
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val accounts = repository.getAllAccounts()
        
        for (account in accounts) {
            try {
                // Ping the Supabase REST API to keep the project active
                val request = Request.Builder()
                    .url("${account.projectUrl}/rest/v1/")
                    .addHeader("apikey", account.apiKey)
                    .addHeader("Authorization", "Bearer ${account.apiKey}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Log failure
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
        
        return Result.success()
    }
}
