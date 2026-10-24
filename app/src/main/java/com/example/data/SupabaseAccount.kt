package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseAccount(
    val id: String,
    val name: String,
    val projectUrl: String,
    val apiKey: String,
    @param:Json(name = "publishable_key")
    val publishableKey: String = "",
    @param:Json(name = "secret_key")
    val secretKey: String = "",
    @param:Json(name = "service_role_key")
    val serviceRoleKey: String = "",
    @param:Json(name = "anon_key")
    val anonKey: String = "",
    @param:Json(name = "db_connection_string")
    val dbConnectionString: String = "",
    val databasePassword: String,
    @param:Json(name = "routing_tag")
    val routingTag: String = "",
    @param:Json(name = "is_active")
    val isActive: Boolean = true
) {
    val effectiveSecretKey: String
        get() = secretKey.ifBlank { serviceRoleKey.ifBlank { apiKey } }

    val effectivePublishableKey: String
        get() = publishableKey.ifBlank { anonKey.ifBlank { apiKey } }

    val effectiveTag: String
        get() {
            val raw = if (routingTag.isNotBlank()) routingTag else name
            val cleaned = raw.trim().lowercase()
                .replace(" ", "-")
                .replace(Regex("[^a-z0-9_-]"), "")
            return if (cleaned.isNotBlank()) cleaned else {
                projectUrl.removePrefix("https://")
                    .removePrefix("http://")
                    .split(".")
                    .firstOrNull()
                    ?.lowercase()
                    ?.replace(Regex("[^a-z0-9_-]"), "") ?: "account"
            }
        }
}
