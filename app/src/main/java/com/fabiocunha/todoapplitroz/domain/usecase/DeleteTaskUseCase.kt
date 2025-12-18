package com.fabiocunha.todoapplitroz.domain.usecase

import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Use case for deleting a task
 */
class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String): Result<Unit> {
        return repository.deleteTask(taskId)
    }
}