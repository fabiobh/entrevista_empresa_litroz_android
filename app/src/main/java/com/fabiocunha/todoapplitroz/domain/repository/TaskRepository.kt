package com.fabiocunha.todoapplitroz.domain.repository

import com.fabiocunha.todoapplitroz.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for task operations
 */
interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    suspend fun createTask(description: String): Result<Task>
    suspend fun updateTask(task: Task): Result<Task>
    suspend fun deleteTask(taskId: String): Result<Unit>
    suspend fun syncWithRemote(): Result<Unit>
}