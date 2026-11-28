package org.wishyclip.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.wishyclip.app.R
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.WishySlider
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

enum class AspectPreset(
    @androidx.annotation.StringRes val labelRes: Int,
    @androidx.annotation.StringRes val ratioRes: Int,
    val width: Int,
    val height: Int
) {
    SQUARE(R.string.ui_preset_square, R.string.ui_ratio_square, 1080, 1080),
    LANDSCAPE(R.string.ui_preset_landscape, R.string.ui_ratio_landscape, 1920, 1080),
    PORTRAIT(R.string.ui_preset_portrait, R.string.ui_ratio_portrait, 1080, 1920),
    CUSTOM(R.string.ui_preset_custom, R.string.ui_ratio_custom, 1080, 1080)
}

/** Smallest and largest canvas side in pixels (4096 x 4096 is already 64 MB per painted layer). */
private const val MIN_CANVAS_SIDE = 64
private const val MAX_CANVAS_SIDE = 4096

/**
 * Everything needed to start a project is on one screen, with no scrolling on a normal phone:
 * a live preview of the canvas shape and speed, the size presets, the name, the frame rate and the
 * save location. Wide or landscape windows put the preview on the left and the controls on the right.
 */
@Composable
fun NewProjectScreen(
    onCreateProject: (name: String, width: Int, height: Int, fps: Int) -> Unit,
    onBack: () -> Unit,
    /** Save-location card shown above the Create button; null hides it (previews). */
    folderCard: (@Composable () -> Unit)? = null
) {
    val tokens = WishyTheme.tokens
    var name by remember { mutableStateOf("New Project") }
    var preset by remember { mutableStateOf(AspectPreset.LANDSCAPE) }
    var widthText by remember { mutableStateOf("1920") }
    var heightText by remember { mutableStateOf("1080") }
    var fps by remember { mutableIntStateOf(12) }
    var creating by remember { mutableStateOf(false) } // stops a double tap from creating two projects

    // Canvas sides are clamped by the same ProjectLimits rule the ViewModel applies, so the size
    // shown here is the size that is created.
    val fitted = org.wishyclip.app.model.ProjectLimits.fit(
        widthText.toIntOrNull() ?: 1080, heightText.toIntOrNull() ?: 1080
    )
    val widthInt = fitted.width
    val heightInt = fitted.height
    val estMb = widthInt.toLong() * heightInt.toLong() * 4L / (1024 * 1024)

    @Composable
    fun PreviewPane(modifier: Modifier) {
        CanvasPreview(
            width = widthInt, height = heightInt, fps = fps, estMb = estMb,
            modifier = modifier
        )
    }

    @Composable
    fun Presets() {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
        ) {
            for (p in AspectPreset.entries) {
                val selected = preset == p
                val descr = stringResource(p.labelRes)
                val ratio = (if (p == AspectPreset.CUSTOM) widthInt.toFloat() / heightInt else p.width.toFloat() / p.height)
                    .coerceIn(0.4f, 2.5f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                        .clip(RoundedCornerShape(tokens.mediumRadius))
                        .background(if (selected) tokens.primary.copy(alpha = 0.14f) else tokens.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) tokens.primary else tokens.onSurfaceVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(tokens.mediumRadius)
                        )
                        .semantics { contentDescription = descr }
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            preset = p
                            if (p != AspectPreset.CUSTOM) {
                                widthText = p.width.toString()
                                heightText = p.height.toString()
                            }
                        })
                        .padding(vertical = tokens.spaceSmall),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceXs, Alignment.CenterVertically)
                ) {
                    val bw = if (ratio >= 1f) 34.dp else 34.dp * ratio
                    val bh = if (ratio >= 1f) 34.dp / ratio else 34.dp
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(bw, bh)
                                .border(2.dp, if (selected) tokens.primary else tokens.onSurfaceVariant, RoundedCornerShape(3.dp))
                        )
                    }
                    Text(
                        text = stringResource(p.ratioRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) tokens.primary else tokens.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    fun CustomSize() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
            OutlinedTextField(
                value = widthText,
                onValueChange = { widthText = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.ui_width_px)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.ui_canvas_limits_short, MIN_CANVAS_SIDE, MAX_CANVAS_SIDE)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.ui_height_px)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    fun Controls(modifier: Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.ui_project_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Presets()
            if (preset == AspectPreset.CUSTOM) CustomSize()
            WishySlider(
                value = fps.toFloat(),
                onValueChange = { fps = it.roundToInt() },
                valueRange = 1f..60f,
                label = stringResource(R.string.ui_target_fps),
                unit = " fps"
            )
            folderCard?.invoke()
            Button(
                enabled = !creating && name.isNotBlank(),
                onClick = {
                    if (!creating && name.isNotBlank()) {
                        creating = true
                        onCreateProject(name.trim(), widthInt, heightInt, fps)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.action_create))
            }
        }
    }

    WishyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = tokens.surface) {
            BoxWithConstraints(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = tokens.spaceLarge)
            ) {
                val wide = maxWidth >= 640.dp && maxWidth > maxHeight
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                    ) {
                        ActionIconButton(
                            iconRes = WishyIcons.Back,
                            contentDescription = stringResource(R.string.action_cancel),
                            onClick = onBack
                        )
                        Text(
                            text = stringResource(R.string.title_new_project),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.onSurface
                        )
                    }
                    if (wide) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spaceLarge)
                        ) {
                            PreviewPane(Modifier.weight(1f).fillMaxHeight())
                            // Last-resort scroll: a very short landscape window still reaches every control.
                            Controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                        }
                    } else {
                        // The preview takes whatever height is left, so the controls never need scrolling.
                        PreviewPane(Modifier.weight(1f).fillMaxWidth())
                        Controls(Modifier.fillMaxWidth().padding(bottom = tokens.spaceSmall))
                    }
                }
            }
        }
    }
}

