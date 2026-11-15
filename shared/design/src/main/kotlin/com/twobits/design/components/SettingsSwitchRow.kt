package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twobits.design.TwoBitsSpacing

/**
 * A settings row with a trailing [Switch] where the whole row is the touch target and is announced
 * as one switch ("Title, subtitle, on/off") by TalkBack. Use this instead of a Row with a bare
 * Switch, which leaves the label untappable and reads the switch with no name.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    // Zero the horizontal part when the row already sits inside a padded card.
    contentPadding: PaddingValues = SettingsRowDefaults.contentPadding,
) {
    SettingsControlRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        contentPadding = contentPadding,
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        // onCheckedChange = null: the row owns the interaction, so the Switch isn't a second,
        // separately-focusable target.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A settings row with a trailing [RadioButton] — one option of a single-choice group. The whole
 * row is selectable; wrap the group in `Modifier.selectableGroup()`.
 */
@Composable
fun SettingsOptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    // Zero the horizontal part when the row already sits inside a padded card.
    contentPadding: PaddingValues = SettingsRowDefaults.contentPadding,
) {
    SettingsControlRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        contentPadding = contentPadding,
        modifier =
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

object SettingsRowDefaults {
    val contentPadding = PaddingValues(horizontal = TwoBitsSpacing.card, vertical = TwoBitsSpacing.item)

    /** For rows placed inside an already-padded card. */
    val insetContentPadding = PaddingValues(vertical = TwoBitsSpacing.item)
}

@Composable
private fun SettingsControlRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    enabled: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(contentPadding)
                .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TwoBitsSpacing.screenH),
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
