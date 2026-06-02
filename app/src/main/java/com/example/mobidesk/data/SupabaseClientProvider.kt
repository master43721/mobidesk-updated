package com.example.mobidesk.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SupabaseClientProvider {
    private var _client: SupabaseClient? = null
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseClient not initialized. Call init(context) first.")

    fun init(context: Context) {
        if (_client == null) {
            _client = createSupabaseClient(
                supabaseUrl = "https://mfptgagzizgdrabjadmy.supabase.co",
                supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mcHRnYWd6aXpnZHJhYmphZG15Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAwNDgyOTgsImV4cCI6MjA5NTYyNDI5OH0.JTgLIdw8FcZor7xDO8hllibjVlRGem6aFRP3ZgTjcv0"
            ) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        private val prefs = context.getSharedPreferences("mobidesk_auth", Context.MODE_PRIVATE)
                        override suspend fun saveSession(session: UserSession) {
                            prefs.edit().putString("supabase_session", Json.encodeToString(session)).apply()
                        }
                        override suspend fun loadSession(): UserSession? {
                            val sessionStr = prefs.getString("supabase_session", null) ?: return null
                            return try { Json.decodeFromString<UserSession>(sessionStr) } catch(e: Exception) { null }
                        }
                        override suspend fun deleteSession() {
                            prefs.edit().remove("supabase_session").apply()
                        }
                    }
                }
                install(Postgrest)
            }
        }
    }
}
