package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch

/**
 * Settings, Miscellaneous: the "Hide AI music" switch. Off by default. While
 * it is on, a note under it names the lists it uses, as their licences ask.
 */
@Composable
internal fun HideAiMusicRow(title: String) {
    val hideAiMusic by AppSettings.hideAiMusic.collectAsStateWithLifecycle()
    Column {
        SettingsRow(
            icon = Icons.Rounded.SmartToy,
            title = title,
            subtitle = stringResource(R.string.hide_ai_music_subtitle),
            trailing = {
                IosSwitch(
                    checked = hideAiMusic,
                    onCheckedChange = AppSettings::setHideAiMusic,
                )
            },
            onClick = { AppSettings.setHideAiMusic(!hideAiMusic) },
        )
        if (hideAiMusic) {
            Text(
                text = stringResource(R.string.hide_ai_music_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 14.dp),
            )
        }
    }
}
