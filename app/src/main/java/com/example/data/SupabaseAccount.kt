package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseAccount(
    val id: String,
    val name: String,
    val projectUrl: String,
    val apiKey: String,
    val databasePassword: String
)
