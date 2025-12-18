package com.fabiocunha.todoapplitroz.data.remote

import android.util.Log
import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.data.remote.dto.TaskDto
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source that wraps API service calls and handles network operations
 */
@Singleton
class RemoteTaskDataSource @Inject constructor(
    private val apiService: TaskApiService
) {
    
    companion object {
        private const val TAG = "RemoteTaskDataSource"
    }
    
    /**
     * Tests API connectivity by attempting to fetch all tasks
     * This is useful for debugging API connection issues
     */
    suspend fun testApiConnectivity(): Result<String> {
        return try {
            Log.d(TAG, "Testing API connectivity...")
            val response = apiService.getAllTasks()
            
            val message = when {
                response.isSuccessful -> {
                    val taskCount = response.body()?.size ?: 0
                    "API connection successful! Found $taskCount tasks."
                }
                response.code() == 404 -> {
                    "API endpoint not found (404). Please check if 'tasks' resource exists in mockapi.io"
                }
                response.code() in 400..499 -> {
                    "Client error (${response.code()}): ${response.message()}"
                }
                response.code() in 500..599 -> {
                    "Server error (${response.code()}): ${response.message()}"
                }
                else -> {
                    "Unknown error (${response.code()}): ${response.message()}"
                }
            }
            
            Log.i(TAG, "API Test Result: $message")
            if (response.isSuccessful) {
                Result.success(message)
            } else {
                Result.failure(NetworkException(message))
            }
        } catch (e: IOException) {
            val errorMsg = "Network connectivity test failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "API connectivity test failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        }
    }
    
    /**
     * Fetches all tasks from the remote API
     * @return Result containing list of tasks or error
     */
    suspend fun getAllTasks(): Result<List<Task>> {
        return try {
            Log.d(TAG, "Fetching all tasks from API...")
            val response = apiService.getAllTasks()
            Log.d(TAG, "API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val tasks = response.body()?.map { it.toDomainModel() } ?: emptyList()
                Log.d(TAG, "Successfully fetched ${tasks.size} tasks")
                Result.success(tasks)
            } else {
                val errorMsg = "Failed to fetch tasks: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                try {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody != null) {
                        Log.e(TAG, "Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error body: ${e.message}")
                }
                Result.failure(NetworkException(errorMsg))
            }
        } catch (e: IOException) {
            val errorMsg = "Network error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        }
    }
    
    /**
     * Creates a new task on the remote API
     * @param task The task to create
     * @return Result containing the created task or error
     */
    suspend fun createTask(task: Task): Result<Task> {
        return try {
            val taskDto = task.toDto()
            Log.d(TAG, "Creating task: ${taskDto.description} (ID: ${taskDto.id})")
            
            val response = apiService.createTask(taskDto)
            Log.d(TAG, "Create API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val createdTask = response.body()?.toDomainModel()
                if (createdTask != null) {
                    Log.d(TAG, "Successfully created task: ${createdTask.description}")
                    Result.success(createdTask)
                } else {
                    val errorMsg = "Empty response body when creating task"
                    Log.e(TAG, errorMsg)
                    Result.failure(NetworkException(errorMsg))
                }
            } else {
                val errorMsg = "Failed to create task: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                try {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody != null) {
                        Log.e(TAG, "Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error body: ${e.message}")
                }
                Result.failure(NetworkException(errorMsg))
            }
        } catch (e: IOException) {
            val errorMsg = "Network error when creating task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error when creating task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        }
    }
    
    /**
     * Updates an existing task on the remote API
     * @param task The task to update
     * @return Result containing the updated task or error
     */
    suspend fun updateTask(task: Task): Result<Task> {
        return try {
            val taskDto = task.toDto()
            Log.d(TAG, "Updating task: ${taskDto.description} (ID: ${taskDto.id})")
            
            val response = apiService.updateTask(task.id, taskDto)
            Log.d(TAG, "Update API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val updatedTask = response.body()?.toDomainModel()
                if (updatedTask != null) {
                    Log.d(TAG, "Successfully updated task: ${updatedTask.description}")
                    Result.success(updatedTask)
                } else {
                    val errorMsg = "Empty response body when updating task"
                    Log.e(TAG, errorMsg)
                    Result.failure(NetworkException(errorMsg))
                }
            } else {
                val errorMsg = "Failed to update task: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                try {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody != null) {
                        Log.e(TAG, "Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error body: ${e.message}")
                }
                Result.failure(NetworkException(errorMsg))
            }
        } catch (e: IOException) {
            val errorMsg = "Network error when updating task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error when updating task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        }
    }
    
    /**
     * Deletes a task from the remote API
     * @param taskId The ID of the task to delete
     * @return Result indicating success or error
     */
    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting task with ID: $taskId")
            
            val response = apiService.deleteTask(taskId)
            Log.d(TAG, "Delete API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully deleted task: $taskId")
                Result.success(Unit)
            } else {
                val errorMsg = "Failed to delete task: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                try {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody != null) {
                        Log.e(TAG, "Error body: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error body: ${e.message}")
                }
                Result.failure(NetworkException(errorMsg))
            }
        } catch (e: IOException) {
            val errorMsg = "Network error when deleting task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error when deleting task: ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(NetworkException(errorMsg, e))
        }
    }
    
    /**
     * Converts TaskDto to domain Task model
     */
    private fun TaskDto.toDomainModel(): Task {
        return Task(
            id = this.id,
            description = this.description,
            isCompleted = this.isCompleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            syncStatus = SyncStatus.SYNCED // Remote tasks are considered synced
        )
    }
    
    /**
     * Converts domain Task model to TaskDto
     */
    private fun Task.toDto(): TaskDto {
        return TaskDto(
            id = this.id,
            description = this.description,
            isCompleted = this.isCompleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}

/**
 * Custom exception for network-related errors
 */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)