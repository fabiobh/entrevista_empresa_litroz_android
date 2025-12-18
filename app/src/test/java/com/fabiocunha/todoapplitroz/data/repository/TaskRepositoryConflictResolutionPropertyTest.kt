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
 * Property-based tests for TaskRepository conflict resolution
 * **Feature: offline-task-sync, Property 4: Conflict Resolution Consistency**
 * **Validates: Requirements 6.3**
 */
class TaskRepositoryConflictResolutionPropertyTest : StringSpec({

    "Property 4: For any sync conflict between local and remote data, the system should resolve using last-write-wins strategy based on timestamps" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Task description
            Arb.string(1..50).filter { it.isNotBlank() }, // Different description for conflict
            Arb.boolean(), // Local completion status
            Arb.boolean(), // Remote completion status
            Arb.long(1000L..1000000L), // Base timestamp
            Arb.long(1L..10000L) // Timestamp difference
        ) { localDesc, remoteDesc, localCompleted, remoteCompleted, baseTimestamp, timeDiff ->
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
                
                // Create local task with base timestamp
                val localTask = Task(
                    id = taskId,
                    description = localDesc.trim(),
                    isCompleted = localCompleted,
                    createdAt = baseTimestamp,
                    updatedAt = baseTimestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Create remote task with different timestamp
                // Randomly decide if remote is newer or older
                val remoteIsNewer = timeDiff % 2 == 0L
                val remoteTimestamp = if (remoteIsNewer) {
                    baseTimestamp + timeDiff
                } else {
                    baseTimestamp - timeDiff
                }
                
                val remoteTask = Task(
                    id = taskId,
                    description = remoteDesc.trim(),
                    isCompleted = remoteCompleted,
                    createdAt = baseTimestamp,
                    updatedAt = remoteTimestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock local data source to return the local task
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(localTask)
                
                // Mock remote data source to return the remote task
                coEvery { mockRemoteDataSource.getAllTasks() } returns Result.success(listOf(remoteTask))
                
                // Mock no pending sync tasks
                coEvery { mockLocalDataSource.getPendingSyncTasks() } returns Result.success(emptyList())
                
                // Capture what gets updated in local storage
                val updatedTasks = mutableListOf<Task>()
                coEvery { mockLocalDataSource.updateTask(any()) } answers {
                    val task = firstArg<Task>()
                    updatedTasks.add(task)
                    Result.success(task)
                }
                
                // Trigger sync which should apply conflict resolution
                val syncResult = repository.syncWithRemote()
                
                syncResult.isSuccess shouldBe true
                
                // Verify conflict resolution: last-write-wins based on timestamp
                if (remoteIsNewer && remoteTimestamp > localTask.updatedAt) {
                    // Remote is newer, local should be updated with remote data
                    coVerify { mockLocalDataSource.updateTask(match { 
                        it.id == taskId && 
                        it.description == remoteDesc.trim() && 
                        it.isCompleted == remoteCompleted &&
                        it.updatedAt == remoteTimestamp &&
                        it.syncStatus == SyncStatus.SYNCED
                    }) }
                    
                    // Verify the captured task has remote data
                    val updatedTask = updatedTasks.find { it.id == taskId && it.description == remoteDesc.trim() }
                    if (updatedTask != null) {
                        updatedTask.description shouldBe remoteDesc.trim()
                        updatedTask.isCompleted shouldBe remoteCompleted
                        updatedTask.updatedAt shouldBe remoteTimestamp
                    }
                } else {
                    // Local is newer or equal, local should not be updated
                    // (or remote is older, so local wins)
                    coVerify(exactly = 0) { mockLocalDataSource.updateTask(match { 
                        it.id == taskId && it.description == remoteDesc.trim()
                    }) }
                }
            }
        }
    }



    "Property 4: For any new remote task not present locally, it should be added to local storage" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Task description
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
                
                // Mock online state
                every { mockNetworkMonitor.isOnline } returns flowOf(true)
                
                val taskId = UUID.randomUUID().toString()
                
                // Create remote task that doesn't exist locally
                val remoteTask = Task(
                    id = taskId,
                    description = description.trim(),
                    isCompleted = isCompleted,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock local data source to return failure (task not found)
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.failure(Exception("Task not found"))
                
                // Mock remote data source to return the new task
                coEvery { mockRemoteDataSource.getAllTasks() } returns Result.success(listOf(remoteTask))
                
                // Mock no pending sync tasks
                coEvery { mockLocalDataSource.getPendingSyncTasks() } returns Result.success(emptyList())
                
                // Capture what gets inserted into local storage
                val insertedTasks = mutableListOf<Task>()
                coEvery { mockLocalDataSource.insertTask(any()) } answers {
                    val task = firstArg<Task>()
                    insertedTasks.add(task)
                    Result.success(task)
                }
                
                // Trigger sync
                val syncResult = repository.syncWithRemote()
                
                syncResult.isSuccess shouldBe true
                
                // Verify that the new remote task was added to local storage
                coVerify { mockLocalDataSource.insertTask(match { 
                    it.id == taskId && 
                    it.description == description.trim() && 
                    it.isCompleted == isCompleted &&
                    it.syncStatus == SyncStatus.SYNCED
                }) }
                
                // Verify the captured task has correct data
                val insertedTask = insertedTasks.find { it.id == taskId }
                if (insertedTask != null) {
                    insertedTask.id shouldBe taskId
                    insertedTask.description shouldBe description.trim()
                    insertedTask.isCompleted shouldBe isCompleted
                    insertedTask.syncStatus shouldBe SyncStatus.SYNCED
                }
            }
        }
    }

    "Property 4: For any conflict with equal timestamps, local version should be preserved" {
        checkAll(
            iterations = 100,
            Arb.string(1..50).filter { it.isNotBlank() }, // Local description
            Arb.string(1..50).filter { it.isNotBlank() }, // Remote description
            Arb.long(1000L..1000000L) // Timestamp (same for both)
        ) { localDesc, remoteDesc, timestamp ->
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
                
                // Create local and remote tasks with SAME timestamp
                val localTask = Task(
                    id = taskId,
                    description = localDesc.trim(),
                    isCompleted = false,
                    createdAt = timestamp,
                    updatedAt = timestamp, // Same timestamp
                    syncStatus = SyncStatus.SYNCED
                )
                
                val remoteTask = Task(
                    id = taskId,
                    description = remoteDesc.trim(),
                    isCompleted = true,
                    createdAt = timestamp,
                    updatedAt = timestamp, // Same timestamp
                    syncStatus = SyncStatus.SYNCED
                )
                
                // Mock local data source to return the local task
                coEvery { mockLocalDataSource.getTaskById(taskId) } returns Result.success(localTask)
                
                // Mock remote data source to return the remote task
                coEvery { mockRemoteDataSource.getAllTasks() } returns Result.success(listOf(remoteTask))
                
                // Mock no pending sync tasks
                coEvery { mockLocalDataSource.getPendingSyncTasks() } returns Result.success(emptyList())
                
                // Mock update (should not be called for equal timestamps)
                coEvery { mockLocalDataSource.updateTask(any()) } returns Result.success(localTask)
                
                // Trigger sync
                val syncResult = repository.syncWithRemote()
                
                syncResult.isSuccess shouldBe true
                
                // Verify that local task is NOT updated when timestamps are equal
                // (remote is not strictly greater, so local wins)
                coVerify(exactly = 0) { mockLocalDataSource.updateTask(match { 
                    it.id == taskId && it.description == remoteDesc.trim()
                }) }
            }
        }
    }
})
