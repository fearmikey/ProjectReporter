package com.fearmikey.projectreporter.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.projectreporter.data.entity.ProjectEntity

@Composable
fun EditProjectDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onConfirm: (oldId: String, newId: String, newName: String, newEngineer: String, onResult: (Boolean) -> Unit) -> Unit
) {
    var id by remember { mutableStateOf(project.displayProjectNumber ?: "") }
    var name by remember { mutableStateOf(project.projectName) }
    var engineer by remember { mutableStateOf(project.engineerName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Site Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        errorMessage = null
                    },
                    label = { Text("Project Number (Optional)") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = engineer,
                    onValueChange = { engineer = it },
                    label = { Text("Engineer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedId = id.trim()
                    val trimmedName = name.trim()
                    val trimmedEngineer = engineer.trim()
                    if (trimmedName.isNotBlank()) {
                        val finalId = if (trimmedId.isNotBlank()) {
                            trimmedId
                        } else {
                            if (project.displayProjectNumber == null) project.projectId else "proj_${java.util.UUID.randomUUID()}"
                        }
                        onConfirm(project.projectId, finalId, trimmedName, trimmedEngineer) { success ->
                            if (!success) {
                                errorMessage = "Project Number already exists"
                            }
                        }
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
