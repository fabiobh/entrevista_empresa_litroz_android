package com.fabiocunha.todoapplitroz.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabiocunha.todoapplitroz.R
import com.fabiocunha.todoapplitroz.domain.model.Task

/**
 * Dialog for adding a new task
 */
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAddTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var taskDescription by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.add_new_task))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.enter_task_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text(stringResource(R.string.task_description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = false,
                    maxLines = 3,
                    isError = taskDescription.isBlank() && taskDescription.isNotEmpty()
                )
                if (taskDescription.isBlank() && taskDescription.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.task_description_empty_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (taskDescription.isNotBlank()) {
                        onAddTask(taskDescription.trim())
                    }
                },
                enabled = taskDescription.isNotBlank()
            ) {
                Text(stringResource(R.string.add_task))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier
    )

    // Request focus when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Dialog for confirming task deletion
 */
@Composable
fun DeleteTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.delete_task))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.delete_task_confirmation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "\"${task.description}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                )
                Text(
                    text = stringResource(R.string.delete_action_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier
    )
}