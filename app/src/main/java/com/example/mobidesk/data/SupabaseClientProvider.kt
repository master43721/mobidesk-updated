package com.example.mobidesk.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://mfptgagzizgdrabjadmy.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mcHRnYWd6aXpnZHJhYmphZG15Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAwNDgyOTgsImV4cCI6MjA5NTYyNDI5OH0.JTgLIdw8FcZor7xDO8hllibjVlRGem6aFRP3ZgTjcv0"
    ) {
        install(Auth)
        install(Postgrest)
    }
}
