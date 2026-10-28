package com.fearmikey.projectreporter.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.util.RoleLabels

@Composable
fun EditProjectDialog(
    project: ProjectEntity,
    role: String = "",
    onDismiss: () -> Unit,
    onConfirm: (oldId: String, newId: String, newName: String, newEngineer: String, engineerPhone: String, clientCompany: String, poc: String, phoneNumber: String, notes: String, onResult: (Boolean) -> Unit) -> Unit
) {
    var id by remember { mutableStateOf(project.displayProjectNumber ?: "") }
    var name by remember { mutableStateOf(project.projectName) }
    var engineer by remember { mutableStateOf(project.engineerName) }
    var engineerPhone by remember { mutableStateOf(project.engineerPhone) }
    var clientCompany by remember { mutableStateOf(project.clientCompany) }
    var poc by remember { mutableStateOf(project.poc) }
    var phoneNumber by remember { mutableStateOf(project.phoneNumber) }
    var notes by remember { mutableStateOf(project.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Site Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    value = clientCompany,
                    onValueChange = { clientCompany = it },
                    label = { Text("Client Company") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = poc,
                    onValueChange = { poc = it },
                    label = { Text("Point of Contact (POC)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("POC Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = engineer,
                    onValueChange = { engineer = it },
                    label = { Text(RoleLabels.nameLabel(role)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = engineerPhone,
                    onValueChange = { engineerPhone = it },
                    label = { Text(RoleLabels.phoneLabel(role)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g. Visitor access)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
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
                        onConfirm(project.projectId, finalId, trimmedName, trimmedEngineer, engineerPhone.trim(), clientCompany.trim(), poc.trim(), phoneNumber.trim(), notes.trim()) { success ->
                            if (!success) {
                                errorMessage = "Project Number already in use (check the Recycle Bin too)"
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
