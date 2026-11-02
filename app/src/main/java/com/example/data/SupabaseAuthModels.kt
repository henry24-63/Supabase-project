package com.example.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class SupabaseManagementTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String?,
    val user: SupabaseManagementUser?
)

data class SupabaseManagementUser(
    val id: String,
    val email: String?,
    val app_metadata: Map<String, Any>?
)

data class SupabaseProject(
    val id: String,
    val name: String,
    val slug: String,
    val region: String?,
    @com.squareup.moshi.Json(name = "created_at") val createdAt: String?,
    @com.squareup.moshi.Json(name = "updated_at") val updatedAt: String?
)

data class SupabaseProjectApiKeys(
    val anon_public: String?,
    @com.squareup.moshi.Json(name = "service_role") val serviceRoleKey: String?
)

sealed class SmartFetchResult {
    data class Success(val projects: List<SupabaseProject>) : SmartFetchResult()
    data class Error(val message: String) : SmartFetchResult()
    object Loading : SmartFetchResult()
    object Idle : SmartFetchResult()
}

interface SupabaseManagementApi {
    @POST("v1/auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): SupabaseManagementTokenResponse

    @GET("v1/projects")
    suspend fun getProjects(
        @Header("Authorization") auth: String
    ): List<SupabaseProject>

    @GET("v1/projects/{ref}/api-keys")
    suspend fun getProjectApiKeys(
        @Header("Authorization") auth: String,
        @Path("ref") ref: String
    ): SupabaseProjectApiKeys
}
