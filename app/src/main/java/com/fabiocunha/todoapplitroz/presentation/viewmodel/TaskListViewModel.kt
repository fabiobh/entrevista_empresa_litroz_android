package com.fabiocunha.todoapplitroz.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabiocunha.todoapplitroz.R
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.domain.monitor.NetworkMonitor
import com.fabiocunha.todoapplitroz.domain.usecase.CreateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.DeleteTaskUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.SyncTasksUseCase
import com.fabiocunha.todoapplitroz.domain.usecase.UpdateTaskUseCase
import com.fabiocunha.todoapplitroz.domain.repository.TaskRepository
import com.fabiocunha.todoapplitroz.presentation.ui.TaskListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for task list screen
 * Manages UI state and handles user interactions for CRUD operations
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val networkMonitor: NetworkMonitor,
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val syncTasksUseCase: SyncTasksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
        observeNetworkStatus()
    }

    /**
     * Load tasks from repository and observe network status
     */
    private fun loadTasks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            combine(
                taskRepository.getAllTasks(),
                networkMonitor.isOnline
            ) { tasks, isOnline ->
                val pendingSyncCount = tasks.count { it.syncStatus != SyncStatus.SYNCED }
                TaskListUiState(
                    tasks = tasks,
                    isLoading = false,
                    isSyncing = _uiState.value.isSyncing,
                    isOnline = isOnline,
                    pendingSyncCount = pendingSyncCount,
                    error = null
                )
            }
            .catch { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = context.getString(R.string.error_load_tasks, exception.message ?: "")
                )
            }
            .collect { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * Observe network connectivity status
     */
    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(isOnline = isOnline)
            }
        }
    }

    /**
     * Create a new task
     */
    fun createTask(description: String) {
        if (description.isBlank()) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.error_task_description_empty))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            createTaskUseCase(description.trim())
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_create_task, exception.message ?: "")
                    )
                }
        }
    }

    /**
     * Update an existing task
     */
    fun updateTask(task: Task) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            updateTaskUseCase(task)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_update_task, exception.message ?: "")
                    )
                }
        }
    }

    /**
     * Toggle task completion status
     */
    fun toggleTaskCompletion(task: Task) {
        val updatedTask = task.copy(
            isCompleted = !task.isCompleted,
            updatedAt = System.currentTimeMillis()
        )
        updateTask(updatedTask)
    }

    /**
     * Delete a task
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            deleteTaskUseCase(taskId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_delete_task, exception.message ?: "")
                    )
                }
        }
    }

    /**
     * Sync tasks with remote server
     */
    fun syncTasks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            
            syncTasksUseCase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSyncing = false, error = null)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        error = context.getString(R.string.error_sync_tasks, exception.message ?: "")
                    )
                }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}