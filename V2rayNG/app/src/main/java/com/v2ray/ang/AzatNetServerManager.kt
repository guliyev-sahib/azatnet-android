package com.v2ray.ang

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AzatNet Server Manager
 * Загружает список серверов с бэкенда и автоматически переключается
 * на работающий сервер.
 *
 * Как работает:
 * 1. При запуске приложения вызывается loadServers()
 * 2. Серверы сортированы по приоритету (чем выше — тем важнее)
 * 3. При подключении пробуем серверы по очереди — первый рабочий используем
 * 4. Если текущий сервер падает во время работы — переключаемся на следующий
 */
object AzatNetServerManager {

    private const val TAG = "AzatNetServerManager"
    private const val API_BASE = "http://80.65.211.99:8000"
    private const val PREFS_NAME = "azatnet_servers"
    private const val PREFS_KEY_SERVERS = "cached_servers"
    private const val PREFS_KEY_LAST_UPDATE = "last_update"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 минут

    data class VpnServer(
        val id: Int,
        val name: String,
        val address: String,
        val port: Int,
        val uuid: String,
        val wsPath: String,
        val sni: String,
        val priority: Int
    )

    // Fallback сервер — используется если API недоступен
    val FALLBACK_SERVER = VpnServer(
        id = 0,
        name = "AzatNet",
        address = "vpn.bilgibox.xyz",
        port = 443,
        uuid = "8ad39014-ff27-4172-8211-db3b8bb9fd19",
        wsPath = "/vpn",
        sni = "vpn.bilgibox.xyz",
        priority = 1
    )

    private var servers: List<VpnServer> = listOf(FALLBACK_SERVER)
    private var currentServerIndex = 0

    /**
     * Загружает серверы с API (с кешированием)
     * Вызывать при старте приложения
     */
    suspend fun loadServers(context: Context): List<VpnServer> {
        return withContext(Dispatchers.IO) {
            try {
                // Проверяем кеш
                val cached = getCachedServers(context)
                if (cached != null) {
                    servers = cached
                    Log.d(TAG, "Loaded ${servers.size} servers from cache")
                    return@withContext servers
                }

                // Загружаем с API
                val url = URL("$API_BASE/servers")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val arr = json.getJSONArray("servers")

                val list = mutableListOf<VpnServer>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        VpnServer(
                            id = obj.getInt("id"),
                            name = obj.getString("name"),
                            address = obj.getString("address"),
                            port = obj.getInt("port"),
                            uuid = obj.getString("uuid"),
                            wsPath = obj.getString("ws_path"),
                            sni = obj.getString("sni"),
                            priority = obj.getInt("priority")
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    servers = list.sortedByDescending { it.priority }
                    currentServerIndex = 0
                    cacheServers(context, servers)
                    Log.d(TAG, "Loaded ${servers.size} servers from API")
                }

                servers
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load servers from API, using fallback: ${e.message}")
                servers = listOf(FALLBACK_SERVER)
                servers
            }
        }
    }

    /**
     * Возвращает текущий активный сервер
     */
    fun getCurrentServer(): VpnServer {
        return if (servers.isNotEmpty() && currentServerIndex < servers.size) {
            servers[currentServerIndex]
        } else {
            FALLBACK_SERVER
        }
    }

    /**
     * Переключается на следующий сервер
     * Возвращает null если серверов больше нет
     */
    fun switchToNextServer(): VpnServer? {
        currentServerIndex++
        return if (currentServerIndex < servers.size) {
            val next = servers[currentServerIndex]
            Log.d(TAG, "Switching to server: ${next.name} (${next.address})")
            next
        } else {
            Log.w(TAG, "No more servers available, resetting to first")
            currentServerIndex = 0
            null
        }
    }

    /**
     * Сбрасывает на первый (основной) сервер
     */
    fun resetToFirstServer() {
        currentServerIndex = 0
    }

    /**
     * Количество доступных серверов
     */
    fun getServerCount(): Int = servers.size

    /**
     * Список всех серверов
     */
    fun getAllServers(): List<VpnServer> = servers

    // ─── Cache ───

    private fun cacheServers(context: Context, list: List<VpnServer>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = buildString {
            append("[")
            list.forEachIndexed { i, s ->
                if (i > 0) append(",")
                append("""{"id":${s.id},"name":"${s.name}","address":"${s.address}","port":${s.port},"uuid":"${s.uuid}","ws_path":"${s.wsPath}","sni":"${s.sni}","priority":${s.priority}}""")
            }
            append("]")
        }
        prefs.edit()
            .putString(PREFS_KEY_SERVERS, json)
            .putLong(PREFS_KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedServers(context: Context): List<VpnServer>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(PREFS_KEY_LAST_UPDATE, 0)
        if (System.currentTimeMillis() - lastUpdate > CACHE_TTL_MS) return null

        val json = prefs.getString(PREFS_KEY_SERVERS, null) ?: return null
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<VpnServer>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VpnServer(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        address = obj.getString("address"),
                        port = obj.getInt("port"),
                        uuid = obj.getString("uuid"),
                        wsPath = obj.getString("ws_path"),
                        sni = obj.getString("sni"),
                        priority = obj.getInt("priority")
                    )
                )
            }
            list
        } catch (e: Exception) {
            null
        }
    }
}
