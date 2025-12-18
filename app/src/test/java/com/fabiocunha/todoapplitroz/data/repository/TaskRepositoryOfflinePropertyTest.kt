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
 * Property-based tests for TaskRepository offline operations
 * **Feature: offline-task-sync, Property 2: Offline Operation Consistency**
 * **Validates: Requirements 5.1, 5.2, 5.3**
 */
class TaskRepositoryOfflinePropertyTest : StringSpec({

    "Any task operation performed while offline should be stored locally with PENDING sync status" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid task descriptions
            Arb.boolean(), // Completion status
            Arb.enum<SyncStatus>().filter { it != SyncStatus.PENDING_DELETE } // Initial sync status (excluding PENDING_DELETE for creation)
        ) { description, isCompleted, initialSyncStatus ->
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
                val currentTime = System.currentTimeMillis()
                
                // Test task creation while offline
                val expectedCreatedTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                coEvery { mockLocalDataSource.insertTask(any()) } returns Result.success(expectedCreatedTask)
                
                // Create task while offline
                val createResult = repository.createTask(description)
                
                createResult.isSuccess shouldBe true
                val createdTask = createResult.getOrThrow()
                
                // Verify task has PENDING_CREATE status when created offline
                createdTask.syncStatus shouldBe SyncStatus.PENDING_CREATE
                createdTask.description shouldBe description.trim()
                
                // Verify local data source was called but remote was not
                coVerify { mockLocalDataSource.insertTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
                
                // Test task update while offline
                val updatedTask = createdTask.copy(
                    description = "Updated: ${description.trim()}",
                    isCompleted = isCompleted,
                    updatedAt = currentTime + 1000,
                    syncStatus = SyncStatus.PENDING_CREATE // Should remain PENDING_CREATE if it was never synced
                )
                
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.success(updatedTask)
                
                val updateResult = repository.updateTask(updatedTask)
                
                updateResult.isSuccess shouldBe true
                val resultUpdatedTask = updateResult.getOrThrow()
                
                // Verify task maintains PENDING status when updated offline
                (resultUpdatedTask.syncStatus == SyncStatus.PENDING_CREATE || 
                 resultUpdatedTask.syncStatus == SyncStatus.PENDING_UPDATE) shouldBe true
                
                // Verify local data source was called but remote was not
                coVerify { mockLocalDataSource.updateTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.updateTask(any()) }
            }
        }
    }

    "Any task operation while offline should automatically sync when connectivity is restored" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid task descriptions
            Arb.enum<SyncStatus>().filter { it != SyncStatus.SYNCED } // Pending sync statuses
        ) { description, pendingSyncStatus ->
            runTest {
                val mockLocalDataSource = mockk<LocalTaskDataSource>()
                val mockRemoteDataSource = mockk<RemoteTaskDataSource>()
                val mockNetworkMonitor = mockk<NetworkMonitor>()
                
                val repository = TaskRepositoryImpl(
                    mockLocalDataSource,
                    mockRemoteDataSource,
                    mockNetworkMonitor
                )
                
                val taskId = UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                
                val pendingTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = pendingSyncStatus
                )
                
                // Mock connectivity restored (online)
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                // Mock pending tasks retrieval
                coEvery { mockLocalDataSource.getPendingSyncTasks() } returns Result.success(listOf(pendingTask))
                
                // Mock successful remote operations based on sync status
                when (pendingSyncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        // For PENDING_CREATE, the remote returns a task with a new ID
                        val remoteTask = pendingTask.copy(id = "remote-id-${pendingTask.id}", syncStatus = SyncStatus.SYNCED)
                        coEvery { mockRemoteDataSource.createTask(pendingTask) } returns Result.success(remoteTask)
                        coEvery { mockLocalDataSource.deleteTask(pendingTask) } returns Result.success(Unit)
                        coEvery { mockLocalDataSource.insertTask(remoteTask) } returns Result.success(remoteTask)
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        val syncedTask = pendingTask.copy(syncStatus = SyncStatus.SYNCED)
                        coEvery { mockRemoteDataSource.updateTask(pendingTask) } returns Result.success(syncedTask)
                        coEvery { mockLocalDataSource.updateTask(syncedTask) } returns Result.success(syncedTask)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        coEvery { mockRemoteDataSource.deleteTask(taskId) } returns Result.success(Unit)
                        coEvery { mockLocalDataSource.deleteTask(pendingTask) } returns Result.success(Unit)
                    }
                    else -> {
                        // SYNCED case - should not happen in this test
                    }
                }
                
                // Mock remote tasks retrieval (empty for simplicity)
                coEvery { mockRemoteDataSource.getAllTasks() } returns Result.success(emptyList())
                
                // Trigger sync when connectivity is restored
                val syncResult = repository.syncWithRemote()
                
                syncResult.isSuccess shouldBe true
                
                // Verify appropriate remote operations were called based on sync status
                when (pendingSyncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        coVerify { mockRemoteDataSource.createTask(pendingTask) }
                        coVerify { mockLocalDataSource.deleteTask(pendingTask) }
                        coVerify { mockLocalDataSource.insertTask(any()) }
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        coVerify { mockRemoteDataSource.updateTask(pendingTask) }
                        coVerify { mockLocalDataSource.updateTask(any()) }
                    }
                    SyncStatus.PENDING_DELETE -> {
                        coVerify { mockRemoteDataSource.deleteTask(taskId) }
                        coVerify { mockLocalDataSource.deleteTask(pendingTask) }
                    }
                    else -> {
                        // SYNCED case - should not happen in this test
                    }
                }
            }
        }
    }

    "Any offline task operation should maintain data integrity during offline operations" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Valid task descriptions
            Arb.boolean(), // Initial completion status
            Arb.boolean()  // Updated completion status
        ) { description, initialCompleted, updatedCompleted ->
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
                val currentTime = System.currentTimeMillis()
                
                // Create initial task
                val initialTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = initialCompleted,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                coEvery { mockLocalDataSource.insertTask(any()) } returns Result.success(initialTask)
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(initialTask)
                
                // Create task
                val createResult = repository.createTask(description)
                createResult.isSuccess shouldBe true
                
                // Update task while offline
                val updatedTask = initialTask.copy(
                    isCompleted = updatedCompleted,
                    updatedAt = currentTime + 1000,
                    syncStatus = SyncStatus.PENDING_CREATE // Should remain PENDING_CREATE since never synced
                )
                
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.success(updatedTask)
                
                val updateResult = repository.updateTask(updatedTask)
                updateResult.isSuccess shouldBe true
                
                // Verify data integrity: all operations should succeed and maintain consistent state
                val finalTask = updateResult.getOrThrow()
                finalTask.id shouldBe taskId
                finalTask.description shouldBe description.trim()
                finalTask.isCompleted shouldBe updatedCompleted
                finalTask.createdAt shouldBe currentTime
                finalTask.syncStatus shouldBe SyncStatus.PENDING_CREATE
                
                // Verify no remote calls were made during offline operations
                coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.updateTask(any()) }
                coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
                
                // Verify all local operations were called
                coVerify { mockLocalDataSource.insertTask(any()) }
                coVerify { mockLocalDataSource.updateTask(any()) }
            }
        }
    }

    "Any task deletion while offline should handle PENDING_CREATE tasks by immediate deletion" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() } // Valid task descriptions
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
                
                // Mock offline state
                every { mockNetworkMonitor.isOnline } returns flowOf(false)
                
                val taskId = UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                
                // Task that was created offline (PENDING_CREATE)
                val pendingCreateTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                // Mock getting the task
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(pendingCreateTask)
                // Mock immediate deletion for PENDING_CREATE tasks
                coEvery { mockLocalDataSource.deleteTask(pendingCreateTask) } returns Result.success(Unit)
                
                // Delete task while offline
                val deleteResult = repository.deleteTask(taskId)
                
                deleteResult.isSuccess shouldBe true
                
                // Verify task was deleted immediately (never synced to remote)
                coVerify { mockLocalDataSource.deleteTask(pendingCreateTask) }
                
                // Verify remote was not called for PENDING_CREATE deletion
                coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
            }
        }
    }

    "Any task deletion while offline should mark SYNCED tasks as PENDING_DELETE" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() } // Valid task descriptions
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
                
                // Mock offline state
                every { mockNetworkMonitor.isOnline } returns flowOf(false)
                
                val taskId = UUID.randomUUID().toString()
                val currentTime = System.currentTimeMillis()
                
                // Task that was previously synced
                val syncedTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock getting the task
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(syncedTask)
                // Mock marking for deletion - use flexible matcher for timestamp
                coEvery { mockLocalDataSource.updateTask(match { 
                    it.id == taskId && it.syncStatus == SyncStatus.PENDING_DELETE 
                }) } answers {
                    val task = firstArg<Task>()
                    Result.success(task)
                }
                
                // Delete task while offline
                val deleteResult = repository.deleteTask(taskId)
                
                deleteResult.isSuccess shouldBe true
                
                // Verify task was marked for deletion, not immediately deleted
                coVerify { mockLocalDataSource.updateTask(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
                
                // Verify task was not immediately deleted from local storage
                coVerify(exactly = 0) { mockLocalDataSource.deleteTask(any()) }
                
                // Verify remote was not called while offline
                coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
            }
        }
    }
})