package org.wishyclip.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.wishyclip.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wishyclip.app.data.folder.FolderAccess
import org.wishyclip.app.data.folder.FolderEntry
import org.wishyclip.app.data.folder.FolderStatus
import org.wishyclip.app.data.folder.ProblemKind
import org.wishyclip.app.data.folder.SyncResult
import org.wishyclip.app.ui.HomeViewModel
import org.wishyclip.app.ui.design.WishyTheme

/** Opens Android's folder picker (it can create folders) and reports the chosen folder. */
@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(FolderAccess.suggestedStart()) }
}

/** Shows where projects are saved and lets the user change it. Used when creating a project and in Settings. */
@Composable
fun ProjectFolderCard(
    status: HomeViewModel.FolderStatusUi,
    rootUri: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    /** One short row (icon, status, button) for screens that must fit without scrolling. */
    compact: Boolean = false
) {
    val tokens = WishyTheme.tokens
    val ok = status == HomeViewModel.FolderStatusUi.READY
    if (compact) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.mediumRadius))
                .background(if (ok) tokens.primaryContainer else tokens.surfaceVariant)
                .padding(start = tokens.spaceMedium),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(org.wishyclip.app.ui.design.WishyIcons.Folder),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (ok) tokens.onPrimaryContainer else tokens.onSurfaceVariant),
                modifier = Modifier.size(24.dp)
            )
            Text(
                when (status) {
                    HomeViewModel.FolderStatusUi.READY ->
                        stringResource(R.string.pf_short_ready, FolderAccess.label(rootUri))
                    HomeViewModel.FolderStatusUi.LOST ->
                        stringResource(R.string.pf_short_lost, FolderAccess.label(rootUri))
                    HomeViewModel.FolderStatusUi.NOT_CHOSEN ->
                        stringResource(R.string.pf_short_not_chosen)
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onPick) {
                Text(stringResource(if (ok) R.string.pf_change else R.string.pf_choose))
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(if (ok) tokens.primaryContainer else tokens.surfaceVariant)
            .padding(tokens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
    ) {
        Text(
            stringResource(R.string.pf_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.onSurface
        )
        Text(
            when (status) {
                HomeViewModel.FolderStatusUi.READY ->
                    stringResource(R.string.pf_ready, FolderAccess.label(rootUri))
                HomeViewModel.FolderStatusUi.LOST ->
                    stringResource(R.string.pf_lost, FolderAccess.label(rootUri))
                HomeViewModel.FolderStatusUi.NOT_CHOSEN ->
                    stringResource(R.string.pf_not_chosen)
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.onSurfaceVariant
        )
        TextButton(onClick = onPick) {
            Text(stringResource(if (ok) R.string.pf_change else R.string.pf_choose))
        }
    }
}

/** Notices at the top of the project list: no folder yet, projects found in the folder, folders that need attention. */
@Composable
fun ProjectFolderBanner(
    vm: HomeViewModel,
    problems: Map<Long, SyncResult.Problem>,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    var showFound by remember { mutableStateOf(false) }
    val status = vm.folderStatus
    val found = vm.foundInFolder
    val shownProblems = problems.values.toList()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = tokens.spaceLarge), verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
        if (status != HomeViewModel.FolderStatusUi.READY) {
            ProjectFolderCard(status, vm.rootUri.value, onPick)
        }
        if (found.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.mediumRadius))
                    .background(tokens.primaryContainer).padding(start = tokens.spaceMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    pluralStringResource(R.plurals.pf_found, found.size, found.size),
                    style = MaterialTheme.typography.bodyMedium, color = tokens.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showFound = true }) { Text(stringResource(R.string.pf_review)) }
            }
        }
        for (p in shownProblems) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.mediumRadius))
                    .background(tokens.surfaceVariant).padding(tokens.spaceMedium)
            ) {
                Text(p.message, style = MaterialTheme.typography.bodySmall, color = tokens.onSurface)
                if (p.kind == ProblemKind.CONFLICT) {
                    Row {
                        TextButton(onClick = { vm.keepAppVersion(p.projectId) }) { Text(stringResource(R.string.pf_keep_app)) }
                        TextButton(onClick = { vm.useFolderVersion(p.projectId) }) { Text(stringResource(R.string.pf_use_folder)) }
                    }
                }
            }
        }
    }

    if (showFound) {
        WishyDialog(
            title = stringResource(R.string.pf_dialog_title),
            onDismissRequest = { showFound = false },
            confirmText = stringResource(R.string.pf_close),
            onConfirm = { showFound = false },
            dismissText = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                for (e in found) FoundRow(vm, e)
            }
        }
    }
}

@Composable
private fun FoundRow(vm: HomeViewModel, e: FolderEntry) {
    val tokens = WishyTheme.tokens
    Column {
        Text(e.projectName, fontWeight = FontWeight.SemiBold, color = tokens.onSurface)
        when (e.status) {
            FolderStatus.IMPORTABLE -> {
                Text(pluralStringResource(R.plurals.pf_frames, e.frames, e.frames), style = MaterialTheme.typography.bodySmall, color = tokens.onSurfaceVariant)
                Row {
                    TextButton(onClick = { vm.importFolder(e) }) { Text(stringResource(R.string.pf_import)) }
                    TextButton(onClick = { vm.importFolder(e, allowIncomplete = true) }) { Text(stringResource(R.string.pf_import_partial)) }
                }
            }
            FolderStatus.CHANGED_OUTSIDE -> {
                Text(stringResource(R.string.pf_newer), style = MaterialTheme.typography.bodySmall, color = tokens.onSurfaceVariant)
                TextButton(onClick = { vm.importFolder(e, replaceExisting = true) }) { Text(stringResource(R.string.pf_replace)) }
            }
            FolderStatus.TOO_NEW -> Text(
                stringResource(R.string.pf_too_new),
                style = MaterialTheme.typography.bodySmall, color = tokens.onSurfaceVariant
            )
            FolderStatus.DAMAGED -> Text(
                stringResource(R.string.pf_damaged),
                style = MaterialTheme.typography.bodySmall, color = tokens.onSurfaceVariant
            )
            FolderStatus.ALREADY_IMPORTED -> Unit
        }
    }
}
