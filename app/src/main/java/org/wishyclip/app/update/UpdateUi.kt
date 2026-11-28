package org.wishyclip.app.update

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import org.wishyclip.app.R
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.design.WishyTheme

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** One update flow for the whole app, shared by the start screen prompt and Settings. */
@Composable
fun activityUpdateViewModel(): UpdateViewModel {
    val activity = LocalContext.current.findActivity()
    return if (activity != null) viewModel(viewModelStoreOwner = activity) else viewModel()
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))

/**
 * The update dialogs. Shown on the start screen only, so an update never interrupts drawing.
 * Every step is the user's choice: nothing downloads until "Update" is pressed and Android itself
 * asks for the final install confirmation.
 */
@Composable
fun UpdatePrompt(vm: UpdateViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // Coming back from Android's "install unknown apps" page: continue by itself if it was allowed.
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermission()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val s = vm.state
    // The system installer opens by itself once per download; "Install" below reopens it if it was dismissed.
    LaunchedEffect(s) {
        if (s is UpdateState.Ready && !vm.installLaunched && !vm.promptHidden) {
            vm.markInstallLaunched()
            vm.installIntent()?.let { runCatching { context.startActivity(it) } }
        }
    }

    when {
        s is UpdateState.Available && !vm.promptHidden -> WishyDialog(
            title = stringResource(R.string.upd_title),
            onDismissRequest = vm::later,
            confirmText = stringResource(R.string.upd_update),
            onConfirm = vm::startDownload,
            dismissText = stringResource(R.string.upd_later),
            onDismiss = vm::later
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.upd_available, s.info.version, vm.currentVersion))
                if (s.info.apkSize > 0) {
                    Text(
                        stringResource(R.string.upd_size, megabytes(s.info.apkSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = WishyTheme.tokens.onSurfaceVariant
                    )
                }
                if (s.info.notes.isNotBlank()) {
                    Text(stringResource(R.string.upd_whats_new), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        Text(s.info.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    stringResource(R.string.upd_projects_safe),
                    style = MaterialTheme.typography.bodySmall,
                    color = WishyTheme.tokens.onSurfaceVariant
                )
            }
        }

        s is UpdateState.Downloading -> WishyDialog(
            title = stringResource(R.string.upd_downloading),
            onDismissRequest = {},
            confirmText = stringResource(R.string.action_cancel),
            onConfirm = vm::cancelDownload,
            dismissText = null,
            onDismiss = null
        ) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.total > 0) {
                    LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.upd_progress, megabytes(s.done), megabytes(s.total)), style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        s is UpdateState.Ready && !vm.promptHidden -> WishyDialog(
            title = stringResource(R.string.upd_ready_title),
            onDismissRequest = vm::later,
            confirmText = stringResource(R.string.upd_install),
            onConfirm = { vm.installIntent()?.let { runCatching { context.startActivity(it) } } },
            dismissText = stringResource(R.string.upd_later),
            onDismiss = vm::later
        ) { Text(stringResource(R.string.upd_ready_body)) }

        s is UpdateState.NeedsPermission && !vm.promptHidden -> WishyDialog(
            title = stringResource(R.string.upd_permission_title),
            onDismissRequest = vm::later,
            confirmText = stringResource(R.string.upd_open_settings),
            onConfirm = { runCatching { context.startActivity(vm.permissionIntent()) } },
            dismissText = stringResource(R.string.upd_later),
            onDismiss = vm::later
        ) { Text(stringResource(R.string.upd_permission_body)) }

        s is UpdateState.KeyMismatch -> WishyDialog(
            title = stringResource(R.string.upd_key_title),
            onDismissRequest = vm::dismissMessage,
            confirmText = stringResource(R.string.upd_open_page),
            onConfirm = {
                vm.dismissMessage()
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.info.pageUrl))
                    )
                }
            },
            dismissText = stringResource(R.string.ui_close),
            onDismiss = vm::dismissMessage
        ) { Text(stringResource(R.string.upd_key_body)) }

        s is UpdateState.Failed && s.info != null && !vm.promptHidden -> WishyDialog(
            title = stringResource(R.string.upd_failed_title),
            onDismissRequest = vm::later,
            confirmText = stringResource(R.string.upd_retry),
            onConfirm = vm::startDownload,
            dismissText = stringResource(R.string.ui_close),
            onDismiss = vm::later
        ) { Text(s.message) }
    }
}

/** The "Updates" block in Settings → About: version, the check-at-start switch and a manual check. */
@Composable
fun UpdateStatusRow(vm: UpdateViewModel, modifier: Modifier = Modifier) {
    val s = vm.state
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.upd_current, vm.currentVersion), style = MaterialTheme.typography.bodyMedium)
        if (!vm.supported) {
            Text(
                stringResource(R.string.upd_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = WishyTheme.tokens.onSurfaceVariant
            )
            return
        }
        val status = when (s) {
            UpdateState.Checking -> stringResource(R.string.upd_checking)
            UpdateState.UpToDate -> stringResource(R.string.upd_up_to_date)
            is UpdateState.Available -> stringResource(R.string.upd_available, s.info.version, vm.currentVersion)
            is UpdateState.Failed -> s.message
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = WishyTheme.tokens.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s is UpdateState.Available) {
                Button(onClick = vm::startDownload) { Text(stringResource(R.string.upd_update)) }
            }
            TextButton(onClick = vm::checkNow, enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading) {
                Text(stringResource(R.string.upd_check_now))
            }
        }
    }
}
