package com.example.mobidesk.data

import kotlinx.serialization.Serializable

@Serializable
data class VmDetails(
    val vm_username: String,
    val current_ip: String,
    val name: String,
    val assigned_vm: String,
    val rdp_port: String
)

@Serializable
data class StudentProfile(
    val id: String,
    val name: String,
    val roll_number: String,
    val vm_username: String
)

@Serializable
data class VmProfile(
    val vm_username: String,
    val current_ip: String? = null,
    val status: String,
    val assigned_vm: String = "",
    val rdp_port: String = "3389"
)
