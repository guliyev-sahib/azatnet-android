package com.v2ray.ang.handler

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ActivationManager {
    private const val BASE_URL = "http://80.65.211.99:8000"
    
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences("azatnet_prefs", Context.MODE_PRIVATE)
        val expiresAt = prefs.getString("expires_at", null) ?: return false
        return try {
            val expires = java.time.LocalDateTime.parse(expiresAt)
            expires.isAfter(java.time.LocalDateTime.now())
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun activateCode(context: Context, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId(context)
            val url = URL("$BASE_URL/codes/activate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = """{"code":"$code","device_id":"$deviceId"}"""
            conn.outputStream.write(body.toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val expiresAt = json.getString("expires_at")
            context.getSharedPreferences("azatnet_prefs", Context.MODE_PRIVATE)
                .edit().putString("expires_at", expiresAt).apply()
            Result.success(expiresAt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkStatus(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId(context)
            val url = URL("$BASE_URL/codes/check/$deviceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val expiresAt = json.getString("expires_at")
            context.getSharedPreferences("azatnet_prefs", Context.MODE_PRIVATE)
                .edit().putString("expires_at", expiresAt).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
