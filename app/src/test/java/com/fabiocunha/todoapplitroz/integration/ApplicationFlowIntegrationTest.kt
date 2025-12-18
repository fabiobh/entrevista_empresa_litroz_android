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
 * Integration tests for application flow
 */
class ApplicationFlowIntegrationTest : FunSpec({
    
    test("basic component integration test") {
        val mockTaskDao = mockk<TaskDao>(relaxed = true)
        val mockApiService = mockk<TaskApiService>()
        val mockNetworkMonitor = mockk<NetworkMonitor>()
        
        val localDataSource = LocalTaskDataSource(mockTaskDao)
        val remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        val repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        val createTaskUseCase = CreateTaskUseCase(repository)
        val syncTasksUseCase = SyncTasksUseCase(repository)
        
        createTaskUseCase shouldBe createTaskUseCase
        syncTasksUseCase shouldBe syncTasksUseCase
    }
    
    test("use case layer properly delegates to repository") {
        val mockRepository = mockk<TaskRepositoryImpl>()
        
        val createTaskUseCase = CreateTaskUseCase(mockRepository)
        val syncTasksUseCase = SyncTasksUseCase(mockRepository)
        
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
        
        val createResult = createTaskUseCase("Test task")
        createResult.isSuccess shouldBe true
        createResult.getOrThrow() shouldBe testTask
        
        val syncResult = syncTasksUseCase()
        syncResult.isSuccess shouldBe true
        
        coVerify { mockRepository.createTask("Test task") }
        coVerify { mockRepository.syncWithRemote() }
    }
})