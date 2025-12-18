package com.fabiocunha.todoapplitroz.data.remote.api

import com.fabiocunha.todoapplitroz.data.remote.dto.TaskDto
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service for task operations
 */
interface TaskApiService {
    @GET("tasks")
    suspend fun getAllTasks(): Response<List<TaskDto>>
    
    @POST("tasks")
    suspend fun createTask(@Body task: TaskDto): Response<TaskDto>
    
    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body task: TaskDto): Response<TaskDto>
    
    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>
}