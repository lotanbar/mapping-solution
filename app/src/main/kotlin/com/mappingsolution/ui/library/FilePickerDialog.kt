package com.mappingsolution.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

/**
 * A filesystem-browsing dialog that lets the user pick a file matching any of [fileExtensions]
 * OR select the current folder via the "Use this folder" button.
 * Directories are navigable; matching files are selectable.
 * [onFolderSelected] is called when the user taps "Use this folder".
 */
@Composable
fun FilePickerDialog(
    initialPath: String,
    fileExtensions: List<String>,
    onFileSelected: (File) -> Unit,
    onFolderSelected: ((File) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val startDir = remember(initialPath) {
        val f = File(initialPath)
        when {
            f.isDirectory -> f
            f.parentFile?.isDirectory == true -> f.parentFile!!
            else -> File("/storage/emulated/0")
        }
    }
    var currentDir by remember { mutableStateOf(startDir) }

    val entries = remember(currentDir) {
        currentDir.listFiles()
            ?.filter { !it.name.startsWith(".") && (it.isDirectory || fileExtensions.any { ext -> it.name.endsWith(ext, ignoreCase = true) }) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                Text("Select file", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    currentDir.parentFile?.let { parent ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentDir = parent }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go up",
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("..")
                            }
                            HorizontalDivider()
                        }
                    }

                    when {
                        entries == null -> item {
                            Text(
                                "No access. Grant \"All files access\" in Settings first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        entries.isEmpty() -> item {
                            val extList = fileExtensions.joinToString(" / ") { "*$it" }
                            Text(
                                "No $extList files or folders here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        else -> items(entries) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDirectory) currentDir = entry
                                        else onFileSelected(entry)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (entry.isDirectory) HorizontalDivider()
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (onFolderSelected != null) {
                        TextButton(onClick = { onFolderSelected(currentDir) }) { Text("Use this folder") }
                    }
                }
            }
        }
    }
}
