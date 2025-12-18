package com.fabiocunha.todoapplitroz.domain.usecase

import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Use case for creating a new task
 */
class CreateTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(description: String): Result<Task> {
        return repository.createTask(description)
    }
}