package org.wishyclip.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wishyclip.app.WishyApp
import org.wishyclip.app.data.ProjectEntity

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WishyApp).repository

    val projects: StateFlow<List<ProjectEntity>> =
        repo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, width: Int, height: Int, fps: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createProject(name, width, height, fps)
            onCreated(id)
        }
    }

    fun delete(project: ProjectEntity) {
        viewModelScope.launch { repo.deleteProject(project.id) }
    }

    fun rename(projectId: Long, newName: String) {
        viewModelScope.launch {
            val p = repo.getProject(projectId) ?: return@launch
            repo.updateProject(p.copy(name = newName))
        }
    }

    fun thumbFile(projectId: Long): File = repo.store.thumbFile(projectId)
}
