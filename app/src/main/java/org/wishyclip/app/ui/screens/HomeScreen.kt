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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import org.wishyclip.app.R
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.ui.HomeViewModel
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ProjectCard
import org.wishyclip.app.ui.components.WishyDialog
import androidx.compose.ui.unit.sp
import org.wishyclip.app.ui.design.BagelFamily
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
    var projectPendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = tokens.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surface)
                    .statusBarsPadding()
                    .padding(start = tokens.spaceLarge, end = tokens.spaceSmall, top = tokens.spaceSmall, bottom = tokens.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Bagel Fat One wordmark (brand moment), with the page title in the interface font.
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontFamily = BagelFamily,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        color = tokens.primary
                    )
                    Text(
                        text = stringResource(R.string.ui_my_animations),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.onSurfaceVariant
                    )
                }
                ActionIconButton(
                    iconRes = WishyIcons.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    onClick = onOpenSettings
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = {
                    Text(
                        text = stringResource(R.string.ui_new_animation),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(WishyIcons.Add),
                        contentDescription = stringResource(R.string.title_new_project),
                        modifier = Modifier.size(28.dp)
                    )
                },
                onClick = onNewProject,
                containerColor = tokens.primary,
                contentColor = tokens.onPrimary,
                shape = RoundedCornerShape(tokens.mediumRadius),
                modifier = Modifier.height(56.dp)
            )
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
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(tokens.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(WishyIcons.Pencil),
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = tokens.primary
                            )
                        }
                        Spacer(Modifier.height(tokens.spaceSmall))
                        Text(
                            text = stringResource(R.string.ui_let_s_make_something),
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_tap_new_animation_to_start_your_first),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(
                        start = tokens.spaceLarge,
                        end = tokens.spaceLarge,
                        top = tokens.spaceSmall,
                        bottom = 112.dp // keep the last row clear of the New Animation button
                    ),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceLarge),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceLarge)
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
                    Text(stringResource(R.string.ui_rename_project))
                }
                TextButton(
                    onClick = {
                        // Destructive: ask first (the options dialog closes, the confirmation opens).
                        projectPendingDelete = project
                        selectedProjectForOptions = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_delete_project), color = tokens.danger)
                }
            }
        }
    }

    projectPendingDelete?.let { project ->
        WishyDialog(
            title = stringResource(R.string.ui_delete_animation),
            onDismissRequest = { projectPendingDelete = null },
            confirmText = stringResource(R.string.ui_delete),
            onConfirm = {
                vm.delete(project)
                projectPendingDelete = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { projectPendingDelete = null }
        ) {
            Text("\"${project.name}\" and all of its frames will be permanently deleted. This can't be undone.")
        }
    }

    if (showRenameDialog && selectedProjectForOptions != null) {
        val proj = selectedProjectForOptions!!
        WishyDialog(
            title = stringResource(R.string.ui_rename_project),
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
                label = { Text(stringResource(R.string.ui_project_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
