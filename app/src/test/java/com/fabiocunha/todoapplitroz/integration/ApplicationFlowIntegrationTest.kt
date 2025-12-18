package com.fabiocunha.todoapplitroz.integration

import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.local.dao.TaskDao
import com.fabiocunha.todoapplitroz.data.local.entity.TaskEntity
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.data.remote.dto.TaskDto
import com.fabiocunha.todoapplitroz.data.repository.TaskRepositoryImpl
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response

/**
 * Integration test for complete application flow
 * Tests the connection between all layers and validates offline-to-online synchronization
 */
class ApplicationFlowIntegrationTest : FunSpec({
    
    test("complete offline-to-online task creation flow") {
        // Mock dependencies
        val mockTaskDao = mockk<TaskDao>(relaxed = true)
        val mockApiService = mockk<TaskApiService>()
        val mockNetworkMonitor = mockk<NetworkMonitor>()
        
        val networkStateFlow = MutableStateFlow(false) // Start offline
        coEvery { mockNetworkMonitor.isOnline } returns networkStateFlow
        
        // Set up data sources
        val localDataSource = LocalTaskDataSource(mockTaskDao)
        val remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        // Set up repository and use cases
        val repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        val createTaskUseCase = CreateTaskUseCase(repository)
        val syncTasksUseCase = SyncTasksUseCase(repository)
        
        // Mock local storage behavior
        val createdTasks = mutableListOf<TaskEntity>()
        coEvery { mockTaskDao.insertTask(any()) } answers {
            createdTasks.add(firstArg())
        }
        coEvery { mockTaskDao.getAllTasks() } returns flowOf(createdTasks)
        coEvery { mockTaskDao.getPendingSyncTasks() } answers {
            createdTasks.filter { it.syncStatus != "SYNCED" }
        }
        
        // Step 1: Create task offline
        val result = createTaskUseCase("Test task offline")
        result.isSuccess shouldBe true
        
        val createdTask = result.getOrThrow()
        createdTask.description shouldBe "Test task offline"
        createdTask.syncStatus shouldBe SyncStatus.PENDING_CREATE
        
        // Verify task was stored locally
        coVerify { mockTaskDao.insertTask(any()) }
        createdTasks shouldHaveSize 1
        
        // Step 2: Go online and sync
        networkStateFlow.value = true
        
        // Mock successful API response with a different ID (simulating real API behavior)
        val syncedTaskDto = TaskDto(
            id = "api-generated-id",
            description = createdTask.description,
            isCompleted = createdTask.isCompleted,
            createdAt = createdTask.createdAt,
            updatedAt = createdTask.updatedAt
        )
        coEvery { mockApiService.createTask(any()) } returns Response.success(syncedTaskDto)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(syncedTaskDto))
        
        // Mock delete behavior for ID replacement
        coEvery { mockTaskDao.deleteTask(any()) } answers {
            val taskToDelete = firstArg<TaskEntity>()
            createdTasks.removeIf { it.id == taskToDelete.id }
        }
        
        // Mock insert behavior for new ID
        coEvery { mockTaskDao.insertTask(any()) } answers {
            val newTask = firstArg<TaskEntity>()
            createdTasks.add(newTask)
        }
        
        val syncResult = syncTasksUseCase()
        syncResult.isSuccess shouldBe true
        
        // Verify API was called and task ID was replaced
        coVerify(exactly = 1) { mockApiService.createTask(any()) }
        coVerify(exactly = 1) { mockTaskDao.deleteTask(any()) }
        coVerify(atLeast = 1) { mockTaskDao.insertTask(any()) }
    }
    
    test("error propagation through all layers") {
        // Mock dependencies
        val mockTaskDao = mockk<TaskDao>()
        val mockApiService = mockk<TaskApiService>()
        val mockNetworkMonitor = mockk<NetworkMonitor>()
        
        val networkStateFlow = MutableStateFlow(true)
        coEvery { mockNetworkMonitor.isOnline } returns networkStateFlow
        
        // Set up data sources
        val localDataSource = LocalTaskDataSource(mockTaskDao)
        val remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        // Set up repository and use cases
        val repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        val createTaskUseCase = CreateTaskUseCase(repository)
        
        // Mock database error
        coEvery { mockTaskDao.insertTask(any()) } throws RuntimeException("Database error")
        
        // Test error propagation
        val result = createTaskUseCase("Test task")
        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldBe "Database error"
    }
    
    test("use case layer properly delegates to repository") {
        // Mock dependencies
        val mockRepository = mockk<TaskRepositoryImpl>()
        
        // Set up use cases
        val createTaskUseCase = CreateTaskUseCase(mockRepository)
        val syncTasksUseCase = SyncTasksUseCase(mockRepository)
        
        // Mock repository responses
        val testTask = Task(
            id = "test-id",
            description = "Test task",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )
        
        coEvery { mockRepository.createTask("Test task") } returns Result.success(testTask)
        coEvery { mockRepository.syncWithRemote() } returns Result.success(Unit)
        
        // Test use case delegation
        val createResult = createTaskUseCase("Test task")
        createResult.isSuccess shouldBe true
        createResult.getOrThrow() shouldBe testTask
        
        val syncResult = syncTasksUseCase()
        syncResult.isSuccess shouldBe true
        
        // Verify repository methods were called
        coVerify { mockRepository.createTask("Test task") }
        coVerify { mockRepository.syncWithRemote() }
    }
    
    test("data layer coordination between local and remote sources") {
        // Mock dependencies
        val mockTaskDao = mockk<TaskDao>(relaxed = true)
        val mockApiService = mockk<TaskApiService>()
        val mockNetworkMonitor = mockk<NetworkMonitor>()
        
        val networkStateFlow = MutableStateFlow(true) // Online
        coEvery { mockNetworkMonitor.isOnline } returns networkStateFlow
        
        // Set up data sources
        val localDataSource = LocalTaskDataSource(mockTaskDao)
        val remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        // Set up repository
        val repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        
        // Mock successful responses
        val testTaskDto = TaskDto(
            id = "api-generated-id",
            description = "Test task",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        coEvery { mockApiService.createTask(any()) } returns Response.success(testTaskDto)
        coEvery { mockTaskDao.getAllTasks() } returns flowOf(emptyList())
        coEvery { mockTaskDao.insertTask(any()) } returns Unit
        coEvery { mockTaskDao.deleteTask(any()) } returns Unit
        coEvery { mockTaskDao.updateTask(any()) } returns Unit
        
        // Test repository coordination
        val result = repository.createTask("Test task")
        result.isSuccess shouldBe true
        
        // Verify both local and remote operations were attempted
        // First insert (initial local storage)
        coVerify(atLeast = 1) { mockTaskDao.insertTask(any()) }
        coVerify { mockApiService.createTask(any()) }
        // Then delete old task and insert new one with API ID
        coVerify { mockTaskDao.deleteTask(any()) }
    }
})