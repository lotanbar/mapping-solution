package com.mappingsolution.ui.common

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.mappingsolution.data.model.Group
import androidx.compose.ui.res.painterResource
import com.mappingsolution.ui.common.IconCatalog

/**
 * Shared group picker dropdown used in POI and route forms.
 * Shows the selected group's icon+color as a leading icon in the field,
 * and icon+color for each item in the dropdown.
 *
 * @param showCreateGroup If true, appends a "+ New Group" action at the bottom of the list.
 * @param onCreateGroup   Called when the user taps "+ New Group".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPickerField(
    groups: List<Group>,
    selectedGroupId: String?,
    onGroupSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showCreateGroup: Boolean = false,
    onCreateGroup: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedGroup?.name ?: "No group",
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            leadingIcon = selectedGroup?.let { group ->
                {
                    Icon(
                        painter = painterResource(IconCatalog.iconRes(group.iconKey)),
                        contentDescription = null,
                        tint = group.parsedColor(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .then(modifier),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("No group") },
                onClick = {
                    onGroupSelected(null)
                    expanded = false
                },
            )
            groups.forEach { group ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(IconCatalog.iconRes(group.iconKey)),
                            contentDescription = null,
                            tint = group.parsedColor(),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    text = { Text(group.name) },
                    onClick = {
                        onGroupSelected(group.id)
                        expanded = false
                    },
                )
            }
            if (showCreateGroup) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("+ New Group") },
                    onClick = {
                        expanded = false
                        onCreateGroup()
                    },
                )
            }
        }
    }
}

private fun Group.parsedColor(): Color = try {
    Color(android.graphics.Color.parseColor(color))
} catch (_: Exception) {
    Color(0xFF2196F3.toInt())
}
