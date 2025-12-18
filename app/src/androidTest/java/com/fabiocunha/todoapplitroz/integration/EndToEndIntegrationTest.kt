package com.fabiocunha.todoapplitroz.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fabiocunha.todoapplitroz.data.local.LocalTaskDataSource
import com.fabiocunha.todoapplitroz.data.local.database.AppDatabase
import com.fabiocunha.todoapplitroz.data.monitor.NetworkMonitorImpl
import com.fabiocunha.todoapplitroz.data.remote.RemoteTaskDataSource
import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.data.remote.dto.TaskDto
import com.fabiocunha.todoapplitroz.data.repository.TaskRepositoryImpl
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.DeleteTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.UpdateTaskUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

/**
 * End-to-end integration tests for complete user workflows
 * Tests offline/online synchronization scenarios and data consistency across app restarts
 */
@RunWith(AndroidJUnit4::class)
class EndToEndIntegrationTest {
    
    private lateinit var database: AppDatabase
    private lateinit var localDataSource: LocalTaskDataSource
    private lateinit var mockApiService: TaskApiService
    private lateinit var remoteDataSource: RemoteTaskDataSource
    private lateinit var mockNetworkMonitor: NetworkMonitorImpl
    private lateinit var repository: TaskRepositoryImpl
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var updateTaskUseCase: UpdateTaskUseCase
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase
    private lateinit var syncTasksUseCase: SyncTasksUseCase
    
    private val networkStateFlow = MutableStateFlow(true)
    
    @Before
    fun setup() {
        // Set up real in-memory database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        localDataSource = LocalTaskDataSource(database.taskDao())
        
        // Mock API service and network monitor
        mockApiService = mockk()
        remoteDataSource = RemoteTaskDataSource(mockApiService)
        
        mockNetworkMonitor = mockk()
        coEvery { mockNetworkMonitor.isOnline } returns networkStateFlow
        
        // Set up repository and use cases
        repository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        createTaskUseCase = CreateTaskUseCase(repository)
        updateTaskUseCase = UpdateTaskUseCase(repository)
        deleteTaskUseCase = DeleteTaskUseCase(repository)
        syncTasksUseCase = SyncTasksUseCase(repository)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun testCompleteOfflineToOnlineWorkflow() = runBlocking {
        // Start offline
        networkStateFlow.value = false
        
        // Create task offline
        val createResult = createTaskUseCase("Offline task")
        assertTrue("Task creation should succeed offline", createResult.isSuccess)
        
        val createdTask = createResult.getOrThrow()
        assertEquals("Task should have correct description", "Offline task", createdTask.description)
        assertEquals("Task should be pending create", SyncStatus.PENDING_CREATE, createdTask.syncStatus)
        
        // Verify task is stored locally
        val localTasks = repository.getAllTasks().first()
        assertEquals("Should have 1 task locally", 1, localTasks.size)
        assertEquals("Local task should match created task", createdTask.id, localTasks.first().id)
        
        // Update task offline
        val updatedTask = createdTask.copy(
            description = "Updated offline task",
            updatedAt = System.currentTimeMillis()
        )
        val updateResult = updateTaskUseCase(updatedTask)
        assertTrue("Task update should succeed offline", updateResult.isSuccess)
        
        // Verify update is stored locally
        val updatedLocalTasks = repository.getAllTasks().first()
        assertEquals("Should still have 1 task", 1, updatedLocalTasks.size)
        assertEquals("Task description should be updated", "Updated offline task", updatedLocalTasks.first().description)
        assertEquals("Task should still be pending create", SyncStatus.PENDING_CREATE, updatedLocalTasks.first().syncStatus)
        
        // Mock successful API responses for sync
        val syncedTaskDto = TaskDto(
            id = createdTask.id,
            description = "Updated offline task",
            isCompleted = false,
            createdAt = createdTask.createdAt,
            updatedAt = updatedTask.updatedAt
        )
        coEvery { mockApiService.createTask(any()) } returns Response.success(syncedTaskDto)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(syncedTaskDto))
        
        // Go online and sync
        networkStateFlow.value = true
        val syncResult = syncTasksUseCase()
        assertTrue("Sync should succeed", syncResult.isSuccess)
        
        // Verify task is now synced
        val syncedTasks = repository.getAllTasks().first()
        assertEquals("Should still have 1 task", 1, syncedTasks.size)
        assertEquals("Task should be synced", SyncStatus.SYNCED, syncedTasks.first().syncStatus)
        assertEquals("Task description should be preserved", "Updated offline task", syncedTasks.first().description)
    }
    
