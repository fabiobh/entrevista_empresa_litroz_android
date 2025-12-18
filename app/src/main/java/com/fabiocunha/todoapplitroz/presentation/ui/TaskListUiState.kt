package com.fabiocunha.todoapplitroz.presentation.ui

import com.fabiocunha.todoapplitroz.domain.model.Task

/**
 * UI state data class for the task list screen
 * Represents different states the screen can be in
 */
data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val error: String? = null
)

/**
 * Sealed class representing different UI states for task operations
 */
sealed class TaskOperationState {
    object Idle : TaskOperationState()
    object Loading : TaskOperationState()
    data class Success(val message: String) : TaskOperationState()
    data class Error(val message: String) : TaskOperationState()
}

/**
 * UI state for individual task items
 */
data class TaskItemUiState(
    val task: Task,
    val isEditing: Boolean = false,
    val isDeleting: Boolean = false
)