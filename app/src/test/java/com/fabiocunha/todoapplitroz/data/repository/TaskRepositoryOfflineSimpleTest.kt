package com.fabiocunha.todoapplitroz.data.repository

import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID

/**
 * Simple unit tests for TaskRepository offline operations
 * **Feature: offline-task-sync, Property 2: Offline Operation Consistency**
 * **Validates: Requirements 5.1, 5.2, 5.3**
 */
class TaskRepositoryOfflineSimpleTest : StringSpec({

    "Task created while offline should have PENDING_CREATE sync status" {
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
            
            val description = "Test task"
            val mockTask = Task(
                id = UUID.randomUUID().toString(),
                description = description,
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_CREATE
            )
            coEvery { mockLocalDataSource.insertTask(any()) } returns Result.success(mockTask)
            
            // Create task while offline
            val result = repository.createTask(description)
            
            result.isSuccess shouldBe true
            val createdTask = result.getOrThrow()
            
            // Verify task has PENDING_CREATE status when created offline
            createdTask.syncStatus shouldBe SyncStatus.PENDING_CREATE
            createdTask.description shouldBe description.trim()
            
            // Verify local data source was called but remote was not
            coVerify { mockLocalDataSource.insertTask(any()) }
            coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
        }
    }

    "Empty task description should be rejected in offline mode" {
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
            
            // Attempt to create task with empty description
            val result = repository.createTask("")
            
            // Verify operation fails with validation error
            result.isFailure shouldBe true
            result.exceptionOrNull() shouldBe instanceOf<IllegalArgumentException>()
            
            // Verify no data source calls were made
            coVerify(exactly = 0) { mockLocalDataSource.insertTask(any()) }
            coVerify(exactly = 0) { mockRemoteDataSource.createTask(any()) }
        }
    }

    "Task deletion while offline should handle PENDING_CREATE tasks correctly" {
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
            
            val task = Task(
                id = UUID.randomUUID().toString(),
                description = "Test task",
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_CREATE
            )
            
            // Mock getting the task
            coEvery { mockLocalDataSource.getTaskById(task.id) } returns Result.success(task)
            // Mock immediate deletion for PENDING_CREATE tasks
            coEvery { mockLocalDataSource.deleteTask(task) } returns Result.success(Unit)
            
            // Delete task while offline
            val result = repository.deleteTask(task.id)
            
            result.isSuccess shouldBe true
            
            // Verify task was deleted immediately (never synced)
            coVerify { mockLocalDataSource.deleteTask(task) }
            
            // Verify remote was not called
            coVerify(exactly = 0) { mockRemoteDataSource.deleteTask(any()) }
        }
    }
})