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
    val syncProblems by vm.syncProblems.collectAsState()
    val pickFolder = org.wishyclip.app.ui.components.rememberFolderPicker { vm.chooseFolder(it) }
    // Access can change while the app is in the background (folder moved, permission revoked).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshFolder()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedProjectForOptions by remember { mutableStateOf<ProjectEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var projectPendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Failed create / delete / rename used to vanish silently (or crash); tell the user instead.
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(vm.error) {
        vm.error?.let {
            vm.clearError()
            snackbarHost.showSnackbar(it, duration = androidx.compose.material3.SnackbarDuration.Long)
        }
    }
    val removedMessage = vm.removedNotice?.let { stringResource(R.string.ui_removed_from_app, it.first) }
    val undoLabel = stringResource(R.string.ui_undo)
    androidx.compose.runtime.LaunchedEffect(vm.removedNotice) {
        if (removedMessage != null) {
            val r = snackbarHost.showSnackbar(removedMessage, actionLabel = undoLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restoreRemoved() else vm.clearRemovedNotice()
        }
    }

    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var showFolderGate by remember { mutableStateOf(false) }
    val visibleProjects = remember(projects, query, sortByName) {
        projects
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .let { list -> if (sortByName) list.sortedBy { it.name.lowercase() } else list }
    }

    Scaffold(
        containerColor = tokens.surface,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHost) },
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
                // Plain page title: no logo or wordmark anywhere in the interface.
                Text(
                    text = stringResource(R.string.ui_my_animations),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
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
                onClick = {
                    // A lost save folder means a new project would only exist inside the app: say so first.
                    if (vm.folderStatus == org.wishyclip.app.ui.HomeViewModel.FolderStatusUi.LOST) showFolderGate = true
                    else onNewProject()
                },
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
          Column(Modifier.fillMaxSize()) {
            org.wishyclip.app.ui.components.ProjectFolderBanner(
                vm = vm, problems = syncProblems, onPick = pickFolder
            )
            vm.working?.let { text ->
                Column(Modifier.fillMaxWidth().padding(horizontal = tokens.spaceLarge, vertical = tokens.spaceXs)) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(text, style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                }
            }
            if (projects.size >= 4) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = tokens.spaceLarge, vertical = tokens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ui_search_projects)) },
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = !sortByName,
                        onClick = { sortByName = false },
                        label = { Text(stringResource(R.string.ui_sort_recent)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = sortByName,
                        onClick = { sortByName = true },
                        label = { Text(stringResource(R.string.ui_sort_name)) }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
            } else if (visibleProjects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.ui_no_matching_projects, query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.onSurfaceVariant
                    )
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
                    items(visibleProjects, key = { it.id }) { project ->
                        val thumb = vm.thumbFile(project.id)
                        ProjectCard(
                            title = project.name,
                            info = "${project.width}x${project.height} • ${project.fps} FPS",
                            thumbnailFile = thumb,
                            badge = when {
                                vm.folderStatus != org.wishyclip.app.ui.HomeViewModel.FolderStatusUi.READY -> org.wishyclip.app.ui.components.ProjectBadge.NONE
                                syncProblems.containsKey(project.id) -> org.wishyclip.app.ui.components.ProjectBadge.PROBLEM
                                project.folderName == null -> org.wishyclip.app.ui.components.ProjectBadge.PENDING
                                else -> org.wishyclip.app.ui.components.ProjectBadge.SAVED
                            },
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
        }
    }

    if (showFolderGate) {
        WishyDialog(
            title = stringResource(R.string.pf_gate_title),
            onDismissRequest = { showFolderGate = false },
            confirmText = stringResource(R.string.pf_choose),
            onConfirm = { showFolderGate = false; pickFolder() },
            dismissText = stringResource(R.string.pf_gate_continue),
            onDismiss = { showFolderGate = false; onNewProject() }
        ) {
            Text(stringResource(R.string.pf_gate_body))
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
        val inFolder = project.folderName != null && vm.folderStatus == org.wishyclip.app.ui.HomeViewModel.FolderStatusUi.READY
        WishyDialog(
            title = stringResource(R.string.ui_delete_animation),
            onDismissRequest = { projectPendingDelete = null },
            confirmText = if (inFolder) stringResource(R.string.ui_remove_from_app) else stringResource(R.string.ui_delete),
            destructive = !inFolder,
            onConfirm = {
                vm.delete(project, deleteFolder = false)
                projectPendingDelete = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { projectPendingDelete = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                if (inFolder) {
                    Text(stringResource(R.string.ui_remove_from_app_body, project.name, project.folderName ?: ""))
                    TextButton(onClick = {
                        vm.delete(project, deleteFolder = true)
                        projectPendingDelete = null
                    }) { Text(stringResource(R.string.ui_delete_folder_too), color = tokens.danger) }
                } else {
                    Text(stringResource(R.string.ui_delete_project_body, project.name))
                }
            }
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
