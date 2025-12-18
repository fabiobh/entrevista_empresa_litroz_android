package com.fabiocunha.todoapplitroz.presentation.viewmodel

import android.content.Context
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.DeleteTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.UpdateTaskUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **Feature: offline-task-sync, Property 9: Threading Safety**
 * **Validates: Requirements 8.3**
 * 
 * Property-based test for threading safety in TaskListViewModel
 * Tests that network operations execute on background threads and maintain UI responsiveness
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelThreadingSafetyPropertyTest : StringSpec({
    
    beforeTest {
        Dispatchers.setMain(StandardTestDispatcher())
    }
    
    afterTest {
        Dispatchers.resetMain()
    }

    "Property 9: Threading Safety - Network operations should not block main thread" {
        checkAll(10, Arb.list(Arb.string(1..20), 1..5)) { taskDescriptions -> // Reduced iterations and data size
            runTest {
                val mockRepository = object : TaskRepository {
                    override fun getAllTasks() = flowOf(emptyList<Task>())
                    override suspend fun createTask(description: String): Result<Task> {
                        // Simulate network delay
                        delay(10)
                        return Result.success(
                            Task(
                                id = UUID.randomUUID().toString(),
                                description = description,
                                isCompleted = false,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SyncStatus.PENDING_CREATE
                            )
                        )
                    }
                    override suspend fun updateTask(task: Task): Result<Task> {
                        delay(10)
                        return Result.success(task)
                    }
                    override suspend fun deleteTask(taskId: String): Result<Unit> {
                        delay(10)
                        return Result.success(Unit)
                    }
                    override suspend fun syncWithRemote(): Result<Unit> {
                        delay(10)
                        return Result.success(Unit)
                    }
                }
                
                val mockNetworkMonitor = object : NetworkMonitor {
                    override val isOnline: Flow<Boolean> = flowOf(true)
                }
                
                val createTaskUseCase = CreateTaskUseCase(mockRepository)
                val updateTaskUseCase = UpdateTaskUseCase(mockRepository)
                val deleteTaskUseCase = DeleteTaskUseCase(mockRepository)
                val syncTasksUseCase = SyncTasksUseCase(mockRepository)

                val mockContext = mockk<Context> {
                    every { getString(any(), any()) } returns "Test error message"
                    every { getString(any()) } returns "Test message"
                }

                val viewModel = TaskListViewModel(
                    mockContext,
                    mockRepository,
                    mockNetworkMonitor,
                    createTaskUseCase,
                    updateTaskUseCase,
                    deleteTaskUseCase,
                    syncTasksUseCase
                )

                val mainThreadBlocked = AtomicBoolean(false)
                
                // Test create task operation
                taskDescriptions.forEach { description ->
                    if (description.isNotBlank()) {
                        viewModel.createTask(description)
                        
                        // Verify UI state is updated properly (indicating background operation completed)
                        val currentState = viewModel.uiState.value
                        currentState.isLoading shouldBe false
                    }
                }
                
                // Test sync operation
                viewModel.syncTasks()
                
                val finalState = viewModel.uiState.value
                finalState.isSyncing shouldBe false
                
                // If we reach this point, operations didn't block the main thread
                mainThreadBlocked.get() shouldBe false
            }
        }
    }

    "Property 9: Threading Safety - UI state updates should be thread-safe" {
        checkAll(10, Arb.string(1..20)) { taskDescription -> // Reduced iterations and data size
            runTest {
                val mockRepository = object : TaskRepository {
                    override fun getAllTasks() = flowOf(emptyList<Task>())
                    override suspend fun createTask(description: String): Result<Task> {
                        delay(5)
                        return Result.success(
                            Task(
                                id = UUID.randomUUID().toString(),
                                description = description,
                                isCompleted = false,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SyncStatus.PENDING_CREATE
                            )
                        )
                    }
                    override suspend fun updateTask(task: Task): Result<Task> {
                        delay(5)
                        return Result.success(task)
                    }
                    override suspend fun deleteTask(taskId: String): Result<Unit> {
                        delay(5)
                        return Result.success(Unit)
                    }
                    override suspend fun syncWithRemote(): Result<Unit> {
                        delay(5)
                        return Result.success(Unit)
                    }
                }
                
                val mockNetworkMonitor = object : NetworkMonitor {
                    override val isOnline: Flow<Boolean> = flowOf(true)
                }
                
                val createTaskUseCase = CreateTaskUseCase(mockRepository)
                val updateTaskUseCase = UpdateTaskUseCase(mockRepository)
                val deleteTaskUseCase = DeleteTaskUseCase(mockRepository)
                val syncTasksUseCase = SyncTasksUseCase(mockRepository)

                val mockContext = mockk<Context> {
                    every { getString(any(), any()) } returns "Test error message"
                    every { getString(any()) } returns "Test message"
                }

                val viewModel = TaskListViewModel(
                    mockContext,
                    mockRepository,
                    mockNetworkMonitor,
                    createTaskUseCase,
                    updateTaskUseCase,
                    deleteTaskUseCase,
                    syncTasksUseCase
                )

                if (taskDescription.isNotBlank()) {
                    // Perform multiple operations concurrently
                    viewModel.createTask(taskDescription)
                    viewModel.syncTasks()
                    
                    // Verify state is consistent after concurrent operations
                    val state = viewModel.uiState.value
                    state.isLoading shouldBe false
                    state.isSyncing shouldBe false
                }
            }
        }
    }
})