package com.example.mobilab.data

import kotlinx.coroutines.delay

enum class UserRole {
    STUDENT, ADMIN
}

data class AuthResponse(
    val success: Boolean,
    val role: UserRole? = null,
    val token: String? = null,
    val studentName: String? = null,
    val assignedVmId: String? = null,
    val errorMessage: String? = null
)

data class SessionResponse(
    val sessionId: String,
    val vmIp: String,
    val token: String,
    val protocol: String,
    val status: String
)

object MockApiService {
    suspend fun authenticateUser(username: String, pin: String): AuthResponse {
        delay(1200) // 1.2s mock network validation delay
        return when {
            username == "student_labs_01" && pin == "1234" -> {
                AuthResponse(
                    success = true,
                    role = UserRole.STUDENT,
                    token = "rvrjc_token_std_8899",
                    studentName = "Sudheendra Sripada",
                    assignedVmId = "8f3b9c74"
                )
            }
            username == "admin_labs_01" && pin == "9999" -> {
                AuthResponse(
                    success = true,
                    role = UserRole.ADMIN,
                    token = "rvrjc_token_adm_1122",
                    studentName = "Professor Ramesh (Lab Admin)",
                    assignedVmId = null
                )
            }
            else -> {
                AuthResponse(
                    success = false,
                    errorMessage = "ACCESS DENIED: INVALID INSTITUTIONAL CREDENTIALS"
                )
            }
        }
    }

    suspend fun requestSession(vmId: String): SessionResponse {
        delay(800) 
        return SessionResponse(
            sessionId = "sess-rvrjc-4c11",
            vmIp = "135.119.92.61", // Publicly hosted VM IP
            token = "rvrjc_handshake_7788",
            protocol = "rdp",
            status = "ready"
        )
    }
}