/**
 * Live picture of what will be created: the canvas shape (to scale), its pixel size, and a row of
 * dots stepping at the chosen frame rate so speed can be felt rather than read.
 */
@Composable
private fun CanvasPreview(width: Int, height: Int, fps: Int, estMb: Long, modifier: Modifier = Modifier) {
    val tokens = WishyTheme.tokens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.largeRadius))
            .background(tokens.canvasBackdrop),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(tokens.spaceMedium), contentAlignment = Alignment.Center) {
            // Fit the canvas rectangle into the free space, keeping its true proportions.
            val ratio = width.toFloat() / height
            val availW = maxWidth
            val availH = maxHeight
            val w = if (availW / availH > ratio) availH * ratio else availW
            val h = if (availW / availH > ratio) availH else availW / ratio
            Box(
                Modifier
                    .size(w.coerceAtLeast(1.dp), h.coerceAtLeast(1.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(tokens.paper)
                    .border(1.dp, tokens.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Rule-of-thirds guide, drawn faintly, so the empty paper reads as a canvas.
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0x1F000000)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
                        drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
                    }
                }
                Text(
                    text = "$width × $height",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF444444)
                )
            }
        }
        FpsTicker(fps)
        Text(
            text = stringResource(R.string.ui_np_summary, width, height, fps) + "\n" +
                stringResource(R.string.ui_np_duration, fps) +
                if (estMb > 24) "\n" + stringResource(R.string.ui_layer_memory_high) else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (estMb > 24) tokens.danger else tokens.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceSmall)
        )
    }
}

/** Eight dots lighting one after another at the chosen speed. Own composable so only it recomposes. */
@Composable
private fun FpsTicker(fps: Int) {
    val tokens = WishyTheme.tokens
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(fps) {
        while (true) {
            delay(1000L / fps.coerceIn(1, 60))
            tick = (tick + 1) % 8
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
        for (i in 0 until 8) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == tick) tokens.primary else tokens.onSurfaceVariant.copy(alpha = 0.25f))
            )
        }
    }
}

@Preview(name = "NewProjectScreen Light")
@Composable
private fun NewProjectScreenLightPreview() {
    WishyTheme(tokens = LightTokens) {
        NewProjectScreen(onCreateProject = { _, _, _, _ -> }, onBack = {})
    }
}

@Preview(name = "NewProjectScreen Dark")
@Composable
private fun NewProjectScreenDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        NewProjectScreen(onCreateProject = { _, _, _, _ -> }, onBack = {})
    }
}
