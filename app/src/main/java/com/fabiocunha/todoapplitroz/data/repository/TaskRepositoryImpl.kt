package com.fabiocunha.todoapplitroz.data.repository

import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TaskRepository that coordinates between local and remote data sources
 * Implements offline-first strategy with proper sync status management
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val localDataSource: LocalTaskDataSource,
    private val remoteDataSource: RemoteTaskDataSource,
    private val networkMonitor: NetworkMonitor
) : TaskRepository {
    
    override fun getAllTasks(): Flow<List<Task>> {
        return localDataSource.getAllTasks()
    }
    
    override suspend fun createTask(description: String): Result<Task> {
        // Validate input
        if (description.isBlank()) {
            return Result.failure(IllegalArgumentException("Task description cannot be empty or whitespace"))
        }
        
        val currentTime = System.currentTimeMillis()
        val isOnline = networkMonitor.isOnline.first()
        
        val task = Task(
            id = generateObjectId(),
            description = description.trim(),
            isCompleted = false,
            createdAt = currentTime,
            updatedAt = currentTime,
            syncStatus = if (isOnline) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_CREATE
        )
        
        // Always store locally first (offline-first strategy)
        val localResult = localDataSource.insertTask(task)
        if (localResult.isFailure) {
            return localResult
        }
        
        // If online, try to sync immediately
        if (isOnline) {
            val syncResult = syncTaskWithRemote(task)
            if (syncResult.isSuccess) {
                val remoteTask = syncResult.getOrNull()
                if (remoteTask != null) {
                    // Delete the local task with the old ID
                    localDataSource.deleteTask(task)
                    // Insert the task with the new ID from the API
                    val syncedTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                    localDataSource.insertTask(syncedTask)
                    return Result.success(syncedTask)
                }
            }
            // If sync fails, keep the task with PENDING_CREATE status
        }
        
        return Result.success(task)
    }
    
    override suspend fun updateTask(task: Task): Result<Task> {
        // Validate input
        if (task.description.isBlank()) {
            return Result.failure(IllegalArgumentException("Task description cannot be empty or whitespace"))
        }
        
        val isOnline = networkMonitor.isOnline.first()
        val updatedTask = task.copy(
            description = task.description.trim(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (task.syncStatus == SyncStatus.PENDING_CREATE) 
                SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE
        )
        
        // Always update locally first (offline-first strategy)
        val localResult = localDataSource.updateTask(updatedTask)
        if (localResult.isFailure) {
            return localResult
        }
        
        // If online, try to sync immediately based on sync status
        if (isOnline) {
            val syncResult = syncTaskWithRemote(updatedTask)
            if (syncResult.isSuccess) {
                val remoteTask = syncResult.getOrNull()
                if (remoteTask != null) {
                    if (updatedTask.syncStatus == SyncStatus.PENDING_CREATE) {
                        // For PENDING_CREATE, we need to replace the local task with the remote ID
                        localDataSource.deleteTask(updatedTask)
                        val syncedTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                        localDataSource.insertTask(syncedTask)
                        return Result.success(syncedTask)
                    } else {
                        // For PENDING_UPDATE, just update the sync status
                        val syncedTask = updatedTask.copy(syncStatus = SyncStatus.SYNCED)
                        localDataSource.updateTask(syncedTask)
                        return Result.success(syncedTask)
                    }
                }
            }
            // If sync fails, keep the task with current status (PENDING_CREATE or PENDING_UPDATE)
        }
        
        return Result.success(updatedTask)
    }
    
    override suspend fun deleteTask(taskId: String): Result<Unit> {
        // Get the task first to check its current state
        val taskResult = localDataSource.getTaskById(taskId)
        if (taskResult.isFailure) {
            return Result.failure(taskResult.exceptionOrNull() ?: Exception("Task not found"))
        }
        
        val task = taskResult.getOrNull()
        if (task == null) {
            return Result.failure(Exception("Task not found"))
        }
        
        val isOnline = networkMonitor.isOnline.first()
        
        // If task was never synced (PENDING_CREATE), just delete it locally
        if (task.syncStatus == SyncStatus.PENDING_CREATE) {
            return localDataSource.deleteTask(task)
        }
        
        // Mark for deletion instead of immediate deletion
        val deletedTask = task.copy(
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = System.currentTimeMillis()
        )
        
        val localResult = localDataSource.updateTask(deletedTask)
        if (localResult.isFailure) {
            return Result.failure(localResult.exceptionOrNull() ?: Exception("Failed to mark task for deletion"))
        }
        
        // If online, try to sync deletion immediately
        if (isOnline) {
            val syncResult = remoteDataSource.deleteTask(taskId)
            if (syncResult.isSuccess) {
                // Successfully deleted from remote, now delete locally
                return localDataSource.deleteTask(deletedTask)
            }
            // If sync fails, keep the task with PENDING_DELETE status
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun syncWithRemote(): Result<Unit> {
        val isOnline = networkMonitor.isOnline.first()
        if (!isOnline) {
            return Result.failure(Exception("Cannot sync while offline"))
        }
        
        return try {
            // Get all pending sync tasks
            val pendingTasksResult = localDataSource.getPendingSyncTasks()
            if (pendingTasksResult.isFailure) {
                return Result.failure(pendingTasksResult.exceptionOrNull() ?: Exception("Failed to get pending tasks"))
            }
            
            val pendingTasks = pendingTasksResult.getOrThrow()
            
            // Process each pending task with retry logic
            for (task in pendingTasks) {
                when (task.syncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        val result = syncTaskWithRetry(task)
                        if (result.isSuccess) {
                            val remoteTask = result.getOrNull()
                            if (remoteTask != null) {
                                // Delete the local task with the old ID and insert with new ID
                                localDataSource.deleteTask(task)
                                val syncedTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                                localDataSource.insertTask(syncedTask)
                            }
                        }
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        val result = syncTaskWithRetry(task)
                        if (result.isSuccess) {
                            val syncedTask = task.copy(syncStatus = SyncStatus.SYNCED)
                            localDataSource.updateTask(syncedTask)
                        }
                    }
                    SyncStatus.PENDING_DELETE -> {
                        val result = deleteTaskWithRetry(task.id)
                        if (result.isSuccess) {
                            localDataSource.deleteTask(task)
                        }
                    }
                    SyncStatus.SYNCED -> {
                        // Already synced, skip
                    }
                }
            }
            
            // Fetch remote changes and apply conflict resolution
            syncRemoteChanges()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Syncs a single task with the remote API
     */
    private suspend fun syncTaskWithRemote(task: Task): Result<Task> {
        return when (task.syncStatus) {
            SyncStatus.PENDING_CREATE -> remoteDataSource.createTask(task)
            SyncStatus.PENDING_UPDATE -> remoteDataSource.updateTask(task)
            else -> Result.failure(Exception("Invalid sync status for task: ${task.syncStatus}"))
        }
    }
    
    /**
     * Syncs a task with retry logic and exponential backoff
     */
    private suspend fun syncTaskWithRetry(task: Task, maxRetries: Int = 3): Result<Task> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            val result = syncTaskWithRemote(task)
            if (result.isSuccess) {
                return result
            }
            
            lastException = result.exceptionOrNull() as? Exception
            
            // Don't retry on the last attempt
            if (attempt < maxRetries - 1) {
                // Exponential backoff: 1s, 2s, 4s
                val delayMs = 1000L * (1 shl attempt)
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("Sync failed after $maxRetries attempts"))
    }
    
    /**
     * Deletes a task with retry logic and exponential backoff
     */
    private suspend fun deleteTaskWithRetry(taskId: String, maxRetries: Int = 3): Result<Unit> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            val result = remoteDataSource.deleteTask(taskId)
            if (result.isSuccess) {
                return result
            }
            
            lastException = result.exceptionOrNull() as? Exception
            
            // Don't retry on the last attempt
            if (attempt < maxRetries - 1) {
                // Exponential backoff: 1s, 2s, 4s
                val delayMs = 1000L * (1 shl attempt)
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("Delete failed after $maxRetries attempts"))
    }
    
    /**
     * Fetches remote changes and applies conflict resolution using last-write-wins strategy
     */
    private suspend fun syncRemoteChanges(): Result<Unit> {
        return try {
            val remoteTasksResult = remoteDataSource.getAllTasks()
            if (remoteTasksResult.isFailure) {
                return remoteTasksResult.map { }
            }
            
            val remoteTasks = remoteTasksResult.getOrThrow()
            
            for (remoteTask in remoteTasks) {
                val localTaskResult = localDataSource.getTaskById(remoteTask.id)
                
                if (localTaskResult.isSuccess) {
                    val localTask = localTaskResult.getOrNull()
                    
                    if (localTask != null) {
                        // Conflict resolution: last-write-wins based on updatedAt timestamp
                        if (remoteTask.updatedAt > localTask.updatedAt && localTask.syncStatus == SyncStatus.SYNCED) {
                            // Remote is newer and local is synced, update local
                            val updatedTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                            localDataSource.updateTask(updatedTask)
                        }
                        // If local has pending changes, keep local version (will be synced later)
                    } else {
                        // Task doesn't exist locally, add it
                        val newTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                        localDataSource.insertTask(newTask)
                    }
                } else {
                    // Task doesn't exist locally, add it
                    val newTask = remoteTask.copy(syncStatus = SyncStatus.SYNCED)
                    localDataSource.insertTask(newTask)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generates a MongoDB-compatible ObjectId string
     * ObjectId format: 24-character hex string (12 bytes)
     * - 4 bytes timestamp
     * - 5 bytes random value unique to machine and process
     * - 3 bytes incrementing counter
     */
    private fun generateObjectId(): String {
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        val random = SecureRandom()
        
        // Create 12 bytes for ObjectId
        val bytes = ByteArray(12)
        
        // First 4 bytes: timestamp
        bytes[0] = (timestamp shr 24).toByte()
        bytes[1] = (timestamp shr 16).toByte()
        bytes[2] = (timestamp shr 8).toByte()
        bytes[3] = timestamp.toByte()
        
        // Next 5 bytes: random value
        val randomBytes = ByteArray(5)
        random.nextBytes(randomBytes)
        System.arraycopy(randomBytes, 0, bytes, 4, 5)
        
        // Last 3 bytes: counter (using random for simplicity)
        val counterBytes = ByteArray(3)
        random.nextBytes(counterBytes)
        System.arraycopy(counterBytes, 0, bytes, 9, 3)
        
        // Convert to hex string
        return bytes.joinToString("") { "%02x".format(it) }
    }
}