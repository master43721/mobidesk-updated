package com.example.mobidesk.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest

class AuthRepository {
    private val supabase = SupabaseClientProvider.client

    // Regex for Roll Number (Example: Y20IT001 or similar RVRJC format)
    private val rollNumberRegex = Regex("^[A-Z][0-9]{2}[A-Z]{2}[0-9]{3}$", RegexOption.IGNORE_CASE)

    suspend fun authenticateStudent(rollNumber: String, password: String): Result<VmDetails> {
        // 1. Validate Regex Format
        if (!rollNumberRegex.matches(rollNumber)) {
            return Result.failure(Exception("Invalid Roll Number format"))
        }

        return try {
            val email = "${rollNumber.lowercase()}@rvrjc.ac.in"
            
            // Log in via Supabase Auth
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            val userUuid = supabase.auth.currentSessionOrNull()?.user?.id 
                ?: return Result.failure(Exception("Session initialization failed."))

            // Fetch student profile row matching the Auth UUID
            val student = supabase.postgrest.from("students")
                .select { filter { eq("id", userUuid) } }
                .decodeSingle<StudentProfile>()

            // Fetch the corresponding VM profile using the assigned vm_username
            val vm = supabase.postgrest.from("vm_profiles")
                .select { filter { eq("vm_username", student.vm_username) } }
                .decodeSingle<VmProfile>()

            Result.success(
                VmDetails(
                    vm_username = student.vm_username,
                    current_ip = vm.current_ip ?: "135.119.92.61", // default backup fallback IP
                    name = student.name,
                    assigned_vm = vm.assigned_vm,
                    rdp_port = vm.rdp_port
                )
            )
        } catch (e: Exception) {
            // Check for specific error messages if needed, otherwise return the exception
            if (e.message?.contains("Invalid login credentials", ignoreCase = true) == true) {
                Result.failure(Exception("Password wrong or account not found"))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun logout() {
        supabase.auth.signOut()
    }
}