    @Test
    fun testDataConsistencyAcrossAppRestart() = runBlocking {
        // Create tasks with different sync statuses
        networkStateFlow.value = false
        
        // Create offline task
        val offlineResult = createTaskUseCase("Offline task")
        val offlineTask = offlineResult.getOrThrow()
        
        // Go online and create another task
        networkStateFlow.value = true
        val onlineTaskDto = TaskDto(
            id = "online-id",
            description = "Online task",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        coEvery { mockApiService.createTask(any()) } returns Response.success(onlineTaskDto)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(onlineTaskDto))
        
        val onlineResult = createTaskUseCase("Online task")
        val onlineTask = onlineResult.getOrThrow()
        
        // Verify both tasks exist
        val allTasks = repository.getAllTasks().first()
        assertEquals("Should have 2 tasks", 2, allTasks.size)
        
        val offlineTaskInList = allTasks.find { it.id == offlineTask.id }
        val onlineTaskInList = allTasks.find { it.id == onlineTask.id }
        
        assertNotNull("Offline task should exist", offlineTaskInList)
        assertNotNull("Online task should exist", onlineTaskInList)
        assertEquals("Offline task should be pending", SyncStatus.PENDING_CREATE, offlineTaskInList?.syncStatus)
        assertEquals("Online task should be synced", SyncStatus.SYNCED, onlineTaskInList?.syncStatus)
        
        // Simulate app restart by creating new repository instance
        val newRepository = TaskRepositoryImpl(localDataSource, remoteDataSource, mockNetworkMonitor)
        val tasksAfterRestart = newRepository.getAllTasks().first()
        
        // Verify data consistency after restart
        assertEquals("Should still have 2 tasks after restart", 2, tasksAfterRestart.size)
        
        val offlineTaskAfterRestart = tasksAfterRestart.find { it.id == offlineTask.id }
        val onlineTaskAfterRestart = tasksAfterRestart.find { it.id == onlineTask.id }
        
        assertNotNull("Offline task should persist after restart", offlineTaskAfterRestart)
        assertNotNull("Online task should persist after restart", onlineTaskAfterRestart)
        assertEquals("Offline task sync status should persist", SyncStatus.PENDING_CREATE, offlineTaskAfterRestart?.syncStatus)
        assertEquals("Online task sync status should persist", SyncStatus.SYNCED, onlineTaskAfterRestart?.syncStatus)
    }
    
    @Test
    fun testOfflineDeleteAndOnlineSync() = runBlocking {
        // Start online and create task
        networkStateFlow.value = true
        
        val originalTaskDto = TaskDto(
            id = "delete-test-id",
            description = "Task to delete",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        coEvery { mockApiService.createTask(any()) } returns Response.success(originalTaskDto)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(originalTaskDto))
        
        val createResult = createTaskUseCase("Task to delete")
        val taskToDelete = createResult.getOrThrow()
        
        // Go offline and delete task
        networkStateFlow.value = false
        
        val deleteResult = deleteTaskUseCase(taskToDelete.id)
        assertTrue("Delete should succeed offline", deleteResult.isSuccess)
        
        // Verify task is marked for deletion (not actually deleted yet)
        val pendingTasks = repository.getAllTasks().first()
        assertEquals("Should still have 1 task marked for deletion", 1, pendingTasks.size)
        assertEquals("Task should be marked for deletion", SyncStatus.PENDING_DELETE, pendingTasks.first().syncStatus)
        
        // Mock successful API response for deletion
        coEvery { mockApiService.deleteTask(taskToDelete.id) } returns Response.success(Unit)
        coEvery { mockApiService.getAllTasks() } returns Response.success(emptyList())
        
        // Go online and sync
        networkStateFlow.value = true
        val syncResult = syncTasksUseCase()
        assertTrue("Sync should succeed", syncResult.isSuccess)
        
        // Verify task is now completely deleted
        val finalTasks = repository.getAllTasks().first()
        assertEquals("Should have no tasks after sync", 0, finalTasks.size)
    }
    
