package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseAccount(
    val id: String,
    val name: String,
    val projectUrl: String,
    val apiKey: String,
    val databasePassword: String,
    @param:Json(name = "routing_tag")
    val routingTag: String = "",
    @param:Json(name = "is_active")
    val isActive: Boolean = true
) {
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
