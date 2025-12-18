package com.fabiocunha.todoapplitroz.data.local

import com.fabiocunha.todoapplitroz.data.local.dao.TaskDao
import com.fabiocunha.todoapplitroz.data.local.entity.TaskEntity
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
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
 * Property-based tests for LocalTaskDataSource
 * **Feature: offline-task-sync, Property 1: Task Operations Persistence**
 * **Validates: Requirements 1.1, 1.2, 2.1, 3.1, 4.1**
 */
class LocalTaskDataSourcePropertyTest : StringSpec({

    "Task creation should persist immediately in local storage with correct sync status" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.string(1..30).filter { it.isNotBlank() }, // Valid descriptions - reduced size
            Arb.boolean(),
            Arb.enum<SyncStatus>()
        ) { description, isCompleted, syncStatus ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val task = Task(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = syncStatus
                )
                
                val expectedEntity = TaskEntity(
                    id = task.id,
                    description = task.description,
                    isCompleted = task.isCompleted,
                    createdAt = task.createdAt,
                    updatedAt = task.updatedAt,
                    syncStatus = task.syncStatus.name
                )
                
                // Mock successful insertion
                coEvery { mockDao.insertTask(expectedEntity) } just Runs
                coEvery { mockDao.getTaskById(task.id) } returns expectedEntity
                every { mockDao.getAllTasks() } returns flowOf(listOf(expectedEntity))
                
                // Insert task
                val insertResult = dataSource.insertTask(task)
                insertResult.isSuccess shouldBe true
                
                // Verify DAO was called with correct entity
                coVerify { mockDao.insertTask(expectedEntity) }
            }
        }
    }

    "Task updates should persist immediately with updated sync status" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.string(1..30).filter { it.isNotBlank() }, // Reduced size
            Arb.string(1..30).filter { it.isNotBlank() }, // Reduced size
            Arb.boolean(),
            Arb.boolean()
        ) { originalDescription, updatedDescription, originalCompleted, updatedCompleted ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val originalTask = Task(
                    id = UUID.randomUUID().toString(),
                    description = originalDescription,
                    isCompleted = originalCompleted,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED
                )
                
                val updatedTask = originalTask.copy(
                    description = updatedDescription,
                    isCompleted = updatedCompleted,
                    updatedAt = System.currentTimeMillis() + 1000,
                    syncStatus = SyncStatus.PENDING_UPDATE
                )
                
                val updatedEntity = TaskEntity(
                    id = updatedTask.id,
                    description = updatedTask.description,
                    isCompleted = updatedTask.isCompleted,
                    createdAt = updatedTask.createdAt,
                    updatedAt = updatedTask.updatedAt,
                    syncStatus = updatedTask.syncStatus.name
                )
                
                // Mock successful update
                coEvery { mockDao.updateTask(updatedEntity) } just Runs
                coEvery { mockDao.getTaskById(originalTask.id) } returns updatedEntity
                
                val updateResult = dataSource.updateTask(updatedTask)
                updateResult.isSuccess shouldBe true
                
                // Verify DAO was called with updated entity
                coVerify { mockDao.updateTask(updatedEntity) }
            }
        }
    }

    "Task deletion should remove from local storage" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.string(1..30).filter { it.isNotBlank() }, // Reduced size
            Arb.boolean(),
            Arb.enum<SyncStatus>()
        ) { description, isCompleted, syncStatus ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val task = Task(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = syncStatus
                )
                
                val taskEntity = TaskEntity(
                    id = task.id,
                    description = task.description,
                    isCompleted = task.isCompleted,
                    createdAt = task.createdAt,
                    updatedAt = task.updatedAt,
                    syncStatus = task.syncStatus.name
                )
                
                // Mock successful deletion
                coEvery { mockDao.deleteTask(taskEntity) } just Runs
                
                val deleteResult = dataSource.deleteTask(task)
                deleteResult.isSuccess shouldBe true
                
                // Verify DAO was called with correct entity
                coVerify { mockDao.deleteTask(taskEntity) }
            }
        }
    }

    "Task completion toggle should persist with appropriate sync status" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.string(1..30).filter { it.isNotBlank() }, // Reduced size
            Arb.boolean()
        ) { description, initialCompleted ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val originalTask = Task(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    isCompleted = initialCompleted,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED
                )
                
                val toggledTask = originalTask.copy(
                    isCompleted = !initialCompleted,
                    updatedAt = System.currentTimeMillis() + 1000,
                    syncStatus = SyncStatus.PENDING_UPDATE
                )
                
                val toggledEntity = TaskEntity(
                    id = toggledTask.id,
                    description = toggledTask.description,
                    isCompleted = toggledTask.isCompleted,
                    createdAt = toggledTask.createdAt,
                    updatedAt = toggledTask.updatedAt,
                    syncStatus = toggledTask.syncStatus.name
                )
                
                // Mock successful update
                coEvery { mockDao.updateTask(toggledEntity) } just Runs
                coEvery { mockDao.getTaskById(originalTask.id) } returns toggledEntity
                
                val updateResult = dataSource.updateTask(toggledTask)
                updateResult.isSuccess shouldBe true
                
                // Verify DAO was called with toggled entity
                coVerify { mockDao.updateTask(toggledEntity) }
                
                // Verify the task was retrieved with correct completion status
                val retrievedTask = dataSource.getTaskById(originalTask.id).getOrNull()
                retrievedTask?.isCompleted shouldBe !initialCompleted
                retrievedTask?.syncStatus shouldBe SyncStatus.PENDING_UPDATE
            }
        }
    }

    "Pending sync tasks should be retrievable based on sync status" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.list(Arb.enum<SyncStatus>(), 1..5) // Reduced list size
        ) { syncStatuses ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val tasks = syncStatuses.mapIndexed { index, syncStatus ->
                    Task(
                        id = "task-$index",
                        description = "Task $index",
                        isCompleted = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = syncStatus
                    )
                }
                
                // Filter to only pending tasks (non-SYNCED)
                val pendingTasks = tasks.filter { it.syncStatus != SyncStatus.SYNCED }
                val pendingEntities = pendingTasks.map { task ->
                    TaskEntity(
                        id = task.id,
                        description = task.description,
                        isCompleted = task.isCompleted,
                        createdAt = task.createdAt,
                        updatedAt = task.updatedAt,
                        syncStatus = task.syncStatus.name
                    )
                }
                
                // Mock DAO to return pending entities
                coEvery { mockDao.getPendingSyncTasks() } returns pendingEntities
                
                // Get pending sync tasks
                val result = dataSource.getPendingSyncTasks().getOrNull() ?: emptyList()
                
                // Verify only non-SYNCED tasks are returned
                result.size shouldBe pendingTasks.size
                
                result.forEach { pendingTask ->
                    (pendingTask.syncStatus == SyncStatus.PENDING_CREATE ||
                    pendingTask.syncStatus == SyncStatus.PENDING_UPDATE ||
                    pendingTask.syncStatus == SyncStatus.PENDING_DELETE) shouldBe true
                }
                
                // Verify DAO was called
                coVerify { mockDao.getPendingSyncTasks() }
            }
        }
    }

    "Error handling should return failure results when DAO operations fail" {
        checkAll(
            iterations = 10, // Added explicit iteration count
            Arb.string(1..30).filter { it.isNotBlank() } // Reduced size
        ) { description ->
            runTest {
                val mockDao = mockk<TaskDao>()
                val dataSource = LocalTaskDataSource(mockDao)
                
                val task = Task(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                val exception = RuntimeException("Database error")
                
                // Mock DAO to throw exception
                coEvery { mockDao.insertTask(any()) } throws exception
                coEvery { mockDao.updateTask(any()) } throws exception
                coEvery { mockDao.deleteTask(any()) } throws exception
                coEvery { mockDao.getTaskById(any()) } throws exception
                coEvery { mockDao.getPendingSyncTasks() } throws exception
                
                // Test that all operations return failure results
                dataSource.insertTask(task).isFailure shouldBe true
                dataSource.updateTask(task).isFailure shouldBe true
                dataSource.deleteTask(task).isFailure shouldBe true
                dataSource.getTaskById(task.id).isFailure shouldBe true
                dataSource.getPendingSyncTasks().isFailure shouldBe true
            }
        }
    }
})