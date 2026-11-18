package com.example.geminidevagent.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class WorkspaceFile(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

class WorkspaceRepository(context: Context) {

    val rootDir: File = File(context.filesDir, "dev_workspace").apply { mkdirs() }

    private val _files = MutableStateFlow<List<WorkspaceFile>>(scanFiles())
    val files: StateFlow<List<WorkspaceFile>> = _files.asStateFlow()

    fun resolveSafe(relativePath: String): File {
        val cleaned = relativePath.trim().removePrefix("/")
        val target = File(rootDir, cleaned).canonicalFile
        require(target.path.startsWith(rootDir.canonicalPath)) { "Path escapes workspace." }
        return target
    }

    fun writeFile(relativePath: String, content: String): WorkspaceFile {
        val target = resolveSafe(relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        refresh()
        return WorkspaceFile(
            relativePath = target.relativeTo(rootDir).path,
            sizeBytes = target.length(),
            lastModifiedMillis = target.lastModified()
        )
    }

    fun writeBytes(relativePath: String, bytes: ByteArray): WorkspaceFile {
        val target = resolveSafe(relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        refresh()
        return WorkspaceFile(
            relativePath = target.relativeTo(rootDir).path,
            sizeBytes = target.length(),
            lastModifiedMillis = target.lastModified()
        )
    }

    fun readFile(relativePath: String): String {
        val target = resolveSafe(relativePath)
        require(target.exists() && target.isFile) { "File not found: $relativePath" }
        return target.readText(Charsets.UTF_8)
    }

    fun deleteFile(relativePath: String): Boolean {
        val target = resolveSafe(relativePath)
        val ok = target.exists() && target.delete()
        refresh()
        return ok
    }

    fun clearAll() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
        refresh()
    }

    fun refresh() {
        _files.value = scanFiles()
    }

    private fun scanFiles(): List<WorkspaceFile> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.walkTopDown()
            .filter { it.isFile }
            .map { f ->
                WorkspaceFile(
                    relativePath = f.relativeTo(rootDir).path,
                    sizeBytes = f.length(),
                    lastModifiedMillis = f.lastModified()
                )
            }
            .sortedByDescending { it.lastModifiedMillis }
            .toList()
    }
}
