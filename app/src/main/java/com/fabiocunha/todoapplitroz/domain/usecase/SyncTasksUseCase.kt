package com.fabiocunha.todoapplitroz.domain.usecase

import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Use case for synchronizing tasks with remote server
 */
class SyncTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.syncWithRemote()
    }
}