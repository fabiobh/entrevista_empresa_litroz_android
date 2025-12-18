package com.fabiocunha.todoapplitroz.data.local

import com.fabiocunha.todoapplitroz.data.local.dao.TaskDao
import com.fabiocunha.todoapplitroz.data.local.entity.TaskEntity
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source that wraps TaskDao operations and handles data mapping
 */
@Singleton
class LocalTaskDataSource @Inject constructor(
    private val taskDao: TaskDao
) {
    
    /**
     * Get all tasks as a Flow of domain models
     */
    fun getAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    /**
     * Insert a task into local storage
     */
    suspend fun insertTask(task: Task): Result<Task> {
        return try {
            taskDao.insertTask(task.toEntity())
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update a task in local storage
     */
    suspend fun updateTask(task: Task): Result<Task> {
        return try {
            taskDao.updateTask(task.toEntity())
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a task from local storage
     */
    suspend fun deleteTask(task: Task): Result<Unit> {
        return try {
            taskDao.deleteTask(task.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a task by ID
     */
    suspend fun getTaskById(id: String): Result<Task?> {
        return try {
            val entity = taskDao.getTaskById(id)
            Result.success(entity?.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all tasks that need synchronization
     */
    suspend fun getPendingSyncTasks(): Result<List<Task>> {
        return try {
            val entities = taskDao.getPendingSyncTasks()
            Result.success(entities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Convert TaskEntity to domain Task model
     */
    private fun TaskEntity.toDomainModel(): Task {
        return Task(
            id = id,
            description = description,
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }
    
    /**
     * Convert domain Task model to TaskEntity
     */
    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            description = description,
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = syncStatus.name
        )
    }
}