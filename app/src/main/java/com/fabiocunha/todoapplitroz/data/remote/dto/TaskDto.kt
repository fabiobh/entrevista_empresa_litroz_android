package com.fabiocunha.todoapplitroz.data.remote.dto

/**
 * Data Transfer Object for API communication
 */
data class TaskDto(
    val id: String,
    val description: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)