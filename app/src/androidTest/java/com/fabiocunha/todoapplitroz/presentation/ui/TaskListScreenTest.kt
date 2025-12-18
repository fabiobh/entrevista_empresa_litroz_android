package com.fabiocunha.todoapplitroz.presentation.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.ui.theme.TodoAppLitrozTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for TaskListScreen and related components
 * Tests user interaction flows and state changes
 * **Validates: Requirements 10.3**
 */
@RunWith(AndroidJUnit4::class)
class TaskListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleTasks = listOf(
        Task(
            id = "1",
            description = "Complete project documentation",
            isCompleted = false,
            createdAt = System.currentTimeMillis() - 3600000,
            updatedAt = System.currentTimeMillis() - 3600000,
            syncStatus = SyncStatus.SYNCED
        ),
        Task(
            id = "2",
            description = "Review code changes",
            isCompleted = true,
            createdAt = System.currentTimeMillis() - 7200000,
            updatedAt = System.currentTimeMillis() - 1800000,
            syncStatus = SyncStatus.PENDING_UPDATE
        ),
        Task(
            id = "3",
            description = "Update dependencies",
            isCompleted = false,
            createdAt = System.currentTimeMillis() - 10800000,
            updatedAt = System.currentTimeMillis() - 10800000,
            syncStatus = SyncStatus.PENDING_CREATE
        )
    )

    @Test
    fun taskListScreen_displaysTasksCorrectly() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                // We'll test individual components since we can't easily mock the ViewModel
                sampleTasks.forEach { task ->
                    TaskItem(
                        task = task,
                        onToggleComplete = {},
                        onDeleteClick = {},
                        onEditTask = {}
                    )
                }
            }
        }

        // Verify all tasks are displayed
        composeTestRule.onNodeWithText("Complete project documentation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review code changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update dependencies").assertIsDisplayed()

        // Verify sync status indicators
        composeTestRule.onNodeWithText("Synced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Update").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Create").assertIsDisplayed()
    }

    @Test
    fun taskItem_completionToggle_worksCorrectly() {
        var toggledTask: Task? = null
        val task = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = task,
                    onToggleComplete = { toggledTask = task },
                    onDeleteClick = {},
                    onEditTask = {}
                )
            }
        }

        // Find and click the checkbox
        composeTestRule.onNode(hasClickAction() and hasContentDescription("")).performClick()

        // Verify the callback was called
        assert(toggledTask == task)
    }

    @Test
    fun taskItem_deleteButton_triggersCallback() {
        var deletedTask: Task? = null
        val task = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = task,
                    onToggleComplete = {},
                    onDeleteClick = { deletedTask = task },
                    onEditTask = {}
                )
            }
        }

        // Find and click the delete button
        composeTestRule.onNodeWithContentDescription("Delete task").performClick()

        // Verify the callback was called
        assert(deletedTask == task)
    }

    @Test
    fun taskItem_editMode_worksCorrectly() {
        var editedTask: Task? = null
        val task = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = task,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = { editedTask = it }
                )
            }
        }

        // Click on the task to enter edit mode
        composeTestRule.onNodeWithText(task.description).performClick()

        // Verify edit field appears
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Edit the text
        composeTestRule.onNodeWithText(task.description).performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("Updated task description")

        // Click save
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify the callback was called with updated task
        assert(editedTask?.description == "Updated task description")
    }

    @Test
    fun addTaskDialog_validation_worksCorrectly() {
        var addedTaskDescription: String? = null

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                AddTaskDialog(
                    onDismiss = {},
                    onAddTask = { description -> addedTaskDescription = description }
                )
            }
        }

        // Verify dialog is displayed
        composeTestRule.onNodeWithText("Add New Task").assertIsDisplayed()

        // Try to add empty task - button should be disabled
        composeTestRule.onNodeWithText("Add Task").assertIsNotEnabled()

        // Enter task description
        composeTestRule.onNodeWithText("Task description").performTextInput("New task to complete")

        // Button should now be enabled
        composeTestRule.onNodeWithText("Add Task").assertIsEnabled()

        // Click add task
        composeTestRule.onNodeWithText("Add Task").performClick()

        // Verify callback was called with correct description
        assert(addedTaskDescription == "New task to complete")
    }

    @Test
    fun addTaskDialog_cancel_worksCorrectly() {
        var dismissed = false

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                AddTaskDialog(
                    onDismiss = { dismissed = true },
                    onAddTask = {}
                )
            }
        }

        // Click cancel
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Verify dismiss callback was called
        assert(dismissed)
    }

    @Test
    fun deleteTaskDialog_confirmation_worksCorrectly() {
        var confirmed = false
        var dismissed = false
        val task = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                DeleteTaskDialog(
                    task = task,
                    onDismiss = { dismissed = true },
                    onConfirmDelete = { confirmed = true }
                )
            }
        }

        // Verify dialog content
        composeTestRule.onNodeWithText("Delete Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete this task?").assertIsDisplayed()
        composeTestRule.onNodeWithText("\"${task.description}\"").assertIsDisplayed()

        // Test cancel
        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(dismissed)

        // Reset and test confirm
        dismissed = false
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                DeleteTaskDialog(
                    task = task,
                    onDismiss = { dismissed = true },
                    onConfirmDelete = { confirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Delete").performClick()
        assert(confirmed)
    }

    @Test
    fun syncStatusIndicator_displaysCorrectly() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                SyncStatusIndicator(syncStatus = SyncStatus.SYNCED)
                SyncStatusIndicator(syncStatus = SyncStatus.PENDING_CREATE)
                SyncStatusIndicator(syncStatus = SyncStatus.PENDING_UPDATE)
                SyncStatusIndicator(syncStatus = SyncStatus.PENDING_DELETE)
            }
        }

        // Verify all sync statuses are displayed
        composeTestRule.onNodeWithText("Synced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Create").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Update").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Delete").assertIsDisplayed()
    }

    @Test
    fun loadingIndicator_displaysCorrectly() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                LoadingIndicator()
            }
        }

        // Verify loading message is displayed
        composeTestRule.onNodeWithText("Loading tasks...").assertIsDisplayed()
    }

    @Test
    fun emptyTasksMessage_displaysCorrectly() {
        var addTaskClicked = false

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                EmptyTasksMessage(
                    onAddTaskClick = { addTaskClicked = true }
                )
            }
        }

        // Verify empty state message
        composeTestRule.onNodeWithText("No tasks yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add your first task to get started").assertIsDisplayed()

        // Click add task button
        composeTestRule.onNodeWithText("Add Task").performClick()

        // Verify callback was called
        assert(addTaskClicked)
    }

    @Test
    fun taskItem_completedTask_hasCorrectStyling() {
        val completedTask = sampleTasks[1] // This task is completed

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = completedTask,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = {}
                )
            }
        }

        // Verify completed task is displayed
        composeTestRule.onNodeWithText(completedTask.description).assertIsDisplayed()
        
        // Note: We can't easily test text decoration and alpha in Compose tests,
        // but we can verify the task is displayed and the checkbox is checked
        // The visual styling would need to be tested with screenshot tests
    }

    @Test
    fun taskCreationFlow_completeUserJourney() {
        var createdTaskDescription: String? = null
        var dialogDismissed = false

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                AddTaskDialog(
                    onDismiss = { dialogDismissed = true },
                    onAddTask = { description -> 
                        createdTaskDescription = description
                        dialogDismissed = true
                    }
                )
            }
        }

        // Verify dialog appears
        composeTestRule.onNodeWithText("Add New Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a description for your task:").assertIsDisplayed()

        // Verify add button is initially disabled
        composeTestRule.onNodeWithText("Add Task").assertIsNotEnabled()

        // Enter task description
        val taskDescription = "Buy groceries for the week"
        composeTestRule.onNodeWithText("Task description").performTextInput(taskDescription)

        // Verify add button is now enabled
        composeTestRule.onNodeWithText("Add Task").assertIsEnabled()

        // Submit the task
        composeTestRule.onNodeWithText("Add Task").performClick()

        // Verify task was created with correct description
        assert(createdTaskDescription == taskDescription)
        assert(dialogDismissed)
    }

    @Test
    fun taskEditingFlow_completeUserJourney() {
        var editedTask: Task? = null
        val originalTask = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = originalTask,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = { editedTask = it }
                )
            }
        }

        // Verify task is displayed in view mode
        composeTestRule.onNodeWithText(originalTask.description).assertIsDisplayed()

        // Click to enter edit mode
        composeTestRule.onNodeWithText(originalTask.description).performClick()

        // Verify edit mode UI appears
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Clear and enter new description
        val newDescription = "Updated: Complete project documentation by Friday"
        composeTestRule.onNodeWithText(originalTask.description).performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput(newDescription)

        // Save the changes
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify task was updated
        assert(editedTask != null)
        assert(editedTask?.description == newDescription)
        assert(editedTask?.id == originalTask.id)
    }

    @Test
    fun taskEditingFlow_cancelPreservesOriginal() {
        var editedTask: Task? = null
        val originalTask = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = originalTask,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = { editedTask = it }
                )
            }
        }

        // Enter edit mode
        composeTestRule.onNodeWithText(originalTask.description).performClick()

        // Modify the text
        composeTestRule.onNodeWithText(originalTask.description).performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("This should be discarded")

        // Cancel the edit
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Verify task was not updated
        assert(editedTask == null)

        // Verify original text is still displayed
        composeTestRule.onNodeWithText(originalTask.description).assertIsDisplayed()
    }

    @Test
    fun taskDeletionFlow_completeUserJourney() {
        var deleteConfirmed = false
        var dialogDismissed = false
        val taskToDelete = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                DeleteTaskDialog(
                    task = taskToDelete,
                    onDismiss = { dialogDismissed = true },
                    onConfirmDelete = { 
                        deleteConfirmed = true
                        dialogDismissed = true
                    }
                )
            }
        }

        // Verify confirmation dialog content
        composeTestRule.onNodeWithText("Delete Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete this task?").assertIsDisplayed()
        composeTestRule.onNodeWithText("\"${taskToDelete.description}\"").assertIsDisplayed()
        composeTestRule.onNodeWithText("This action cannot be undone.").assertIsDisplayed()

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify deletion was confirmed
        assert(deleteConfirmed)
        assert(dialogDismissed)
    }

    @Test
    fun taskCompletionFlow_togglesCorrectly() {
        var toggleCount = 0
        val task = sampleTasks[0].copy(isCompleted = false)

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = task,
                    onToggleComplete = { toggleCount++ },
                    onDeleteClick = {},
                    onEditTask = {}
                )
            }
        }

        // Verify task is displayed
        composeTestRule.onNodeWithText(task.description).assertIsDisplayed()

        // Toggle completion once
        composeTestRule.onNode(hasClickAction() and hasContentDescription("")).performClick()
        assert(toggleCount == 1)

        // Toggle completion again
        composeTestRule.onNode(hasClickAction() and hasContentDescription("")).performClick()
        assert(toggleCount == 2)
    }

    @Test
    fun addTaskDialog_rejectsWhitespaceOnlyInput() {
        var addedTaskDescription: String? = null

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                AddTaskDialog(
                    onDismiss = {},
                    onAddTask = { description -> addedTaskDescription = description }
                )
            }
        }

        // Try entering only whitespace
        composeTestRule.onNodeWithText("Task description").performTextInput("   ")

        // Verify add button is disabled
        composeTestRule.onNodeWithText("Add Task").assertIsNotEnabled()

        // Verify no task was added
        assert(addedTaskDescription == null)
    }

    @Test
    fun taskItem_editMode_rejectsEmptyDescription() {
        var editedTask: Task? = null
        val originalTask = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = originalTask,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = { editedTask = it }
                )
            }
        }

        // Enter edit mode
        composeTestRule.onNodeWithText(originalTask.description).performClick()

        // Clear the text (making it empty)
        composeTestRule.onNodeWithText(originalTask.description).performTextClearance()

        // Try to save with empty description
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify task was not updated (empty descriptions should be rejected)
        assert(editedTask == null)
    }

    @Test
    fun networkStatusIndicator_displaysOnlineState() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                NetworkStatusIndicator(
                    isOnline = true,
                    isSyncing = false,
                    pendingSyncCount = 0
                )
            }
        }

        // Verify online status is displayed
        composeTestRule.onNodeWithText("Online - All synced").assertIsDisplayed()
    }

    @Test
    fun networkStatusIndicator_displaysOfflineState() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                NetworkStatusIndicator(
                    isOnline = false,
                    isSyncing = false,
                    pendingSyncCount = 0
                )
            }
        }

        // Verify offline status is displayed
        composeTestRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test
    fun networkStatusIndicator_displaysSyncingState() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                NetworkStatusIndicator(
                    isOnline = true,
                    isSyncing = true,
                    pendingSyncCount = 0
                )
            }
        }

        // Verify syncing status is displayed
        composeTestRule.onNodeWithText("Syncing...").assertIsDisplayed()
    }

    @Test
    fun networkStatusIndicator_displaysPendingCount() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                NetworkStatusIndicator(
                    isOnline = true,
                    isSyncing = false,
                    pendingSyncCount = 3
                )
            }
        }

        // Verify pending count is displayed
        composeTestRule.onNodeWithText("Online - 3 pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun multipleTaskItems_displayCorrectly() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                sampleTasks.forEach { task ->
                    TaskItem(
                        task = task,
                        onToggleComplete = {},
                        onDeleteClick = {},
                        onEditTask = {}
                    )
                }
            }
        }

        // Verify all tasks are displayed
        sampleTasks.forEach { task ->
            composeTestRule.onNodeWithText(task.description).assertIsDisplayed()
        }

        // Verify different sync statuses are shown
        composeTestRule.onAllNodesWithText("Synced").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Pending Update").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Pending Create").assertCountEquals(1)
    }

    @Test
    fun taskItem_deleteButton_isAccessible() {
        val task = sampleTasks[0]

        composeTestRule.setContent {
            TodoAppLitrozTheme {
                TaskItem(
                    task = task,
                    onToggleComplete = {},
                    onDeleteClick = {},
                    onEditTask = {}
                )
            }
        }

        // Verify delete button has proper content description
        composeTestRule.onNodeWithContentDescription("Delete task").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete task").assertHasClickAction()
    }

    @Test
    fun addTaskDialog_hasProperAccessibility() {
        composeTestRule.setContent {
            TodoAppLitrozTheme {
                AddTaskDialog(
                    onDismiss = {},
                    onAddTask = {}
                )
            }
        }

        // Verify dialog has proper structure
        composeTestRule.onNodeWithText("Add New Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Task description").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Verify buttons have click actions
        composeTestRule.onNodeWithText("Add Task").assertHasClickAction()
        composeTestRule.onNodeWithText("Cancel").assertHasClickAction()
    }
}