package com.qzone.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.net.URLConnection

data class ExportProgressSnapshot(
    val totalFiles: Int,
    val completedFiles: Int,
    val currentFile: String,
)

suspend fun exportWorkingTree(
    context: Context,
    sourceRoot: File,
    targetTreeUri: Uri,
    qq: String,
    onProgress: suspend (ExportProgressSnapshot) -> Unit,
) {
    if (!sourceRoot.exists() || !sourceRoot.isDirectory) {
        throw IllegalStateException("The staged download directory does not exist.")
    }

    val sourceFiles = sourceRoot.walkTopDown().filter { it.isFile }.toList()
    if (sourceFiles.isEmpty()) {
        throw IllegalStateException("There are no downloaded files to export.")
    }

    val targetRoot = DocumentFile.fromTreeUri(context, targetTreeUri)
        ?: throw IllegalStateException("The selected export folder is no longer available.")
    val qqDir = findOrCreateDirectory(targetRoot, qq)
    val albumRoot = findOrCreateDirectory(qqDir, "album")

    sourceFiles.forEachIndexed { index, sourceFile ->
        val relativePath = sourceFile.relativeTo(sourceRoot).invariantSeparatorsPath
        val pathSegments = relativePath.split('/').filter { it.isNotBlank() }
        if (pathSegments.isEmpty()) {
            return@forEachIndexed
        }

        val fileName = pathSegments.last()
        var parentDir = albumRoot
        pathSegments.dropLast(1).forEach { segment ->
            parentDir = findOrCreateDirectory(parentDir, segment)
        }

        val targetFile = findOrCreateFile(parentDir, fileName, sourceFile)
        if (targetFile.length() != sourceFile.length()) {
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Failed to open the target export file: $fileName")
        }

        onProgress(
            ExportProgressSnapshot(
                totalFiles = sourceFiles.size,
                completedFiles = index + 1,
                currentFile = relativePath,
            ),
        )
    }

    sourceRoot.deleteRecursively()
}

private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile {
    parent.findFile(name)?.let { existing ->
        if (!existing.isDirectory) {
            throw IllegalStateException("The export destination already has a file named $name.")
        }
        return existing
    }

    return parent.createDirectory(name)
        ?: throw IllegalStateException("Failed to create the destination folder: $name")
}

private fun findOrCreateFile(parent: DocumentFile, fileName: String, sourceFile: File): DocumentFile {
    parent.findFile(fileName)?.let { existing ->
        if (existing.isDirectory) {
            throw IllegalStateException("The export destination already has a folder named $fileName.")
        }
        if (existing.length() == sourceFile.length()) {
            return existing
        }
        existing.delete()
    }

    val mimeType = URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
    return parent.createFile(mimeType, fileName)
        ?: throw IllegalStateException("Failed to create the destination file: $fileName")
}
