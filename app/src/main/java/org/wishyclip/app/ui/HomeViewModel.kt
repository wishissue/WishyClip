package org.wishyclip.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wishyclip.app.WishyApp
import org.wishyclip.app.data.folder.FolderAccess
import org.wishyclip.app.data.folder.FolderEntry
import org.wishyclip.app.data.folder.FolderStatus
import org.wishyclip.app.data.folder.ImportOutcome
import org.wishyclip.app.data.folder.SyncResult
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.model.ProjectLimits

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val wishy = app as WishyApp
    private val repo = wishy.repository
    private val settings = wishy.settings
    private val folderSync = wishy.folderSync

    /** Where the user keeps their projects. */
    enum class FolderStatusUi { NOT_CHOSEN, LOST, READY }

    val rootUri: StateFlow<String?> =
        settings.projectsRootUri.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var folderStatus by mutableStateOf(FolderStatusUi.NOT_CHOSEN)
        private set

    /** Folders in the project folder that are not (or no longer) in the app, or that changed outside it. */
    /** Short description of a running folder job ("Importing…"), null when idle; drives a progress bar. */
    var working by mutableStateOf<String?>(null)
        private set

    /** Set when a project was removed from the app but its folder was kept, so the screen can offer "Undo". */
    var removedNotice by mutableStateOf<Pair<String, String>?>(null) // project name to folder name
        private set

    fun clearRemovedNotice() { removedNotice = null }

    /** Undo for "Remove from app": imports the kept folder again. */
    fun restoreRemoved() {
        val folder = removedNotice?.second ?: return
        removedNotice = null
        viewModelScope.launch {
            val entry = folderSync.scan()?.firstOrNull { it.dirName == folder } ?: return@launch
            importFolder(entry)
        }
    }

    var foundInFolder by mutableStateOf<List<FolderEntry>>(emptyList())
        private set

    /** Projects the folder could not be updated for (conflict, newer version, unreadable). */
    val syncProblems: StateFlow<Map<Long, SyncResult.Problem>> = wishy.syncScheduler.problems

    val projects: StateFlow<List<ProjectEntity>> =
        repo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One-line, user-readable problem from the last failed action; the screen shows it and calls [clearError]. */
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() { error = null }

    init {
        refreshFolder()
        // Removes folders left behind if the app was killed in the middle of deleting a project.
        viewModelScope.launch {
            try {
                repo.purgeOrphanedProjectDirs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Housekeeping only: a failure must never affect the project list.
            }
        }
    }

    fun create(name: String, width: Int, height: Int, fps: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val size = ProjectLimits.fit(width, height)
                val id = repo.createProject(
                    ProjectLimits.cleanName(name), size.width, size.height, ProjectLimits.cleanFps(fps)
                )
                onCreated(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Could not create the project. Check that your device has free storage."
            }
        }
    }

    /** Re-checks access to the project folder and lists what it contains. */
    fun refreshFolder() {
        viewModelScope.launch {
            try {
                folderStatus = when (folderSync.rootState()) {
                    is FolderAccess.RootState.Ready -> FolderStatusUi.READY
                    FolderAccess.RootState.Lost -> FolderStatusUi.LOST
                    FolderAccess.RootState.NotConfigured -> FolderStatusUi.NOT_CHOSEN
                }
                foundInFolder = if (folderStatus == FolderStatusUi.READY) {
                    folderSync.scan().orEmpty().filter { it.status != FolderStatus.ALREADY_IMPORTED }
                } else emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Listing is a convenience; the project list itself must keep working.
            }
        }
    }

    /** Called with the folder the user picked. Keeps access across restarts and writes every project into it. */
    fun chooseFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val old = rootUri.value
                working = app.getString(org.wishyclip.app.R.string.ui_working_folder)
                FolderAccess.persist(app, uri)
                if (old != null && old != uri.toString()) FolderAccess.release(app, android.net.Uri.parse(old))
                settings.saveProjectsRootUri(uri.toString())
                // Projects keep their old folder names only inside the old root; start fresh in the new one.
                repo.allProjects().forEach { repo.setFolderName(it.id, null) }
                refreshFolder()
                wishy.syncScheduler.syncEverything()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Could not use that folder: ${e.message ?: "access was refused"}"
            } finally {
                working = null
            }
        }
    }

    fun importFolder(entry: FolderEntry, replaceExisting: Boolean = false, allowIncomplete: Boolean = false) {
        viewModelScope.launch {
            working = getApplication<Application>().getString(org.wishyclip.app.R.string.ui_working_import, entry.projectName)
            try { importFolderNow(entry, replaceExisting, allowIncomplete) } finally { working = null }
        }
    }

    private suspend fun importFolderNow(entry: FolderEntry, replaceExisting: Boolean, allowIncomplete: Boolean) {
        run {
            error = when (val r = folderSync.importProject(entry.dirName, replaceExisting, allowIncomplete)) {
                is ImportOutcome.Imported -> null
                is ImportOutcome.AlreadyHere -> "\"${entry.projectName}\" is already in the app."
                is ImportOutcome.Incomplete -> "\"${entry.projectName}\" is missing ${r.missing} file(s). " +
                    "Use \"Import what can be read\" to open a repaired copy."
                is ImportOutcome.NoFolder -> "The project folder is not available."
                is ImportOutcome.TooLarge -> "\"${entry.projectName}\" is ${r.width}x${r.height}, larger than this version supports."
                is ImportOutcome.TooNew -> "\"${entry.projectName}\" was saved by a newer WishyClip. Update the app to open it."
                is ImportOutcome.Failed -> "Could not import: ${r.message}"
            }
            refreshFolder()
        }
    }

    /** The user chose to keep the app's version of a project whose folder changed elsewhere. */
    fun keepAppVersion(projectId: Long) {
        viewModelScope.launch { wishy.syncScheduler.resolveKeepApp(projectId) }
    }

    /** The user chose the folder's version: replaces the app's copy. */
    fun useFolderVersion(projectId: Long) {
        viewModelScope.launch {
            val p = repo.getProject(projectId) ?: return@launch
            val name = p.folderName ?: return@launch
            val entry = folderSync.scan()?.firstOrNull { it.dirName == name } ?: return@launch
            importFolder(entry, replaceExisting = true)
        }
    }

    /**
     * [deleteFolder] = also remove the project's folder (cannot be undone). When false the project only leaves
     * the app and can be imported again from the folder.
     */
    fun delete(project: ProjectEntity, deleteFolder: Boolean = false) {
        // An export or import reads the project's files; deleting them underneath it would corrupt the result.
        if (org.wishyclip.app.data.BusyTracker.state.value != null) {
            error = "Wait for the running import or export to finish before deleting a project."
            return
        }
        viewModelScope.launch {
            try {
                if (deleteFolder && !folderSync.deleteFolder(project.folderName)) {
                    error = "Could not delete the project's folder; the project was kept."
                    return@launch
                }
                val keptFolder = !deleteFolder && project.folderName != null &&
                    folderStatus == FolderStatusUi.READY
                repo.deleteProject(project.id)
                if (keptFolder) removedNotice = project.name to project.folderName!!
                refreshFolder()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Could not delete the project."
            }
        }
    }

    fun rename(projectId: Long, newName: String) {
        viewModelScope.launch {
            try {
                val p = repo.getProject(projectId) ?: return@launch
                repo.updateProject(p.copy(name = ProjectLimits.cleanName(newName)))
                wishy.syncScheduler.request(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Could not rename the project."
            }
        }
    }

    fun thumbFile(projectId: Long): File = repo.store.thumbFile(projectId)
}
