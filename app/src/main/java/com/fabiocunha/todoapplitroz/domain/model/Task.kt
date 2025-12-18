package com.fabiocunha.todoapplitroz.domain.model

/**
 * Domain model representing a task in the system
 */
data class Task(
    val id: String,
    val description: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus
)

/**
 * Enum representing the synchronization status of a task
 */
enum class SyncStatus {
    SYNCED,           // In sync with remote
    PENDING_CREATE,   // Created locally, needs upload
    PENDING_UPDATE,   // Modified locally, needs sync
    PENDING_DELETE    // Deleted locally, needs remote deletion
}