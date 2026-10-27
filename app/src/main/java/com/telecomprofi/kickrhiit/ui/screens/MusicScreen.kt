package com.telecomprofi.kickrhiit.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.data.db.BpmSource
import com.telecomprofi.kickrhiit.data.db.TrackEntity
import com.telecomprofi.kickrhiit.ui.components.GlossyButton
import com.telecomprofi.kickrhiit.ui.components.InsetPanel
import com.telecomprofi.kickrhiit.ui.components.MetalSurface
import com.telecomprofi.kickrhiit.ui.components.Stepper
import com.telecomprofi.kickrhiit.ui.components.StitchedHeader
import com.telecomprofi.kickrhiit.ui.components.SwitchRow
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.MusicViewModel
import kotlin.math.roundToInt


/**
 * Set a track's tempo by hand — the fix that sticks when measurement gets one
 * wrong. Half, double and ×1.5 are offered because those are the three ways a
 * beat tracker fails.
 */
@Composable
private fun BpmEditor(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSet: (Double) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember(track.uri) {
        mutableStateOf(if (track.bpm > 0) track.bpm.roundToInt() else 120)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Skeuo.Felt,
        title = { Text(track.displayName, style = Skeuo.engraved.copy(fontSize = 16.sp), maxLines = 2) },
        text = {
            Column {
                Text(
                    if (track.analysed) {
                        "Currently ${track.bpm.roundToInt()} bpm, ${track.bpmSourceLabel}."
                    } else {
                        "No tempo yet."
                    },
                    style = Skeuo.lcd(Skeuo.LcdDim, 12),
                )
                Spacer(Modifier.height(10.dp))
                Stepper("Beats per minute", value, { value = it }, 40..220, step = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlossyButton(
                        text = "Half",
                        onClick = { value = (value / 2).coerceAtLeast(40) },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                    GlossyButton(
                        text = "× 1.5",
                        onClick = { value = (value * 3 / 2).coerceAtMost(220) },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                    GlossyButton(
                        text = "Double",
                        onClick = { value = (value * 2).coerceAtMost(220) },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                }
            }
        },
        confirmButton = {
            GlossyButton(
                text = "Set",
                onClick = { onSet(value.toDouble()) },
                tint = Skeuo.Go,
                modifier = Modifier.width(96.dp),
                height = 42.dp,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (track.bpmSource == BpmSource.MANUAL) {
                    GlossyButton(
                        text = "Re-measure",
                        onClick = onClear,
                        modifier = Modifier.width(120.dp),
                        height = 42.dp,
                    )
                }
                GlossyButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.width(88.dp),
                    height = 42.dp,
                )
            }
        },
    )
}

private enum class MusicPanel { PLAYLIST, LIBRARY, OPTIONS }

/**
 * The tape deck. A playlist of tracks whose tempo has been measured, and a
 * searchable view of the folder they came from.
 *
 * Tempo matters here in a way it does not on a walk: the app nudges playback
 * speed so the beat lands on the pedal stroke, and a sprint gets the fastest
 * thing in the queue. Loudness is levelled so a quiet track does not tempt you
 * to turn the volume up and then get shouted at by the next one.
 */
@Composable
fun MusicScreen(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val music by vm.music.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val catalogueCount by vm.catalogueCount.collectAsState()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val report by vm.report.collectAsState()
    val progress by vm.progress.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    var panel by remember { mutableStateOf(MusicPanel.PLAYLIST) }
    var editing by remember { mutableStateOf<TrackEntity?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setFolder(uri.toString())
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }

    editing?.let { track ->
        BpmEditor(
            track = track,
            onDismiss = { editing = null },
            onSet = { bpm -> vm.setBpm(track, bpm); editing = null },
            onClear = { vm.clearBpm(track); editing = null },
        )
    }

    val analysed = tracks.count { it.analysed }
    val levelled = tracks.count { it.loudnessMeasured }

    MetalSurface {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            InsetPanel(Modifier.fillMaxWidth().height(56.dp)) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${tracks.size} queued", style = Skeuo.lcd(Skeuo.LcdAmber, 18))
                    Text("$analysed tempo", style = Skeuo.lcd(Skeuo.LcdGreen, 18))
                    Text("$levelled levelled", style = Skeuo.lcd(Skeuo.LcdBlue, 18))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab("Playlist", panel == MusicPanel.PLAYLIST, Modifier.weight(1f)) { panel = MusicPanel.PLAYLIST }
                Tab("Library", panel == MusicPanel.LIBRARY, Modifier.weight(1f)) { panel = MusicPanel.LIBRARY }
                Tab("Sound", panel == MusicPanel.OPTIONS, Modifier.weight(1f)) { panel = MusicPanel.OPTIONS }
            }

            if (busy || status != null || progress != null) {
                Spacer(Modifier.height(8.dp))
                val line = progress?.let { p ->
                    "Measuring ${p.done}/${p.total}" + (p.currentTitle?.let { " · $it" } ?: "")
                } ?: status ?: "Working…"
                Text(line, style = Skeuo.lcd(Skeuo.LcdAmber, 12), maxLines = 1)
            }

            report?.let { r ->
                Spacer(Modifier.height(8.dp))
                InsetPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(
                            r.error ?: "Folder scanned: ${r.filesFound} files, " +
                                "${r.catalogued} catalogued, ${r.duplicatesSkipped} duplicates",
                            style = Skeuo.lcd(if (r.error != null) Skeuo.LcdRed else Skeuo.LcdGreen, 13),
                        )
                        Text(
                            "Tap to dismiss",
                            style = Skeuo.lcd(Skeuo.LcdDim, 11),
                            modifier = Modifier.clickable { vm.dismissReport() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when (panel) {
                MusicPanel.PLAYLIST -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossyButton(
                            text = "Add files",
                            onClick = { filePicker.launch(arrayOf("audio/*")) },
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                        GlossyButton(
                            text = if (busy) "Stop" else "Measure",
                            onClick = { if (busy) vm.cancel() else vm.analyse() },
                            tint = if (busy) Skeuo.Danger else Skeuo.MetalMid,
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                        GlossyButton(
                            text = "Clear",
                            onClick = { vm.clear() },
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InsetPanel(Modifier.fillMaxWidth().weight(1f)) {
                        if (tracks.isEmpty()) {
                            Text(
                                "Nothing queued. Point the app at a folder in LIBRARY, " +
                                    "or add single files here.",
                                style = Skeuo.lcd(Skeuo.LcdDim, 13),
                                modifier = Modifier.padding(14.dp),
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                                items(tracks, key = { it.uri }) { track ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            // Tap to correct a tempo: beat
                                            // tracking is fallible and typing
                                            // the number beats arguing with it.
                                            .clickable { editing = track }
                                            .padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                track.displayName,
                                                style = Skeuo.lcd(
                                                    if (track.missing) Skeuo.LcdRed else Skeuo.LcdGreen,
                                                    14,
                                                ),
                                                maxLines = 1,
                                            )
                                            Text(
                                                buildString {
                                                    append(track.artist ?: "unknown")
                                                    append("  ·  ")
                                                    append(
                                                        if (track.analysed) {
                                                            "${track.bpm.roundToInt()} bpm" +
                                                                when {
                                                                    track.bpmSource == BpmSource.TAG -> " (tag)"
                                                                    track.bpmSource == BpmSource.MANUAL -> " (set)"
                                                                    !track.tempoTrusted -> " (unsure)"
                                                                    else -> ""
                                                                }
                                                        } else {
                                                            "tempo not measured"
                                                        }
                                                    )
                                                    if (track.loudnessMeasured) {
                                                        append("  ·  %.1f LUFS".format(track.loudnessLufs))
                                                    }
                                                },
                                                style = Skeuo.lcd(Skeuo.LcdDim, 11),
                                                maxLines = 1,
                                            )
                                        }
                                        GlossyButton(
                                            text = "×",
                                            onClick = { vm.remove(track) },
                                            height = 30.dp,
                                            corner = 8.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MusicPanel.LIBRARY -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossyButton(
                            text = "Pick folder",
                            onClick = { folderPicker.launch(null) },
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                        GlossyButton(
                            text = "Rescan",
                            onClick = { vm.scanFolder() },
                            enabled = music.musicTreeUri != null,
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                        GlossyButton(
                            text = "Add all",
                            onClick = { vm.addAllResults() },
                            enabled = results.isNotEmpty(),
                            tint = Skeuo.Go,
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InsetPanel(Modifier.fillMaxWidth().height(46.dp)) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), Alignment.CenterStart) {
                            BasicTextField(
                                value = query,
                                onValueChange = { vm.setQuery(it) },
                                singleLine = true,
                                textStyle = Skeuo.lcd(Skeuo.LcdGreen, 15),
                                cursorBrush = SolidColor(Skeuo.LcdGreen),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) {
                                            Text(
                                                "Search $catalogueCount files",
                                                style = Skeuo.lcd(Skeuo.LcdDim, 15),
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    InsetPanel(Modifier.fillMaxWidth().weight(1f)) {
                        if (results.isEmpty()) {
                            Text(
                                if (music.musicTreeUri == null) {
                                    "No folder chosen yet."
                                } else {
                                    "Type to search the scanned folder."
                                },
                                style = Skeuo.lcd(Skeuo.LcdDim, 13),
                                modifier = Modifier.padding(14.dp),
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                                items(results, key = { it.uri }) { entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { vm.addFromCatalogue(entry.uri) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                entry.displayName,
                                                style = Skeuo.lcd(Skeuo.LcdGreen, 14),
                                                maxLines = 1,
                                            )
                                            Text(
                                                entry.subtitle,
                                                style = Skeuo.lcd(Skeuo.LcdDim, 11),
                                                maxLines = 1,
                                            )
                                        }
                                        Text("+", style = Skeuo.lcd(Skeuo.LcdAmber, 20))
                                    }
                                }
                            }
                        }
                    }
                }

                MusicPanel.OPTIONS -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        StitchedHeader("Playback")
                        SwitchRow(
                            label = "Play music during rides",
                            checked = music.enabled,
                            onChange = { v -> vm.update { it.copy(enabled = v) } },
                        )
                        SwitchRow(
                            label = "Leave it to another player",
                            checked = music.useExternalPlayer,
                            onChange = { v -> vm.update { it.copy(useExternalPlayer = v) } },
                            description = "The app keeps quiet and only ducks for spoken cues.",
                        )
                        Stepper(
                            label = "Volume",
                            value = music.volumePercent,
                            onChange = { v -> vm.update { it.copy(volumePercent = v) } },
                            range = 10..100,
                            step = 5,
                            suffix = " %",
                        )
                        Stepper(
                            label = "Duck to, while the coach speaks",
                            value = music.duckPercent,
                            onChange = { v -> vm.update { it.copy(duckPercent = v) } },
                            range = 0..60,
                            step = 2,
                            suffix = " %",
                        )

                        Spacer(Modifier.height(14.dp))
                        StitchedHeader("Tempo")
                        SwitchRow(
                            label = "Match the beat to your cadence",
                            checked = music.matchBpm,
                            onChange = { v -> vm.update { it.copy(matchBpm = v) } },
                            description = "Picks the closest track for each step and nudges its speed.",
                        )
                        Stepper(
                            label = "Most a track may be stretched",
                            value = music.maxStretchPercent,
                            onChange = { v -> vm.update { it.copy(maxStretchPercent = v) } },
                            range = 0..15,
                            suffix = " %",
                        )
                        SwitchRow(
                            label = "Allow half and double time",
                            checked = music.allowOctaveMatch,
                            onChange = { v -> vm.update { it.copy(allowOctaveMatch = v) } },
                            description = "A 180 bpm track works at a cadence of 90.",
                        )

                        Spacer(Modifier.height(14.dp))
                        StitchedHeader("Loudness")
                        SwitchRow(
                            label = "Level every track",
                            checked = music.normaliseLoudness,
                            onChange = { v -> vm.update { it.copy(normaliseLoudness = v) } },
                            description = "Measured to EBU R128 and matched to the middle of your queue.",
                        )
                        Stepper(
                            label = "Most a loud track may be cut",
                            value = music.maxCutDb,
                            onChange = { v -> vm.update { it.copy(maxCutDb = v) } },
                            range = 0..20,
                            suffix = " dB",
                        )
                        Stepper(
                            label = "Most a quiet track may be lifted",
                            value = music.maxBoostDb,
                            onChange = { v -> vm.update { it.copy(maxBoostDb = v) } },
                            range = 0..12,
                            suffix = " dB",
                        )
                        GlossyButton(
                            text = "Read BPM tags from files",
                            onClick = { vm.readTags() },
                            enabled = tracks.isNotEmpty() && !busy,
                            tint = Skeuo.Go,
                            height = 40.dp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        GlossyButton(
                            text = "Re-measure everything",
                            onClick = { vm.remeasure() },
                            enabled = tracks.isNotEmpty() && !busy,
                            height = 40.dp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            "Levelling reads every file once. It takes a while, and it is " +
                                "the reason no track startles you in headphones.",
                            style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A small hardware-looking tab: lit when selected, sunk when not. */
@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Skeuo.Brass else Skeuo.Felt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Skeuo.engraved.copy(
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
                color = if (selected) Skeuo.Bezel else Skeuo.Rivet,
            ),
        )
    }
}
