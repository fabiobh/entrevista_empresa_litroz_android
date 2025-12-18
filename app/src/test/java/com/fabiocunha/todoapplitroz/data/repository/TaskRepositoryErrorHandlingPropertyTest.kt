package com.fabiocunha.todoapplitroz.data.repository

import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID

/**
 * Property-based tests for TaskRepository error handling and recovery
 * **Feature: offline-task-sync, Property 5: Error Handling and Recovery**
 * **Validates: Requirements 1.4, 2.4, 3.4, 4.4**
 */
class TaskRepositoryErrorHandlingPropertyTest : StringSpec({

    "Property 5: For any task creation with invalid input, the system should reject and maintain current state" {
        checkAll(
            iterations = 100,
            Arb.string(0..50).filter { it.isBlank() } // Empty or whitespace-only strings
        ) { invalidDescription ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                // Attempt to create task with invalid description
                val result = repository.createTask(invalidDescription)
                
                // Verify operation failed with validation error
                result.isFailure shouldBe true
                (result.exceptionOrNull() is IllegalArgumentException) shouldBe true
                
                // Verify no local or remote operations were performed (state maintained)
                coVerify(exactly = 0) { mockLocalDataSource.insertTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
            }
        }
    }

    "Property 5: For any task update with invalid input, the system should reject and maintain current state" {
        checkAll(
            iterations = 100,
            Arb.string(0..50).filter { it.isBlank() }, // Invalid description
            Arb.long(1000L..1000000L) // Timestamp
        ) { invalidDescription, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val invalidTask = Task(
                    id = taskId,
                    description = invalidDescription,
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Attempt to update task with invalid description
                val result = repository.updateTask(invalidTask)
                
                // Verify operation failed with validation error
                result.isFailure shouldBe true
                (result.exceptionOrNull() is IllegalArgumentException) shouldBe true
                
                // Verify no local or remote operations were performed (state maintained)
                coVerify(exactly = 0) { mockLocalDataSource.updateTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.updateTask(any()) }
            }
        }
    }

    "Property 5: For any failed local storage operation during creation, the system should return failure" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() } // Valid description
        ) { description ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                // Mock local storage failure
                val storageException = Exception("Database error: disk full")
                coEvery { mockLocalDataSource.insertTask(any()) } returns Result.failure(storageException)
                
                // Attempt to create task
                val result = repository.createTask(description)
                
                // Verify operation failed with storage error
                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe storageException
                
                // Verify local operation was attempted but remote was not
                coVerify { mockLocalDataSource.insertTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
            }
        }
    }

    "Property 5: For any failed local storage operation during update, the system should return failure" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid description
            Arb.long(1000L..1000000L) // Timestamp
        ) { description, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock local storage failure
                val storageException = Exception("Database error: constraint violation")
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.failure(storageException)
                
                // Attempt to update task
                val result = repository.updateTask(task)
                
                // Verify operation failed with storage error
                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe storageException
                
                // Verify local operation was attempted but remote was not
                coVerify { mockLocalDataSource.updateTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.updateTask(any()) }
            }
        }
    }

    "Property 5: For any failed deletion when task not found, the system should return failure" {
        checkAll(
            iterations = 100,
            Arb.string(10..36) // Task ID
        ) { taskId ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                // Mock task not found
                val notFoundException = Exception("Task not found")
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.failure(notFoundException)
                
                // Attempt to delete non-existent task
                val result = repository.deleteTask(taskId)
                
                // Verify operation failed
                result.isFailure shouldBe true
                
                // Verify no deletion operations were performed
                coVerify(exactly = 0) { mockLocalDataSource.deleteTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
            }
        }
    }

    "Property 5: For any failed remote sync during creation, the system should keep task with PENDING status" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() } // Valid description
        ) { description ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                
                val task = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                // Mock successful local storage
                coEvery { mockLocalDataSource.insertTask(any()) } returns Result.success(task)
                
                // Mock failed remote sync
                val networkException = Exception("Network error: timeout")
                coEvery { mockRemoteDataSource.createTask(any()) } returns Result.failure(networkException)
                
                // Attempt to create task
                val result = repository.createTask(description)
                
                // Verify operation succeeded locally despite remote failure
                result.isSuccess shouldBe true
                val createdTask = result.getOrThrow()
                
                // Verify task maintains PENDING_CREATE status (not SYNCED)
                createdTask.syncStatus shouldBe SyncStatus.PENDING_CREATE
                
                // Verify both local and remote operations were attempted
                coVerify { mockLocalDataSource.insertTask(any()) }
                coVerify { mockRemoteDataSource.createTask(any()) }
            }
        }
    }

    "Property 5: For any failed remote sync during update, the system should keep task with PENDING status" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid description
            Arb.long(1000L..1000000L) // Timestamp
        ) { description, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                val updatedTask = task.copy(
                    description = "Updated: ${description.trim()}",
                    updatedAt = timestamp + 1000,
                    syncStatus = SyncStatus.PENDING_UPDATE
                )
                
                // Mock successful local storage
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.success(updatedTask)
                
                // Mock failed remote sync
                val networkException = Exception("Network error: server unavailable")
                coEvery { mockRemoteDataSource.updateTask(any()) } returns Result.failure(networkException)
                
                // Attempt to update task
                val result = repository.updateTask(task)
                
                // Verify operation succeeded locally despite remote failure
                result.isSuccess shouldBe true
                val resultTask = result.getOrThrow()
                
                // Verify task maintains PENDING_UPDATE status (not SYNCED)
                resultTask.syncStatus shouldBe SyncStatus.PENDING_UPDATE
                
                // Verify both local and remote operations were attempted
                coVerify { mockLocalDataSource.updateTask(any()) }
                coVerify { mockRemoteDataSource.updateTask(any()) }
            }
        }
    }

    "Property 5: For any failed deletion marking, the system should return failure and maintain state" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid description
            Arb.long(1000L..1000000L) // Timestamp
        ) { description, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock successful task retrieval
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(task)
                
                // Mock failed update (marking for deletion)
                val storageException = Exception("Database error: failed to update")
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.failure(storageException)
                
                // Attempt to delete task
                val result = repository.deleteTask(taskId)
                
                // Verify operation failed
                result.isFailure shouldBe true
                
                // Verify task was not deleted from local storage
                coVerify(exactly = 0) { mockLocalDataSource.deleteTask(any()) }
                
                // Verify remote deletion was not attempted
                coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
            }
        }
    }

    "Property 5: For any failed remote deletion, the system should keep task with PENDING_DELETE status" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid description
            Arb.long(1000L..1000000L) // Timestamp
        ) { description, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock successful task retrieval
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(task)
                
                // Mock successful marking for deletion
                val deletedTask = task.copy(syncStatus = SyncStatus.PENDING_DELETE)
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.success(deletedTask)
                
                // Mock failed remote deletion
                val networkException = Exception("Network error: connection refused")
                coEvery { mockRemoteDataSource.deleteTask(taskId) } returns Result.failure(networkException)
                
                // Attempt to delete task
                val result = repository.deleteTask(taskId)
                
                // Verify operation succeeded (marked for deletion)
                result.isSuccess shouldBe true
                
                // Verify task was marked for deletion but not deleted locally
                coVerify { mockLocalDataSource.updateTask(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
                coVerify(exactly = 0) { mockLocalDataSource.deleteTask(any()) }
                
                // Verify remote deletion was attempted
                coVerify { mockRemoteDataSource.deleteTask(taskId) }
            }
        }
    }

    "Property 5: For any data integrity check, failed operations should not corrupt existing data" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid description
            Arb.boolean(), // Completion status
            Arb.long(1000L..1000000L) // Timestamp
        ) { description, isCompleted, timestamp ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                // Mock offline state
                every { mockNetworkMonitor.isOnline } returns flowOf(false)
                
                val taskId = UUID.randomUUID().toString()
                val originalTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = isCompleted,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock successful retrieval of original task
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(originalTask)
                
                // Mock failed update operation
                val storageException = Exception("Database error: corruption detected")
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.failure(storageException)
                
                // Attempt to update task
                val result = repository.updateTask(originalTask.copy(description = "Modified"))
                
                // Verify operation failed
                result.isFailure shouldBe true
                
                // Verify original task data remains unchanged (can still be retrieved)
                val retrievedTask = mockLocalDataSource.getTaskById(taskId)
                retrievedTask.isSuccess shouldBe true
                retrievedTask.getOrNull()?.description shouldBe description.trim()
                retrievedTask.getOrNull()?.isCompleted shouldBe isCompleted
                retrievedTask.getOrNull()?.syncStatus shouldBe SyncStatus.SYNCED
            }
        }
    }
})
