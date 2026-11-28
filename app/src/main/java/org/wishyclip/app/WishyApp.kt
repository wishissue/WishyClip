package org.wishyclip.app

import android.app.Application
import java.io.File
import org.wishyclip.app.brush.BrushStore
import org.wishyclip.app.data.BitmapStore
import org.wishyclip.app.data.BrushLibrary
import org.wishyclip.app.data.FontLibrary
import org.wishyclip.app.data.ProjectRepository
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.data.WishyDatabase
import org.wishyclip.app.data.folder.FolderAccess
import org.wishyclip.app.data.folder.ProjectFolderSync
import kotlinx.coroutines.flow.first
import org.wishyclip.app.data.folder.ProjectSyncScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application class acting as a tiny service locator (no DI framework, to keep the build simple). */
class WishyApp : Application() {
    val database: WishyDatabase by lazy { WishyDatabase.build(this) }
    val repository: ProjectRepository by lazy {
        ProjectRepository(database, BitmapStore(this)).also { r ->
            r.onProjectChanged = { id -> syncScheduler.request(id) }
        }
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val brushLibrary: BrushLibrary by lazy { BrushLibrary(BrushStore(File(filesDir, "brushes"))) }
    val fonts: FontLibrary by lazy { FontLibrary(this) }

    /** Lives as long as the process; a failure in one background job never takes the app down. */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> e.printStackTrace() }
    )

    /** Mirrors projects into the folder the user chose (see data/folder). */
    val folderSync: ProjectFolderSync by lazy {
        ProjectFolderSync(repository) {
            FolderAccess.open(this, settings.projectsRootUri.first())
        }
    }
    val syncScheduler: ProjectSyncScheduler by lazy { ProjectSyncScheduler(appScope, folderSync) }

    override fun onCreate() {
        super.onCreate()
        // Catch up on anything that was not written to the folder before the app last stopped.
        appScope.launch { syncScheduler.syncEverything() }
    }
}
