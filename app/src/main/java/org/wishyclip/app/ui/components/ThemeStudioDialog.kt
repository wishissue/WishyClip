package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.wishyclip.app.brush.MiniJson
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import org.wishyclip.app.ui.design.themes.ThemeImporter

/** The colors people can edit directly; everything else is derived from them (readable text, containers...). */
private val EDITABLE_COLORS = listOf(
    "primary" to "Main color",
    "secondary" to "Second color",
    "surface" to "Background",
    "surfaceVariant" to "Cards & chips",
    "canvasBackdrop" to "Behind the canvas",
    "toolRail" to "Tool bar",
    "timeline" to "Timeline",
    "paper" to "Drawing paper",
    "danger" to "Delete / warning"
)

private fun colorOf(t: WishyTokens, key: String): Color? =
    ThemeImporter.colorsOf(t).firstOrNull { it.first == key }?.second

private fun fieldsFor(t: WishyTokens): Map<String, String> =
    EDITABLE_COLORS.associate { (key, _) -> key to ThemeImporter.toHex(colorOf(t, key) ?: Color.Black) }

/**
 * Create or edit a custom theme. [initial] is the theme being edited (or a copy of the current one
 * for "new"); [onSave] receives finished tokens built through [ThemeImporter], so text colors on
 * whatever the user picks stay readable.
 */
@Composable
fun ThemeStudioDialog(
    initial: WishyTokens,
    isEditing: Boolean,
    onSave: (WishyTokens) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(if (isEditing) initial.name else "My Theme") }
    var dark by remember { mutableStateOf(initial.isDark) }
    val original = remember { fieldsFor(initial) }
    val fields = remember { mutableStateMapOf<String, String>().apply { putAll(original) } }
    var copied by remember { mutableStateOf(false) }

    /** Builds tokens: edited colors are taken as typed, everything that depends on them is re-derived. */
    fun build(): WishyTokens {
        val edited = fields.filter { (k, v) ->
            ThemeImporter.parseColorOrNull(v) != null && !v.trim().equals(original[k], ignoreCase = true)
        }.keys
        val drop = HashSet<String>()
        if ("primary" in edited || "surface" in edited) {
            drop += listOf("primaryContainer", "onPrimaryContainer")
        }
        if ("primary" in edited) drop += "onPrimary"
        if ("secondary" in edited || "surface" in edited) {
            drop += listOf("secondaryContainer", "onSecondaryContainer")
        }
        if ("secondary" in edited) drop += "onSecondary"
        if ("surface" in edited) {
            drop += listOf("onSurface", "surfaceVariant", "onSurfaceVariant", "canvasBackdrop", "toolRail", "timeline")
        }
        drop -= edited

        val sb = StringBuilder("{\"name\":").append(MiniJson.quote(name.trim().ifBlank { ThemeImporter.DEFAULT_NAME }))
        sb.append(",\"isDark\":").append(dark)
        for (key in ThemeImporter.COLOR_KEYS) {
            if (key in drop) continue
            val typedColor = ThemeImporter.parseColorOrNull(fields[key])
            val color = typedColor ?: colorOf(initial, key) ?: continue
            sb.append(",\"").append(key).append("\":\"").append(ThemeImporter.toHex(color)).append('"')
        }
        sb.append('}')
        return ThemeImporter.parseJson(sb.toString())
    }

    val preview = build()
    val allValid = fields.values.all { ThemeImporter.parseColorOrNull(it) != null }

    WishyDialog(
        title = if (isEditing) "Edit theme" else "Theme Studio",
        onDismissRequest = onDismiss,
        confirmText = "Save",
        onConfirm = { if (allValid) onSave(build()) },
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
            ThemePreview(preview)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.filter { c -> c >= ' ' }.take(ThemeImporter.MAX_NAME_LENGTH) },
                label = { Text("Theme name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Start from", style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                for ((label, isDarkChoice) in listOf("Light" to false, "Dark" to true)) {
                    val selected = dark == isDarkChoice
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) tokens.primaryContainer else tokens.surfaceVariant)
                            .clickable {
                                if (dark != isDarkChoice) {
                                    dark = isDarkChoice
                                    // Switching the base palette replaces the colors below with that palette's.
                                    val base = if (isDarkChoice) DarkTokens else LightTokens
                                    fields.putAll(fieldsFor(base))
                                }
                            }
                            .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) tokens.onPrimaryContainer else tokens.onSurface
                        )
                    }
                }
            }

            for ((key, label) in EDITABLE_COLORS) {
                val value = fields[key] ?: ""
                val parsed = ThemeImporter.parseColorOrNull(value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(parsed ?: Color.Transparent)
                            .border(1.dp, tokens.glassBorder, CircleShape)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            fields[key] = v.filter { c -> c.isLetterOrDigit() || c == '#' }.take(9)
                        },
                        isError = parsed == null,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        modifier = Modifier.width(128.dp)
                    )
                }
            }
            Text(
                text = "Use #RRGGBB. Text colors, containers and borders are worked out for you.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onSurfaceVariant
            )

            TextButton(onClick = {
                clipboard.setText(AnnotatedString(ThemeImporter.toJson(preview)))
                copied = true
            }) {
                Text(if (copied) "Copied theme JSON" else "Copy theme as JSON (to share)")
            }
        }
    }
}

/** A tiny mock of the editor so the colors can be judged before saving. */
@Composable
private fun ThemePreview(t: WishyTokens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(t.canvasBackdrop)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(t.toolRail)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(t.paper),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = ThemeImporter.contrastOn(t.paper), style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier.width(76.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(t.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("Main", color = t.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(t.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Card", color = t.onSurface, style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(t.timeline)
            )
        }
    }
}
