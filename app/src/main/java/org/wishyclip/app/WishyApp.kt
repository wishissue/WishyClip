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

/** Application class acting as a tiny service locator (no DI framework, to keep the build simple). */
class WishyApp : Application() {
    val database: WishyDatabase by lazy { WishyDatabase.build(this) }
    val repository: ProjectRepository by lazy { ProjectRepository(database, BitmapStore(this)) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val brushLibrary: BrushLibrary by lazy { BrushLibrary(BrushStore(File(filesDir, "brushes"))) }
    val fonts: FontLibrary by lazy { FontLibrary(this) }
}
