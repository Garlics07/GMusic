package com.gmusic.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gmusic.app.R
import com.gmusic.app.data.local.EqualizerPreset
import kotlin.math.roundToInt

private val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

enum class SettingsSubSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    THEME("Tema e colori", "Colore dinamico copertina, dissolvenza brani", Icons.Filled.Palette),
    AUDIO_QUALITY("Qualità audio", "Opus 251 preferito, fallback AAC 140, istanze", Icons.Filled.HighQuality),
    EQUALIZER("Equalizzatore", "Preset sonori, bass boost, virtualizzatore, 5 bande", Icons.Filled.Tune),
    CACHE("Memoria e cache", "Dimensione cache audio, pulizia dati temporanei", Icons.Filled.Storage),
    INFO("Informazioni app", "Versione 2.5.0, architettura, cronologia modifiche chat", Icons.Filled.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalBottomSheet(
    onDismiss: () -> Unit,
    onOpenWebLogin: () -> Unit,
    accountAvatarUrl: String? = null,
    initialSubSection: SettingsSubSection? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val support by viewModel.effectsSupport.collectAsStateWithLifecycle()
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    var currentSection by remember(initialSubSection) { mutableStateOf<SettingsSubSection?>(initialSubSection) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var showInstancesDialog by remember { mutableStateOf(false) }
    var pipedInput by remember(settings.pipedInstancesCsv) { mutableStateOf(settings.pipedInstancesCsv) }
    var invidiousInput by remember(settings.invidiousInstancesCsv) { mutableStateOf(settings.invidiousInstancesCsv) }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text(stringResource(R.string.settings_paste_cookie)) },
            text = {
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    placeholder = { Text("SAPISID=...; __Secure-3PAPISID=...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cookieInput.isNotBlank()) {
                            viewModel.saveManualCookie(cookieInput)
                            cookieInput = ""
                            showCookieDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showInstancesDialog) {
        AlertDialog(
            onDismissRequest = { showInstancesDialog = false },
            title = { Text(stringResource(R.string.settings_instances)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pipedInput,
                        onValueChange = { pipedInput = it },
                        label = { Text("Piped (CSV)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invidiousInput,
                        onValueChange = { invidiousInput = it },
                        label = { Text("Invidious (CSV)") },
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 640.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Top bar del modal: se siamo in una sottosezione mostriamo freccia indietro, altrimenti logo, titolo e chiudi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSection != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { currentSection = null }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_cancel)
                                    )
                                }
                                Text(
                                    text = currentSection!!.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.ic_gmusic_logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                        }
                    }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "settingsSubSectionAnim"
            ) { section ->
                if (section == null) {
                    // Schermata principale: Header account Google in alto + Lista categorie
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Gestione Account Google in alto
                        item(key = "account_card") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isLoggedIn) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!accountAvatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = accountAvatarUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Account Google",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isLoggedIn) "Accesso GMusic • YouTube Music" else "Modalità Ospite (Locale)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isLoggedIn) {
                                            OutlinedButton(onClick = viewModel::logout) {
                                                Text(stringResource(R.string.settings_logout))
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilledTonalButton(onClick = onOpenWebLogin) {
                                                    Text(stringResource(R.string.settings_login))
                                                }
                                            }
                                        }
                                    }

                                    if (isLoggedIn) {
                                        val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Dispositivo: $deviceName (Android ${android.os.Build.VERSION.RELEASE})",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Accesso registrato su Google per GMusic tramite sessione web YouTube Music.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Lista categorie cliccabili nel modal
                        items(SettingsSubSection.entries, key = { it.name }) { sub ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentSection = sub }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = sub.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sub.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = sub.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        item(key = "cookie_fallback") {
                            if (!isLoggedIn) {
                                TextButton(
                                    onClick = { showCookieDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_paste_cookie))
                                }
                            }
                        }
                    }
                } else {
                    // Contenuto dinamico della sottosezione selezionata
                    when (section) {
                        SettingsSubSection.THEME -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.settings_adaptive_player),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Tinta dinamica sincronizzata con la copertina del brano in riproduzione",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = settings.adaptivePlayerColor,
                                                onCheckedChange = viewModel::setAdaptivePlayerColor
                                            )
                                        }
                                    }
                                }
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.settings_crossfade),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${settings.crossfadeSeconds}s",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Slider(
                                                value = settings.crossfadeSeconds.toFloat(),
                                                onValueChange = { viewModel.setCrossfadeSeconds(it.roundToInt()) },
                                                valueRange = 0f..12f,
                                                steps = 11
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SettingsSubSection.AUDIO_QUALITY -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.settings_quality_high),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Opus itag 251 (160 kbps) preferito con fallback AAC itag 140. Nessun video decodificato (Audio Only rigoroso).",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = settings.highAudioQuality,
                                                onCheckedChange = viewModel::setHighAudioQuality
                                            )
                                        }
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { showInstancesDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_instances))
                                    }
                                }
                            }
                        }

                        SettingsSubSection.EQUALIZER -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_equalizer),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Switch(
                                                checked = settings.eqEnabled,
                                                onCheckedChange = viewModel::setEqEnabled
                                            )
                                        }
                                    }
                                }

                                if (settings.eqEnabled) {
                                    item {
                                        Text(
                                            text = "Preset",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(EqualizerPreset.ALL_PRESETS) { preset ->
                                                FilterChip(
                                                    selected = settings.eqPresetId == preset.id,
                                                    onClick = { viewModel.selectEqPreset(preset) },
                                                    label = { Text(preset.labelIt) }
                                                )
                                            }
                                        }
                                    }

                                    if (support.bassBoostSupported) {
                                        item {
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = "Bass Boost", style = MaterialTheme.typography.titleSmall)
                                                        Text(text = "${settings.bassBoostPercent}%", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Slider(
                                                        value = settings.bassBoostPercent.toFloat(),
                                                        onValueChange = { viewModel.setBassBoostPercent(it.roundToInt()) },
                                                        valueRange = 0f..100f
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (support.virtualizerSupported) {
                                        item {
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = "Virtualizzatore surround", style = MaterialTheme.typography.titleSmall)
                                                        Text(text = "${settings.virtualizerPercent}%", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Slider(
                                                        value = settings.virtualizerPercent.toFloat(),
                                                        onValueChange = { viewModel.setVirtualizerPercent(it.roundToInt()) },
                                                        valueRange = 0f..100f
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Text(text = "Bande di frequenza", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    }

                                    items(BAND_LABELS.indices.toList()) { index ->
                                        val bandDb = settings.eqBandsDb.getOrNull(index) ?: 0
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(text = BAND_LABELS[index], style = MaterialTheme.typography.bodyMedium)
                                                    Text(text = "${bandDb} dB", style = MaterialTheme.typography.bodySmall)
                                                }
                                                Slider(
                                                    value = bandDb.toFloat(),
                                                    onValueChange = { viewModel.setEqBandDb(index, it.roundToInt()) },
                                                    valueRange = -10f..10f,
                                                    steps = 19
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsSubSection.CACHE -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                            Text(
                                                text = "Spazio utilizzato",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${cacheSizeMb} MB utilizzati",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Include i file audio memorizzati nella cache LRU di riproduzione temporanea.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                item {
                                    Button(
                                        onClick = viewModel::clearAudioCache,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.settings_clear_cache))
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = viewModel::deleteAllDownloads,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Elimina tutti i brani scaricati offline")
                                    }
                                }
                            }
                        }

                        SettingsSubSection.INFO -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                painter = painterResource(R.drawable.ic_gmusic_logo),
                                                contentDescription = "GMusic Logo",
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "GMusic",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Versione 2.6.0 (Build 20261103)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Audio Only Streaming Client • YouTube Music Backend",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Cronologia aggiornamenti di sviluppo",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "v2.6.0 (Attuale)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "• Nuovo logo ufficiale su mobile, Android Auto, widget e interfaccia\n• Pulsante '+' compatto per creare nuove playlist (senza testo)\n• Sezione 'Canzoni scaricate' sotto Canzoni preferite con riproduzione e casuale\n• Playlist: rimozione frecce e drag, eliminazione brani nei 3 puntini, tasti Casuale e Scarica playlist\n• Modal impostazioni centrato su schermo con margini e limiti verticali",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "v2.5.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "• Sincronizzazione ascolti YouTube Music in tempo reale (watchtime)\n• Gestione completa playlist (creazione, aggiunta e rimozione brani su YT e locale)\n• Cronologia ascolti integrata in Libreria\n• Pagina artista essenziale (solo Riproduci Casuale e Artista Preferito)\n• Filtri di ricerca dinamici (visibili solo con query attiva)\n• Classifiche ufficiali Top 100 YouTube Music Italia\n• Impostazioni in Modal Popup interattivo",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "v2.4.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "• Album in sole versioni audio ufficiali (ATV, no videoclip OMV/UGC)\n• Copertine HD 1:1 originali per tutti i brani degli album\n• Radio istantanea per artisti su Android Auto\n• Icona a cuore per Libreria su Android Auto e pulizia descrizioni futili\n• Status bar insets su tutti i sotto-schermi",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "v2.3.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "• Rework interfaccia Home stile Spotify (griglia 2x3 'Riproduzioni recenti')\n• Gradiente dinamico con Palette animato\n• Caroselli infiniti paged senza blocchi UI",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "v2.0.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "• Riscrittura completa da zero dell'applicazione\n• Jetpack Compose Material 3, Media3 ExoPlayer e Android Auto\n• Risolutore resiliente Opus 251 / AAC 140 con NewPipeExtractor",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}