    @Test
    fun testErrorHandlingAndRecovery() = runBlocking {
        // Test network error during sync
        networkStateFlow.value = true
        
        // Create task that will fail to sync
        coEvery { mockApiService.createTask(any()) } throws RuntimeException("Network error")
        
        val result = createTaskUseCase("Test task")
        assertTrue("Task creation should succeed locally even if remote fails", result.isSuccess)
        
        val createdTask = result.getOrThrow()
        assertEquals("Task should remain pending due to sync failure", SyncStatus.PENDING_CREATE, createdTask.syncStatus)
        
        // Verify task is stored locally despite remote failure
        val localTasks = repository.getAllTasks().first()
        assertEquals("Should have 1 task locally", 1, localTasks.size)
        assertEquals("Local task should match created task", createdTask.id, localTasks.first().id)
        
        // Test recovery when network is restored
        val syncedTaskDto = TaskDto(
            id = createdTask.id,
            description = createdTask.description,
            isCompleted = createdTask.isCompleted,
            createdAt = createdTask.createdAt,
            updatedAt = createdTask.updatedAt
        )
        coEvery { mockApiService.createTask(any()) } returns Response.success(syncedTaskDto)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(syncedTaskDto))
        
        val syncResult = syncTasksUseCase()
        assertTrue("Sync should succeed after network recovery", syncResult.isSuccess)
        
        // Verify task is now synced
        val syncedTasks = repository.getAllTasks().first()
        assertEquals("Should still have 1 task", 1, syncedTasks.size)
        assertEquals("Task should be synced after recovery", SyncStatus.SYNCED, syncedTasks.first().syncStatus)
    }
    
    @Test
    fun testMultipleOfflineOperationsSync() = runBlocking {
        // Start offline
        networkStateFlow.value = false
        
        // Create multiple tasks offline
        val task1Result = createTaskUseCase("Task 1")
        val task2Result = createTaskUseCase("Task 2")
        val task3Result = createTaskUseCase("Task 3")
        
        assertTrue("All task creations should succeed", 
            task1Result.isSuccess && task2Result.isSuccess && task3Result.isSuccess)
        
        val task1 = task1Result.getOrThrow()
        val task2 = task2Result.getOrThrow()
        val task3 = task3Result.getOrThrow()
        
        // Update one task
        val updatedTask2 = task2.copy(
            description = "Updated Task 2",
            updatedAt = System.currentTimeMillis()
        )
        updateTaskUseCase(updatedTask2)
        
        // Delete one task
        deleteTaskUseCase(task3.id)
        
        // Verify local state
        val localTasks = repository.getAllTasks().first()
        assertEquals("Should have 3 tasks locally", 3, localTasks.size)
        
        val pendingCreateTasks = localTasks.filter { it.syncStatus == SyncStatus.PENDING_CREATE }
        val pendingDeleteTasks = localTasks.filter { it.syncStatus == SyncStatus.PENDING_DELETE }
        
        assertEquals("Should have 2 tasks pending create", 2, pendingCreateTasks.size)
        assertEquals("Should have 1 task pending delete", 1, pendingDeleteTasks.size)
        
        // Mock API responses for sync
        val syncedTask1Dto = TaskDto(task1.id, task1.description, false, task1.createdAt, task1.updatedAt)
        val syncedTask2Dto = TaskDto(updatedTask2.id, updatedTask2.description, false, updatedTask2.createdAt, updatedTask2.updatedAt)
        
        coEvery { mockApiService.createTask(match { it.id == task1.id }) } returns Response.success(syncedTask1Dto)
        coEvery { mockApiService.createTask(match { it.id == updatedTask2.id }) } returns Response.success(syncedTask2Dto)
        coEvery { mockApiService.deleteTask(task3.id) } returns Response.success(Unit)
        coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(syncedTask1Dto, syncedTask2Dto))
        
        // Go online and sync
        networkStateFlow.value = true
        val syncResult = syncTasksUseCase()
        assertTrue("Sync should succeed", syncResult.isSuccess)
        
        // Verify final state
        val finalTasks = repository.getAllTasks().first()
        assertEquals("Should have 2 tasks after sync", 2, finalTasks.size)
        
        val syncedTasks = finalTasks.filter { it.syncStatus == SyncStatus.SYNCED }
        assertEquals("All remaining tasks should be synced", 2, syncedTasks.size)
        
        val task2Final = finalTasks.find { it.id == updatedTask2.id }
        assertNotNull("Updated task should exist", task2Final)
        assertEquals("Task 2 should have updated description", "Updated Task 2", task2Final?.description)
        
        val task3Final = finalTasks.find { it.id == task3.id }
        assertNull("Deleted task should not exist", task3Final)
    }
}