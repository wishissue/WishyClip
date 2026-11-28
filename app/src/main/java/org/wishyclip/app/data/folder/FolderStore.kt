package org.wishyclip.app.data.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One directory of a project folder, independent of how it is stored.
 *
 * Production uses [SafFolderStore] (a folder the user picked with Android's folder picker, which survives
 * uninstalling the app); tests use [FileFolderStore]. All methods are blocking: call from Dispatchers.IO.
 * Names are plain file names, never paths: anything containing '/' or ".." is rejected.
 */
interface FolderStore {
    /** Plain files in this directory: name -> size in bytes. */
    fun listFiles(): Map<String, Long>

    fun listDirs(): List<String>

    /** null when the file does not exist. The caller closes the stream. */
    fun openRead(name: String): InputStream?

    /** Creates [name], replacing an existing file. Throws [IOException] when it cannot be written. */
    fun writeFile(name: String, write: (OutputStream) -> Unit)

    /** Deletes a file or directory (recursively). Returns true when it is gone afterwards. */
    fun delete(name: String): Boolean

    fun rename(from: String, to: String): Boolean

    /** A sub-directory, created when [create] is set. null when missing (or not creatable). */
    fun dir(name: String, create: Boolean): FolderStore?
}

internal fun requirePlainName(name: String) {
    if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name.contains("..")) {
        throw IOException("Unsafe file name: $name")
    }
}

/** [FolderStore] on ordinary files. Used by the tests and usable for an app-specific directory. */
class FileFolderStore(private val directory: File) : FolderStore {

    override fun listFiles(): Map<String, Long> =
        directory.listFiles()?.filter { it.isFile }?.associate { it.name to it.length() } ?: emptyMap()

    override fun listDirs(): List<String> =
        directory.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    override fun openRead(name: String): InputStream? {
        requirePlainName(name)
        val f = File(directory, name)
        return if (f.isFile) f.inputStream() else null
    }

    override fun writeFile(name: String, write: (OutputStream) -> Unit) {
        requirePlainName(name)
        directory.mkdirs()
        FileOutputStream(File(directory, name)).use { out ->
            write(out)
            out.flush()
            out.fd.sync()
        }
    }

    override fun delete(name: String): Boolean {
        requirePlainName(name)
        val f = File(directory, name)
        return !f.exists() || f.deleteRecursively()
    }

    override fun rename(from: String, to: String): Boolean {
        requirePlainName(from); requirePlainName(to)
        val target = File(directory, to)
        return !target.exists() && File(directory, from).renameTo(target)
    }

    override fun dir(name: String, create: Boolean): FolderStore? {
        requirePlainName(name)
        val d = File(directory, name)
        if (!d.isDirectory) {
            if (!create || d.exists() || !d.mkdirs()) return null
        }
        return FileFolderStore(d)
    }
}

/** [FolderStore] on a document tree chosen by the user (Storage Access Framework). */
class SafFolderStore(private val context: Context, private val doc: DocumentFile) : FolderStore {

    private fun find(name: String): DocumentFile? {
        requirePlainName(name)
        return doc.listFiles().firstOrNull { it.name == name }
    }

    override fun listFiles(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (f in doc.listFiles()) {
            val n = f.name ?: continue
            if (f.isFile) out[n] = f.length()
        }
        return out
    }

    override fun listDirs(): List<String> =
        doc.listFiles().filter { it.isDirectory }.mapNotNull { it.name }

    override fun openRead(name: String): InputStream? {
        val f = find(name) ?: return null
        if (!f.isFile) return null
        return context.contentResolver.openInputStream(f.uri)
    }

    override fun writeFile(name: String, write: (OutputStream) -> Unit) {
        requirePlainName(name)
        // createFile never overwrites: it silently picks "name (1)" instead, so remove the old file first.
        doc.listFiles().filter { it.name == name }.forEach { it.delete() }
        val created = doc.createFile(mimeFor(name), name) ?: throw IOException("Could not create $name")
        if (created.name != name) {
            // The provider changed the name (for example by appending an extension): refuse, never guess.
            created.delete()
            throw IOException("The folder provider renamed $name to ${created.name}")
        }
        val out = context.contentResolver.openOutputStream(created.uri, "wt")
            ?: throw IOException("Could not open $name for writing")
        out.use {
            write(it)
            it.flush()
        }
    }

    override fun delete(name: String): Boolean {
        requirePlainName(name)
        val matches = doc.listFiles().filter { it.name == name }
        return matches.all { it.delete() }
    }

    override fun rename(from: String, to: String): Boolean {
        requirePlainName(from); requirePlainName(to)
        if (find(to) != null) return false
        val f = find(from) ?: return false
        return f.renameTo(to) && doc.listFiles().any { it.name == to }
    }

    override fun dir(name: String, create: Boolean): FolderStore? {
        requirePlainName(name)
        val existing = doc.listFiles().firstOrNull { it.name == name }
        if (existing != null) return if (existing.isDirectory) SafFolderStore(context, existing) else null
        if (!create) return null
        val made = doc.createDirectory(name) ?: return null
        return if (made.name == name) SafFolderStore(context, made) else null
    }

    private fun mimeFor(name: String): String = when {
        // Only types whose extension matches are declared: with a mismatching mime type some providers
        // append an extension ("x.tmp.json"), which would break the exact names the format relies on.
        name.endsWith(".png") -> "image/png"
        name.endsWith(".json") -> "application/json"
        else -> "application/octet-stream"
    }
}

/** Helpers for the folder the user chose for their projects. */
object FolderAccess {

    /** Keeps access to [uri] across restarts. Without this the permission ends when the app closes. */
    fun persist(context: Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    fun release(context: Context, uri: Uri) {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Already released or never held.
        }
    }

    sealed interface RootState {
        object NotConfigured : RootState
        /** A folder was chosen once but access is gone (app reinstalled, permission revoked, folder deleted). */
        object Lost : RootState
        class Ready(val store: FolderStore) : RootState
    }

    fun open(context: Context, uriString: String?): RootState {
        if (uriString.isNullOrBlank()) return RootState.NotConfigured
        return try {
            val uri = Uri.parse(uriString)
            val granted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (!granted) return RootState.Lost
            val doc = DocumentFile.fromTreeUri(context, uri) ?: return RootState.Lost
            if (!doc.exists() || !doc.isDirectory || !doc.canRead() || !doc.canWrite()) RootState.Lost
            else RootState.Ready(SafFolderStore(context, doc))
        } catch (e: Exception) {
            RootState.Lost
        }
    }

    /** "Documents/WishyClip" for a tree URI like .../tree/primary%3ADocuments%2FWishyClip. */
    fun label(uriString: String?): String {
        if (uriString.isNullOrBlank()) return ""
        val seg = try { Uri.parse(uriString).lastPathSegment ?: uriString } catch (_: Exception) { uriString }
        return seg.substringAfter(':', seg).ifEmpty { "Internal storage" }
    }

    /** Where the folder picker should open first. */
    fun suggestedStart(): Uri =
        android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
}
