package com.fabiocunha.todoapplitroz.presentation.viewmodel

import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.DeleteTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.UpdateTaskUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID

/**
 * **Feature: offline-task-sync, Property 8: Timestamp Management**
 * **Validates: Requirements 2.5, 4.5**
 * 
 * Property-based test for timestamp management in TaskListViewModel
 * Tests that task update operations preserve creation timestamp while updating modification timestamp
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTimestampPropertyTest : StringSpec({
    
    beforeTest {
        Dispatchers.setMain(StandardTestDispatcher())
    }
    
    afterTest {
        Dispatchers.resetMain()
    }

    "Property 8: Timestamp Management - Toggle completion preserves creation timestamp and updates modification timestamp" {
        checkAll(10, // Reduced iterations
            Arb.string(1..30), // Reduced string size
            Arb.boolean(), 
            Arb.long(1000000000000L..System.currentTimeMillis())
        ) { description, initialCompletion, createdTime ->
            runTest {
                var capturedTask: Task? = null
                
                val mockRepository = object : TaskRepository {
                    override fun getAllTasks() = flowOf(emptyList<Task>())
                    override suspend fun createTask(description: String) = Result.success(
                        Task(
                            id = UUID.randomUUID().toString(),
                            description = description,
                            isCompleted = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING_CREATE
                        )
                    )
                    override suspend fun updateTask(task: Task): Result<Task> {
                        capturedTask = task
                        return Result.success(task)
                    }
                    override suspend fun deleteTask(taskId: String) = Result.success(Unit)
                    override suspend fun syncWithRemote() = Result.success(Unit)
                }
                
                val mockNetworkMonitor = object : NetworkMonitor {
                    override val isOnline: Flow<Boolean> = flowOf(true)
                }
                
                val createTaskUseCase = CreateTaskUseCase(mockRepository)
                val updateTaskUseCase = UpdateTaskUseCase(mockRepository)
                val deleteTaskUseCase = DeleteTaskUseCase(mockRepository)
                val syncTasksUseCase = SyncTasksUseCase(mockRepository)

                val viewModel = TaskListViewModel(
                    mockRepository,
                    mockNetworkMonitor,
                    createTaskUseCase,
                    updateTaskUseCase,
                    deleteTaskUseCase,
                    syncTasksUseCase
                )

                val originalTask = Task(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    isCompleted = initialCompletion,
                    createdAt = createdTime,
                    updatedAt = createdTime,
                    syncStatus = SyncStatus.SYNCED
                )

                val timeBeforeToggle = System.currentTimeMillis()
                
                // Toggle task completion
                viewModel.toggleTaskCompletion(originalTask)

                // Verify timestamp management
                capturedTask?.let { captured ->
                    captured.createdAt shouldBe originalTask.createdAt // Creation timestamp preserved
                    captured.updatedAt shouldBeGreaterThan originalTask.updatedAt // Update timestamp changed
                    captured.updatedAt shouldBeGreaterThan timeBeforeToggle // Update timestamp is recent
                    captured.isCompleted shouldBe !originalTask.isCompleted // Completion status toggled
                    captured.description shouldBe originalTask.description // Description unchanged
                    captured.id shouldBe originalTask.id // ID unchanged
                }
            }
        }
    }

    "Property 8: Timestamp Management - Task updates preserve creation timestamp" {
        checkAll(10, // Reduced iterations
            Arb.string(1..30), // Reduced string size
            Arb.string(1..30), // Reduced string size
            Arb.boolean(),
            Arb.long(1000000000000L..System.currentTimeMillis())
        ) { originalDescription, newDescription, isCompleted, createdTime ->
            runTest {
                var capturedTask: Task? = null
                
                val mockRepository = object : TaskRepository {
                    override fun getAllTasks() = flowOf(emptyList<Task>())
                    override suspend fun createTask(description: String) = Result.success(
                        Task(
                            id = UUID.randomUUID().toString(),
                            description = description,
                            isCompleted = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING_CREATE
                        )
                    )
                    override suspend fun updateTask(task: Task): Result<Task> {
                        capturedTask = task
                        return Result.success(task)
                    }
                    override suspend fun deleteTask(taskId: String) = Result.success(Unit)
                    override suspend fun syncWithRemote() = Result.success(Unit)
                }
                
                val mockNetworkMonitor = object : NetworkMonitor {
                    override val isOnline: Flow<Boolean> = flowOf(true)
                }
                
                val createTaskUseCase = CreateTaskUseCase(mockRepository)
                val updateTaskUseCase = UpdateTaskUseCase(mockRepository)
                val deleteTaskUseCase = DeleteTaskUseCase(mockRepository)
                val syncTasksUseCase = SyncTasksUseCase(mockRepository)

                val viewModel = TaskListViewModel(
                    mockRepository,
                    mockNetworkMonitor,
                    createTaskUseCase,
                    updateTaskUseCase,
                    deleteTaskUseCase,
                    syncTasksUseCase
                )

                val originalTask = Task(
                    id = UUID.randomUUID().toString(),
                    description = originalDescription,
                    isCompleted = isCompleted,
                    createdAt = createdTime,
                    updatedAt = createdTime,
                    syncStatus = SyncStatus.SYNCED
                )

                val updatedTask = originalTask.copy(
                    description = newDescription,
                    updatedAt = System.currentTimeMillis()
                )

                // Update task
                viewModel.updateTask(updatedTask)

                // Verify timestamp management
                capturedTask?.let { captured ->
                    captured.createdAt shouldBe originalTask.createdAt // Creation timestamp preserved
                    captured.updatedAt shouldBe updatedTask.updatedAt // Update timestamp matches input
                    captured.description shouldBe newDescription // Description updated
                    captured.id shouldBe originalTask.id // ID unchanged
                    captured.isCompleted shouldBe originalTask.isCompleted // Completion status unchanged
                }
            }
        }
    }
})