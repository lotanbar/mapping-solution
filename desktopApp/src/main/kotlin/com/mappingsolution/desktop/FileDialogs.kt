package com.mappingsolution.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** Native file dialogs; each returns null when the user cancels. */
internal object FileDialogs {

    fun openFile(parent: Frame?, title: String, extensions: Set<String>): File? {
        val dialog = FileDialog(parent, title, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
            // Windows ignores FilenameFilter; a wildcard pattern filters there instead.
            file = extensions.joinToString(";") { "*.$it" }
            isVisible = true
        }
        return dialog.files.firstOrNull()
    }

    fun chooseFolder(parent: Frame?, title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun saveFile(parent: Frame?, title: String, suggestedName: String): File? {
        val dialog = FileDialog(parent, title, FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        return dialog.files.firstOrNull()
    }
}
