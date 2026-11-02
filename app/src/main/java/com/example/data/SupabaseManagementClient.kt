package com.example.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object SupabaseManagementClient {
    private const val MANAGEMENT_BASE_URL = "https://api.supabase.com"

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi = com.squareup.moshi.Moshi.Builder().build()

    val managementApi: SupabaseManagementApi = Retrofit.Builder()
        .baseUrl(MANAGEMENT_BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SupabaseManagementApi::class.java)
}