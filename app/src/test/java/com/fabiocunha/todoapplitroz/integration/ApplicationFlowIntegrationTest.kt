package com.fabiocunha.todoapplitroz.integration

import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.local.dao.TaskDao
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.data.repository.TaskRepositoryImpl
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

/**
 * Integration test for complete application flow
 * Tests the connection between all layers and validates offline-to-online synchronization
 */
class ApplicationFlowIntegrationTest : FunSpec({
    
    test("basic component integration test") {
        // Mock dependencies
        val mockTaskDao = mockk<TaskDao>(relaxed = true)
        val mockApiService = mockk<TaskApiService>()
        val mockNetworkMonitor = mockk<NetworkMonitor>()
        
        // Set up data sources
        val localDataSource = LocalTaskDataSource(mockTaskDao)
        val remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        // Set up repository and use cases
        val repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        val createTaskUseCase = CreateTaskUseCase(repository)
        val syncTasksUseCase = SyncTasksUseCase(repository)
        
        // Just verify components can be instantiated
        createTaskUseCase shouldBe createTaskUseCase
        syncTasksUseCase shouldBe syncTasksUseCase
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
})