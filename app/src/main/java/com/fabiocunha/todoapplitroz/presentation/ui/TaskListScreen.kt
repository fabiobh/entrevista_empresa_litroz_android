package com.fabiocunha.todoapplitroz.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fabiocunha.todoapplitroz.R
import com.fabiocunha.todoapplitroz.domain.model.SyncStatus
import com.fabiocunha.todoapplitroz.domain.model.Task
import com.fabiocunha.todoapplitroz.presentation.viewmodel.TaskListViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main task list screen composable
 * Displays tasks in a LazyColumn with loading and error states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    modifier: Modifier = Modifier,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Network status indicator
        NetworkStatusIndicator(
            isOnline = uiState.isOnline,
            isSyncing = uiState.isSyncing,
            pendingSyncCount = uiState.pendingSyncCount,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Header with title and sync button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.my_tasks),
                style = MaterialTheme.typography.headlineMedium
            )
            
            Row {
                // Sync button
                IconButton(
                    onClick = { viewModel.syncTasks() },
                    enabled = !uiState.isSyncing && uiState.isOnline
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.sync_tasks),
                            tint = if (uiState.isOnline) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Add task button
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_task_content_description)
                    )
                }
            }
        }

        // Error message display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.clearError() }
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }

        // Main content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.tasks.isEmpty() -> {
                    // Initial loading state
                    LoadingIndicator()
                }
                uiState.tasks.isEmpty() -> {
                    // Empty state
                    EmptyTasksMessage(
                        onAddTaskClick = { showAddTaskDialog = true }
                    )
                }
                else -> {
                    // Task list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.tasks,
                            key = { task -> task.id }
                        ) { task ->
                            TaskItem(
                                task = task,
                                onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                onDeleteClick = { taskToDelete = task },
                                onEditTask = { updatedTask -> viewModel.updateTask(updatedTask) }
                            )
                        }
                    }
                }
            }

            // Loading overlay for operations
            if (uiState.isLoading && uiState.tasks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Add task dialog
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onAddTask = { description ->
                viewModel.createTask(description)
                showAddTaskDialog = false
            }
        )
    }

    // Delete confirmation dialog
    taskToDelete?.let { task ->
        DeleteTaskDialog(
            task = task,
            onDismiss = { taskToDelete = null },
            onConfirmDelete = {
                viewModel.deleteTask(task.id)
                taskToDelete = null
            }
        )
    }
}

/**
 * Individual task item composable
 */
@Composable
fun TaskItem(
    task: Task,
    onToggleComplete: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(task.description) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { 
                if (!isEditing) {
                    isEditing = true
                    editText = task.description
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completion checkbox
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggleComplete() }
                )

                // Task description or edit field
                if (isEditing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        trailingIcon = {
                            Row {
                                TextButton(
                                    onClick = {
                                        if (editText.isNotBlank()) {
                                            onEditTask(
                                                task.copy(
                                                    description = editText.trim(),
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        isEditing = false
                                    }
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                                TextButton(
                                    onClick = {
                                        editText = task.description
                                        isEditing = false
                                    }
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    )
                } else {
                    Text(
                        text = task.description,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .alpha(if (task.isCompleted) 0.6f else 1f),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_task_content_description),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Task metadata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sync status indicator
                SyncStatusIndicator(syncStatus = task.syncStatus)

                // Timestamp
                Text(
                    text = formatTimestamp(task.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sync status indicator composable
 */
@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    val text = when (syncStatus) {
        SyncStatus.SYNCED -> stringResource(R.string.synced)
        SyncStatus.PENDING_CREATE -> stringResource(R.string.pending_create)
        SyncStatus.PENDING_UPDATE -> stringResource(R.string.pending_update)
        SyncStatus.PENDING_DELETE -> stringResource(R.string.pending_delete)
    }
    
    val color = when (syncStatus) {
        SyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
        SyncStatus.PENDING_CREATE -> MaterialTheme.colorScheme.tertiary
        SyncStatus.PENDING_UPDATE -> MaterialTheme.colorScheme.secondary
        SyncStatus.PENDING_DELETE -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Loading indicator composable
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_tasks),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Empty tasks message composable
 */
@Composable
fun EmptyTasksMessage(
    onAddTaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.no_tasks_yet),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.add_first_task),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddTaskClick
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_task))
            }
        }
    }
}

/**
 * Network status indicator composable
 * Shows online/offline status and pending sync operations
 */
@Composable
fun NetworkStatusIndicator(
    isOnline: Boolean,
    isSyncing: Boolean,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isOnline && pendingSyncCount == 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isOnline && pendingSyncCount > 0 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    }
    
    val contentColor = when {
        isSyncing -> MaterialTheme.colorScheme.primary
        isOnline && pendingSyncCount == 0 -> MaterialTheme.colorScheme.primary
        isOnline && pendingSyncCount > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    val statusText = when {
        isSyncing -> stringResource(R.string.syncing)
        isOnline && pendingSyncCount == 0 -> stringResource(R.string.online_all_synced)
        isOnline && pendingSyncCount > 0 -> stringResource(R.string.online_pending_format, pendingSyncCount)
        else -> stringResource(R.string.offline)
    }
    
    val statusIcon = when {
        isSyncing -> Icons.Default.Refresh
        isOnline -> Icons.Default.Check
        else -> Icons.Default.Warning
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = contentColor
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Show pending operations details if any
            if (pendingSyncCount > 0 && !isSyncing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$pendingSyncCount",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}