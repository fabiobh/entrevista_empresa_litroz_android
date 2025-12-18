package com.fabiocunha.todoapplitroz.presentation.ui

import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for TaskListUiState and related UI state classes
 * **Validates: Requirements 10.3**
 */
class TaskListUiStateTest : FunSpec({

    test("taskListUiState default values are correct") {
        val uiState = TaskListUiState()
        
        uiState.tasks shouldBe emptyList()
        uiState.isLoading shouldBe false
        uiState.isSyncing shouldBe false
        uiState.isOnline shouldBe true
        uiState.pendingSyncCount shouldBe 0
        uiState.error shouldBe null
    }

    test("taskListUiState with tasks calculates correctly") {
        val tasks = listOf(
            Task(
                id = "1",
                description = "Task 1",
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.SYNCED
            ),
            Task(
                id = "2",
                description = "Task 2",
                isCompleted = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPDATE
            )
        )

        val uiState = TaskListUiState(
            tasks = tasks,
            isLoading = false,
            isSyncing = false,
            isOnline = true,
            pendingSyncCount = 1,
            error = null
        )

        uiState.tasks shouldBe tasks
        uiState.tasks.size shouldBe 2
        uiState.pendingSyncCount shouldBe 1
    }

    test("taskListUiState with error preserves error message") {
        val errorMessage = "Network connection failed"
        val uiState = TaskListUiState(error = errorMessage)

        uiState.error shouldBe errorMessage
    }

    test("taskListUiState loading states work correctly") {
        val loadingState = TaskListUiState(isLoading = true)
        val syncingState = TaskListUiState(isSyncing = true)

        loadingState.isLoading shouldBe true
        syncingState.isSyncing shouldBe true
    }

    test("taskListUiState network states work correctly") {
        val onlineState = TaskListUiState(isOnline = true)
        val offlineState = TaskListUiState(isOnline = false)

        onlineState.isOnline shouldBe true
        offlineState.isOnline shouldBe false
    }

    test("taskItemUiState default values are correct") {
        val task = Task(
            id = "1",
            description = "Test task",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )

        val itemState = TaskItemUiState(task = task)

        itemState.task shouldBe task
        itemState.isEditing shouldBe false
        itemState.isDeleting shouldBe false
    }

    test("taskItemUiState editing state works correctly") {
        val task = Task(
            id = "1",
            description = "Test task",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )

        val editingState = TaskItemUiState(task = task, isEditing = true)
        val deletingState = TaskItemUiState(task = task, isDeleting = true)

        editingState.isEditing shouldBe true
        deletingState.isDeleting shouldBe true
    }

    test("taskOperationState sealed class works correctly") {
        val idleState = TaskOperationState.Idle
        val loadingState = TaskOperationState.Loading
        val successState = TaskOperationState.Success("Operation completed")
        val errorState = TaskOperationState.Error("Operation failed")

        idleState.shouldBeInstanceOf<TaskOperationState.Idle>()
        loadingState.shouldBeInstanceOf<TaskOperationState.Loading>()
        successState.shouldBeInstanceOf<TaskOperationState.Success>()
        errorState.shouldBeInstanceOf<TaskOperationState.Error>()

        successState.message shouldBe "Operation completed"
        errorState.message shouldBe "Operation failed"
    }
})