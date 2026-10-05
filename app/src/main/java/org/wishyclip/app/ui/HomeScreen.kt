package org.wishyclip.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.data.ProjectEntity
import org.wishyclip.app.model.Presets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenProject: (Long) -> Unit, vm: HomeViewModel = viewModel()) {
    val projects by vm.projects.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wishy Clip") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showNew = true }) {
                Image(painterResource(R.drawable.ic_add), null, Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("New project")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (projects.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(R.drawable.ic_logo), null, Modifier.size(120.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "No projects yet. Tap \"New project\" to start animating!",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { p ->
                        ProjectCard(
                            project = p,
                            thumbnail = remember(p.id, p.updatedAt) { vm.thumbFile(p.id) },
                            onOpen = { onOpenProject(p.id) },
                            onDelete = { toDelete = p }
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        NewProjectDialog(
            onDismiss = { showNew = false },
            onCreate = { name, w, h, fps ->
                showNew = false
                vm.create(name, w, h, fps) { id -> onOpenProject(id) }
            }
        )
    }

    val target = toDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete project?") },
            text = { Text("\"${target.name}\" and all its frames will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectEntity,
    thumbnail: java.io.File,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(project.width.toFloat() / project.height.toFloat())
                    .background(Color.White)
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(R.drawable.ic_logo)
                )
            }
            Text(project.name, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Text(
                "${project.width}x${project.height} | ${project.fps} fps",
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("My animation") }
    var preset by remember { mutableIntStateOf(0) }
    var fps by remember { mutableFloatStateOf(Presets.DEFAULT_FPS.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                Text("Canvas size")
                Presets.canvasSizes.forEachIndexed { i, p ->
                    if (i == preset) {
                        Button(onClick = { preset = i }, modifier = Modifier.fillMaxWidth()) { Text(p.label) }
                    } else {
                        OutlinedButton(onClick = { preset = i }, modifier = Modifier.fillMaxWidth()) { Text(p.label) }
                    }
                }
                Text("Frame rate: ${fps.roundToInt()} fps")
                Slider(value = fps, onValueChange = { fps = it }, valueRange = 1f..30f)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Presets.canvasSizes[preset]
                val finalName = name.trim().ifEmpty { "Untitled" }
                onCreate(finalName, p.width, p.height, fps.roundToInt())
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
