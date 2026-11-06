package com.gmusic.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmusic.app.R
import com.gmusic.app.data.local.EqualizerPreset
import kotlin.math.roundToInt

private val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenWebLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val support by viewModel.effectsSupport.collectAsStateWithLifecycle()
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    var showInstancesDialog by remember { mutableStateOf(false) }
    var pipedInput by remember(settings.pipedInstancesCsv) { mutableStateOf(settings.pipedInstancesCsv) }
    var invidiousInput by remember(settings.invidiousInstancesCsv) { mutableStateOf(settings.invidiousInstancesCsv) }

    if (showInstancesDialog) {
        AlertDialog(
            onDismissRequest = { showInstancesDialog = false },
            title = { Text(stringResource(R.string.settings_instances)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pipedInput,
                        onValueChange = { pipedInput = it },
                        label = { Text(stringResource(R.string.settings_instances_piped_csv)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invidiousInput,
                        onValueChange = { invidiousInput = it },
                        label = { Text(stringResource(R.string.settings_instances_invidious_csv)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveInstances(pipedInput, invidiousInput)
                        showInstancesDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstancesDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Tema Material You
        item {
            SettingsCard(title = stringResource(R.string.settings_theme)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_theme_dynamic_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_theme_dynamic_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Crossfade (0..12 s) + Qualità Audio
        item {
            SettingsCard(title = stringResource(R.string.settings_audio_quality)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = !settings.highAudioQuality,
                        onClick = { viewModel.setHighAudioQuality(false) },
                        label = { Text(stringResource(R.string.settings_quality_auto)) }
                    )
                    FilterChip(
                        selected = settings.highAudioQuality,
                        onClick = { viewModel.setHighAudioQuality(true) },
                        label = { Text(stringResource(R.string.settings_quality_high)) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${stringResource(R.string.settings_crossfade)}: ${settings.crossfadeSeconds} s",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { viewModel.setCrossfadeSeconds(it.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11
                )
            }
        }

        // Equalizzatore (5 bande + Preset + BassBoost + Virtualizer)
        item {
            SettingsCard(title = stringResource(R.string.settings_equalizer)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_equalizer),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = settings.eqEnabled,
                        onCheckedChange = viewModel::setEqEnabled,
                        enabled = support.equalizerSupported
                    )
                }

                if (support.equalizerSupported) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EqualizerPreset.ALL_PRESETS, key = { it.id }) { preset ->
                            FilterChip(
                                selected = settings.eqPresetId == preset.id,
                                onClick = { viewModel.selectEqPreset(preset) },
                                enabled = settings.eqEnabled,
                                label = { Text(preset.labelIt) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    BAND_LABELS.forEachIndexed { idx, freqLabel ->
                        val dbVal = settings.eqBandsDb.getOrElse(idx) { 0 }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(56.dp)
                            )
                            Slider(
                                value = dbVal.toFloat(),
                                onValueChange = { viewModel.setEqBandDb(idx, it.roundToInt()) },
                                valueRange = -15f..15f,
                                enabled = settings.eqEnabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (dbVal > 0) "+$dbVal" else "$dbVal"} dB",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }

                if (support.bassBoostSupported) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.settings_bass_boost)}: ${settings.bassBoostPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.bassBoostPercent.toFloat(),
                        onValueChange = { viewModel.setBassBoostPercent(it.roundToInt()) },
                        valueRange = 0f..100f,
                        enabled = settings.eqEnabled
                    )
                }

                if (support.virtualizerSupported) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${stringResource(R.string.settings_virtualizer)}: ${settings.virtualizerPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.virtualizerPercent.toFloat(),
                        onValueChange = { viewModel.setVirtualizerPercent(it.roundToInt()) },
                        valueRange = 0f..100f,
                        enabled = settings.eqEnabled
                    )
                }
            }
        }

        // Cache, Download e Istanze Resolver
        item {
            SettingsCard(title = stringResource(R.string.settings_cache)) {
                Text(
                    text = stringResource(R.string.settings_cache_used_total_format, cacheSizeMb),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = viewModel::clearAudioCache,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_clear_cache))
                    }
                    OutlinedButton(
                        onClick = viewModel::deleteAllDownloads,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_delete_downloads))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showInstancesDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_instances))
                }
            }
        }

        // Account YouTube Music
        item {
            SettingsCard(title = stringResource(R.string.settings_account)) {
                if (isLoggedIn) {
                    Text(
                        text = stringResource(R.string.settings_account_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_logout))
                    }
                } else {
                    if (onOpenWebLogin != null) {
                        Button(
                            onClick = onOpenWebLogin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_login))
                        }
                    }
                }
            }
        }

        // Informazioni
        item {
            SettingsCard(title = stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_about_desc, com.gmusic.app.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
