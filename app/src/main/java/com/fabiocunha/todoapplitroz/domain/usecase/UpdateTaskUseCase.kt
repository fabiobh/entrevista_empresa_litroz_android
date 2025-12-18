package com.fabiocunha.todoapplitroz.domain.usecase

import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Use case for updating an existing task
 */
class UpdateTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(task: Task): Result<Task> {
        return repository.updateTask(task)
    }
}