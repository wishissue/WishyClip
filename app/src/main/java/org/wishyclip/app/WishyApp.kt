package org.wishyclip.app

import android.app.Application
import org.wishyclip.app.data.BitmapStore
import org.wishyclip.app.data.ProjectRepository
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.data.WishyDatabase

/** Application class acting as a tiny service locator (no DI framework, to keep the build simple). */
class WishyApp : Application() {
    val database: WishyDatabase by lazy { WishyDatabase.build(this) }
    val repository: ProjectRepository by lazy { ProjectRepository(database, BitmapStore(this)) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
