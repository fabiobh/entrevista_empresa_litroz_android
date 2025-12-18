package com.fabiocunha.todoapplitroz.data.remote

import com.fabiocunha.todoapplitroz.data.remote.api.TaskApiService
import com.fabiocunha.todoapplitroz.data.remote.dto.TaskDto
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import java.io.IOException
import java.util.UUID

/**
 * Property-based tests for RemoteTaskDataSource
 * **Feature: offline-task-sync, Property 3: Sync Engine Completeness**
 * **Validates: Requirements 6.1, 6.2, 6.5**
 */
class RemoteTaskDataSourcePropertyTest : StringSpec({

    "Successful API operations should return success results with correct data mapping" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.boolean(),
            Arb.long(min = 0),
            Arb.long(min = 0)
        ) { description, isCompleted, createdAt, updatedAt ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                val taskDto = TaskDto(
                    id = taskId,
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                // Mock successful API responses
                coEvery { mockApiService.createTask(any()) } returns Response.success(taskDto)
                coEvery { mockApiService.updateTask(taskId, any()) } returns Response.success(taskDto)
                coEvery { mockApiService.deleteTask(taskId) } returns Response.success(Unit)
                coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(taskDto))
                
                // Test create operation
                val createResult = dataSource.createTask(task)
                createResult.isSuccess shouldBe true
                val createdTask = createResult.getOrNull()!!
                createdTask.id shouldBe taskId
                createdTask.description shouldBe description
                createdTask.isCompleted shouldBe isCompleted
                createdTask.syncStatus shouldBe SyncStatus.SYNCED // Remote tasks are marked as synced
                
                // Test update operation
                val updateResult = dataSource.updateTask(task)
                updateResult.isSuccess shouldBe true
                val updatedTask = updateResult.getOrNull()!!
                updatedTask.id shouldBe taskId
                updatedTask.description shouldBe description
                updatedTask.isCompleted shouldBe isCompleted
                updatedTask.syncStatus shouldBe SyncStatus.SYNCED
                
                // Test delete operation
                val deleteResult = dataSource.deleteTask(taskId)
                deleteResult.isSuccess shouldBe true
                
                // Test get all operation
                val getAllResult = dataSource.getAllTasks()
                getAllResult.isSuccess shouldBe true
                val tasks = getAllResult.getOrNull()!!
                tasks.size shouldBe 1
                tasks[0].id shouldBe taskId
                tasks[0].syncStatus shouldBe SyncStatus.SYNCED
                
                // Verify API service was called with correct parameters
                coVerify { mockApiService.createTask(match { it.id == taskId && it.description == description }) }
                coVerify { mockApiService.updateTask(taskId, match { it.id == taskId && it.description == description }) }
                coVerify { mockApiService.deleteTask(taskId) }
                coVerify { mockApiService.getAllTasks() }
            }
        }
    }

    "Network errors should return failure results with appropriate error handling" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.boolean(),
            Arb.long(min = 0),
            Arb.long(min = 0)
        ) { description, isCompleted, createdAt, updatedAt ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                val networkException = IOException("Network connection failed")
                
                // Mock network failures
                coEvery { mockApiService.createTask(any()) } throws networkException
                coEvery { mockApiService.updateTask(any(), any()) } throws networkException
                coEvery { mockApiService.deleteTask(any()) } throws networkException
                coEvery { mockApiService.getAllTasks() } throws networkException
                
                // Test that all operations return failure results
                val createResult = dataSource.createTask(task)
                createResult.isFailure shouldBe true
                (createResult.exceptionOrNull() is NetworkException) shouldBe true
                
                val updateResult = dataSource.updateTask(task)
                updateResult.isFailure shouldBe true
                (updateResult.exceptionOrNull() is NetworkException) shouldBe true
                
                val deleteResult = dataSource.deleteTask(taskId)
                deleteResult.isFailure shouldBe true
                (deleteResult.exceptionOrNull() is NetworkException) shouldBe true
                
                val getAllResult = dataSource.getAllTasks()
                getAllResult.isFailure shouldBe true
                (getAllResult.exceptionOrNull() is NetworkException) shouldBe true
            }
        }
    }

    "HTTP error responses should return failure results with error details" {
        checkAll(
            Arb.int(400, 599), // HTTP error codes
            Arb.string(1..100).filter { it.isNotBlank() }
        ) { errorCode, description ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                // Mock HTTP error responses
                val errorResponse = mockk<Response<TaskDto>>()
                every { errorResponse.isSuccessful } returns false
                every { errorResponse.code() } returns errorCode
                every { errorResponse.message() } returns "HTTP Error"
                
                val errorResponseUnit = mockk<Response<Unit>>()
                every { errorResponseUnit.isSuccessful } returns false
                every { errorResponseUnit.code() } returns errorCode
                every { errorResponseUnit.message() } returns "HTTP Error"
                
                val errorResponseList = mockk<Response<List<TaskDto>>>()
                every { errorResponseList.isSuccessful } returns false
                every { errorResponseList.code() } returns errorCode
                every { errorResponseList.message() } returns "HTTP Error"
                
                coEvery { mockApiService.createTask(any()) } returns errorResponse
                coEvery { mockApiService.updateTask(any(), any()) } returns errorResponse
                coEvery { mockApiService.deleteTask(any()) } returns errorResponseUnit
                coEvery { mockApiService.getAllTasks() } returns errorResponseList
                
                // Test that all operations return failure results with error codes
                val createResult = dataSource.createTask(task)
                createResult.isFailure shouldBe true
                
                val updateResult = dataSource.updateTask(task)
                updateResult.isFailure shouldBe true
                
                val deleteResult = dataSource.deleteTask(taskId)
                deleteResult.isFailure shouldBe true
                
                val getAllResult = dataSource.getAllTasks()
                getAllResult.isFailure shouldBe true
            }
        }
    }

    "Empty response bodies should be handled gracefully" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() }
        ) { description ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                val taskId = UUID.randomUUID().toString()
                val task = Task(
                    id = taskId,
                    description = description,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING_CREATE
                )
                
                // Mock successful responses with null bodies
                val successResponseWithNullBody = mockk<Response<TaskDto>>()
                every { successResponseWithNullBody.isSuccessful } returns true
                every { successResponseWithNullBody.body() } returns null
                
                val successResponseWithEmptyList = mockk<Response<List<TaskDto>>>()
                every { successResponseWithEmptyList.isSuccessful } returns true
                every { successResponseWithEmptyList.body() } returns null
                
                coEvery { mockApiService.createTask(any()) } returns successResponseWithNullBody
                coEvery { mockApiService.updateTask(any(), any()) } returns successResponseWithNullBody
                coEvery { mockApiService.getAllTasks() } returns successResponseWithEmptyList
                
                // Test create with null body
                val createResult = dataSource.createTask(task)
                createResult.isFailure shouldBe true
                
                // Test update with null body
                val updateResult = dataSource.updateTask(task)
                updateResult.isFailure shouldBe true
                
                // Test get all with null body (should return empty list)
                val getAllResult = dataSource.getAllTasks()
                getAllResult.isSuccess shouldBe true
                getAllResult.getOrNull()!!.isEmpty() shouldBe true
            }
        }
    }

    "Data mapping between DTOs and domain models should preserve all fields" {
        checkAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.boolean(),
            Arb.long(min = 0),
            Arb.long(min = 0)
        ) { description, isCompleted, createdAt, updatedAt ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                val taskId = UUID.randomUUID().toString()
                
                // Create a DTO that would come from the API
                val taskDto = TaskDto(
                    id = taskId,
                    description = description,
                    isCompleted = isCompleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                // Mock successful API response
                coEvery { mockApiService.getAllTasks() } returns Response.success(listOf(taskDto))
                
                // Get tasks from remote source
                val result = dataSource.getAllTasks()
                result.isSuccess shouldBe true
                
                val tasks = result.getOrNull()!!
                tasks.size shouldBe 1
                
                val mappedTask = tasks[0]
                
                // Verify all fields are correctly mapped
                mappedTask.id shouldBe taskDto.id
                mappedTask.description shouldBe taskDto.description
                mappedTask.isCompleted shouldBe taskDto.isCompleted
                mappedTask.createdAt shouldBe taskDto.createdAt
                mappedTask.updatedAt shouldBe taskDto.updatedAt
                mappedTask.syncStatus shouldBe SyncStatus.SYNCED // Remote tasks are always synced
            }
        }
    }

    "Batch operations should handle multiple tasks consistently" {
        checkAll(
            Arb.list(
                Arb.bind(
                    Arb.string(1..100).filter { it.isNotBlank() },
                    Arb.boolean(),
                    Arb.long(min = 0),
                    Arb.long(min = 0)
                ) { desc, completed, created, updated ->
                    TaskDto(
                        id = UUID.randomUUID().toString(),
                        description = desc,
                        isCompleted = completed,
                        createdAt = created,
                        updatedAt = updated
                    )
                },
                range = 0..10
            )
        ) { taskDtos ->
            runTest {
                val mockApiService = mockk<TaskApiService>()
                val dataSource = RemoteTaskDataSource(mockApiService)
                
                // Mock successful API response with multiple tasks
                coEvery { mockApiService.getAllTasks() } returns Response.success(taskDtos)
                
                val result = dataSource.getAllTasks()
                result.isSuccess shouldBe true
                
                val tasks = result.getOrNull()!!
                tasks.size shouldBe taskDtos.size
                
                // Verify each task is correctly mapped
                tasks.forEachIndexed { index, task ->
                    val originalDto = taskDtos[index]
                    task.id shouldBe originalDto.id
                    task.description shouldBe originalDto.description
                    task.isCompleted shouldBe originalDto.isCompleted
                    task.createdAt shouldBe originalDto.createdAt
                    task.updatedAt shouldBe originalDto.updatedAt
                    task.syncStatus shouldBe SyncStatus.SYNCED
                }
            }
        }
    }
})