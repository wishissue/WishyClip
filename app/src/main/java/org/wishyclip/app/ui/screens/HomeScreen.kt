package org.wishyclip.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.ui.HomeViewModel
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ProjectCard
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenProject: (Long) -> Unit,
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val projects by vm.projects.collectAsState()

    var selectedProjectForOptions by remember { mutableStateOf<ProjectEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = tokens.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surface)
                    .statusBarsPadding()
                    .padding(start = tokens.spaceLarge, end = tokens.spaceXs, top = tokens.spaceXs, bottom = tokens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.onSurface
                )
                ActionIconButton(
                    iconRes = WishyIcons.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    onClick = onOpenSettings
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProject,
                containerColor = tokens.primary,
                contentColor = tokens.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(WishyIcons.Add),
                    contentDescription = stringResource(R.string.title_new_project),
                    modifier = Modifier.size(28.dp),
                    tint = tokens.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = tokens.surface
        ) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                    ) {
                        Icon(
                            painter = painterResource(WishyIcons.Pencil),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = tokens.onSurfaceVariant
                        )
                        Text(
                            text = "No projects yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.onSurface
                        )
                        Text(
                            text = "Tap + to start a new animation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = tokens.spaceLarge,
                        end = tokens.spaceLarge,
                        top = tokens.spaceSmall,
                        bottom = 96.dp // keep the last row clear of the + button
                    ),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceMedium),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceMedium)
                ) {
                    items(projects, key = { it.id }) { project ->
                        val thumb = vm.thumbFile(project.id)
                        ProjectCard(
                            title = project.name,
                            info = "${project.width}x${project.height} • ${project.fps} FPS",
                            thumbnailFile = thumb,
                            onClick = { onOpenProject(project.id) },
                            onLongClick = {
                                selectedProjectForOptions = project
                            }
                        )
                    }
                }
            }
        }
    }

    selectedProjectForOptions?.let { project ->
        WishyDialog(
            title = project.name,
            onDismissRequest = { selectedProjectForOptions = null },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { selectedProjectForOptions = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                TextButton(
                    onClick = {
                        renameText = project.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rename Project")
                }
                TextButton(
                    onClick = {
                        vm.delete(project)
                        selectedProjectForOptions = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Project", color = tokens.danger)
                }
            }
        }
    }

    if (showRenameDialog && selectedProjectForOptions != null) {
        val proj = selectedProjectForOptions!!
        WishyDialog(
            title = "Rename Project",
            onDismissRequest = { showRenameDialog = false },
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                if (renameText.isNotBlank()) {
                    vm.rename(proj.id, renameText.trim())
                }
                showRenameDialog = false
                selectedProjectForOptions = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showRenameDialog = false }
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("Project Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
