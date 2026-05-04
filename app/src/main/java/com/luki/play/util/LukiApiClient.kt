package com.luki.play.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP ligero para la API REST de Luki Play.
 */
object LukiApiClient {

    private const val TAG = "LukiApiClient"
    private const val API_BASE = "http://98.80.97.51"
    private const val TIMEOUT_MS = 10_000

    data class Channel(
        val id: String,
        val nombre: String,
        val logo: String?,
        val categoria: String,
        val tipo: String,
        val requiereControlParental: Boolean
    ) {
        val logoUrl: String?
            get() = logo?.let {
                if (it.startsWith("http")) it else "$API_BASE$it"
            }
    }

    data class Slider(
        val id: String,
        val titulo: String,
        val subtitulo: String?,
        val imagen: String?,
        val actionType: String,
        val actionValue: String?
    ) {
        val imagenUrl: String?
            get() = imagen?.let {
                if (it.startsWith("http")) it else "$API_BASE$it"
            }
    }

    data class AuthResult(
        val accessToken: String,
        val refreshToken: String?,
        val userId: String,
        val displayName: String
    )

    data class StreamResult(
        val streamUrl: String,
        val sessionId: String?
    )

    fun getPublicChannels(): List<Channel> {
        val json = get("/public/canales") ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Channel(
                    id                    = obj.getString("id"),
                    nombre                = obj.getString("nombre"),
                    logo                  = obj.optString("logo").ifBlank { null },
                    categoria             = obj.optString("categoria", "General"),
                    tipo                  = obj.optString("tipo", "tv"),
                    requiereControlParental = obj.optBoolean("requiereControlParental", false)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels", e)
            emptyList()
        }
    }

    fun getChannelStreamUrl(channelId: String, token: String): StreamResult? {
        val json = get("/public/canales/$channelId/stream", token) ?: return null
        return try {
            val obj = JSONObject(json)
            StreamResult(
                streamUrl = obj.getString("url"),
                sessionId = obj.optString("sessionId").ifBlank { null }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stream", e)
            null
        }
    }

    fun getSliders(): List<Slider> {
        val json = get("/public/sliders") ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Slider(
                    id          = obj.getString("id"),
                    titulo      = obj.getString("titulo"),
                    subtitulo   = obj.optString("subtitulo").ifBlank { null },
                    imagen      = obj.optString("imagen").ifBlank { null },
                    actionType  = obj.optString("actionType", "NONE"),
                    actionValue = obj.optString("actionValue").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sliders", e)
            emptyList()
        }
    }

    fun loginWithId(idNumber: String, password: String, deviceId: String): AuthResult? {
        val body = JSONObject().apply {
            put("idNumber", idNumber)
            put("password", password)
            put("deviceId", deviceId)
        }
        val json = post("/auth/app/id-login", body.toString()) ?: return null
        return parseAuthResult(json)
    }

    fun loginWithContract(contractNumber: String, password: String, deviceId: String): AuthResult? {
        val body = JSONObject().apply {
            put("contractNumber", contractNumber)
            put("password", password)
            put("deviceId", deviceId)
        }
        val json = post("/auth/app/contract-login", body.toString()) ?: return null
        return parseAuthResult(json)
    }

    private fun get(path: String, token: String? = null): String? {
        return try {
            val url = URL("$API_BASE$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                Log.w(TAG, "GET $path -> ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed", e)
            null
        }
    }

    private fun post(path: String, body: String, token: String? = null): String? {
        return try {
            val url = URL("$API_BASE$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                outputStream.write(body.toByteArray(Charsets.UTF_8))
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.readText()
            Log.d(TAG, "POST $path -> ${conn.responseCode}: $response")
            if (conn.responseCode in 200..299) response else null
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            null
        }
    }

    private fun parseAuthResult(json: String): AuthResult? {
        return try {
            val obj = JSONObject(json)
            AuthResult(
                accessToken  = obj.getString("accessToken"),
                refreshToken = obj.optString("refreshToken").ifBlank { null },
                userId       = obj.optString("userId", obj.optString("id", "")),
                displayName  = obj.optString("nombre", obj.optString("name", ""))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing auth result", e)
            null
        }
    }
}
