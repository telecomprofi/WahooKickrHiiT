package com.telecomprofi.kickrhiit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.BuildConfig
import com.telecomprofi.kickrhiit.core.TimeMath
import com.telecomprofi.kickrhiit.core.Workout
import com.telecomprofi.kickrhiit.data.db.RideEntity
import com.telecomprofi.kickrhiit.ride.RideService
import com.telecomprofi.kickrhiit.ui.components.GlossyButton
import com.telecomprofi.kickrhiit.ui.components.InsetPanel
import com.telecomprofi.kickrhiit.ui.components.LcdReadout
import com.telecomprofi.kickrhiit.ui.components.MetalSurface
import com.telecomprofi.kickrhiit.ui.components.ProfileStrip
import com.telecomprofi.kickrhiit.ui.components.StitchedHeader
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.HomeViewModel

/**
 * The front panel: pick a programme, see what it looks like, press the big
 * button. Everything else is behind the other tabs.
 */
/** Rides shown at once in History; the rest are a page away. */
private const val HISTORY_ROWS = 3

@Composable
fun HomeScreen(onStarted: () -> Unit = {}, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val workouts by vm.workouts.collectAsState()
    val selected by vm.selected.collectAsState()
    val recent by vm.recent.collectAsState()
    val week by vm.week.collectAsState()
    var infoFor by remember { mutableStateOf<String?>(null) }
    var historyPage by remember { mutableStateOf(0) }
    val lastRide by vm.lastRide.collectAsState()
    val note by vm.note.collectAsState()
    val ride by vm.ride.collectAsState()

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { RideService.start(context); onStarted() }

    fun startRide() {
        val needed = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            RideService.start(context)
            onStarted()
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }

    MetalSurface {
        // The button is pinned, the rest scrolls under it: a start button you
        // have to go looking for is the one thing on this screen that must never
        // be off-screen, and the summary belongs above the fold, not below it.
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 10.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
            WeekStrip(week)

            lastRide?.let { done ->
                Spacer(Modifier.height(10.dp))
                StitchedHeader("Last ride", trailing = done.workoutName)
                Spacer(Modifier.height(8.dp))
                InsetPanel(Modifier.fillMaxWidth().height(96.dp)) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LcdReadout("${done.minutes}", "min", "time", size = 26)
                        LcdReadout("${done.normalisedPowerW}", "W", "normalised", size = 26)
                        LcdReadout("${done.workKj}", "kJ", "work", size = 26, color = Skeuo.LcdGreen)
                        if (done.bestSprintW > 0) {
                            LcdReadout("${done.bestSprintW}", "W", "best 30s", size = 26, color = Skeuo.LcdRed)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap to dismiss",
                    style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
                    modifier = Modifier.clickable { vm.dismissRide() },
                )
            }

            Spacer(Modifier.height(12.dp))
            StitchedHeader("Programme", trailing = "${workouts.size} available")
            Spacer(Modifier.height(8.dp))

            workouts.forEach { workout ->
                WorkoutCard(
                    workout = workout,
                    selected = workout.id == selected.id,
                    plannedKj = workout.plannedKj(settings.rider),
                    showBlurb = infoFor == workout.id,
                    onClick = { vm.select(workout) },
                    onInfo = { infoFor = if (infoFor == workout.id) null else workout.id },
                )
                Spacer(Modifier.height(6.dp))
            }

            if (recent.isNotEmpty()) {
                // Three rides at a time, with a pair of arrows. A nested scroll
                // area inside a scrolling screen fights the finger; paging does
                // not, and three lines is all this needs to be.
                val pages = (recent.size + HISTORY_ROWS - 1) / HISTORY_ROWS
                val page = historyPage.coerceIn(0, (pages - 1).coerceAtLeast(0))
                val from = page * HISTORY_ROWS
                val shown = recent.drop(from).take(HISTORY_ROWS)

                Spacer(Modifier.height(12.dp))
                StitchedHeader(
                    "History",
                    trailing = "${from + 1}–${from + shown.size} of ${recent.size}",
                )
                Spacer(Modifier.height(6.dp))
                shown.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${TimeMath.dayLabel(r.startedAt)} · ${r.workoutName}",
                            style = Skeuo.engraved.copy(fontSize = 12.sp),
                            maxLines = 1,
                        )
                        Text(
                            "${r.minutes}m · ${r.normalisedPowerW}W · ${r.workKj}kJ",
                            style = Skeuo.engraved.copy(fontSize = 12.sp, color = Skeuo.Brass),
                            maxLines = 1,
                        )
                    }
                }
                if (pages > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossyButton(
                            text = "◀",
                            onClick = { historyPage = (page - 1).coerceAtLeast(0) },
                            enabled = page > 0,
                            modifier = Modifier.weight(1f),
                            height = 36.dp,
                            corner = 8.dp,
                        )
                        GlossyButton(
                            text = "▶",
                            onClick = { historyPage = (page + 1).coerceAtMost(pages - 1) },
                            enabled = page < pages - 1,
                            modifier = Modifier.weight(1f),
                            height = 36.dp,
                            corner = 8.dp,
                        )
                    }
                }
            }

            note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = Skeuo.engraved.copy(color = Skeuo.Brass, fontSize = 12.sp))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Kickr HIIT ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                    if (BuildConfig.DEBUG) " · debug" else "",
                style = Skeuo.lcd(Skeuo.LcdDim, 11),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            GlossyButton(
                text = if (ride.isActive) "Back to the ride" else "Start",
                subtitle = if (ride.isActive) null else "${selected.name} · ${selected.minutes} min",
                onClick = { if (ride.isActive) onStarted() else startRide() },
                tint = if (ride.isActive) Skeuo.Brass else Skeuo.Go,
                height = 58.dp,
                corner = 14.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The engraved badge at the top of the case. */
/**
 * The week across the top: what has been ridden, as figures and as a row of
 * bikes. One glyph per ride is a better answer to "have I done enough this week"
 * than any number — you count them without meaning to.
 */
@Composable
private fun WeekStrip(rides: List<RideEntity>) {
    val minutes = rides.sumOf { it.minutes }
    val kj = rides.sumOf { it.workKj }
    val best = rides.maxOfOrNull { it.bestSprintW } ?: 0

    InsetPanel(Modifier.fillMaxWidth(), corner = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                LcdReadout("${rides.size}", "", "rides · 7 days", size = 30, color = Skeuo.LcdGreen)
                LcdReadout("$minutes", "min", "time", size = 30)
                LcdReadout("$kj", "kJ", "work", size = 30, color = Skeuo.LcdBlue)
                LcdReadout(
                    if (best > 0) "$best" else "--",
                    "W",
                    "best 30s",
                    size = 30,
                    color = Skeuo.LcdRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (rides.isEmpty()) {
                Text(
                    "Nothing this week — pick a programme below",
                    style = Skeuo.lcd(Skeuo.LcdDim, 11),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rides.takeLast(12).forEach { ride ->
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            // A ride that ran to the end of its plan is lit; one
                            // that was stopped early is outlined, and still counts.
                            tint = if (ride.completed) Skeuo.LcdGreen else Skeuo.LcdDim,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (rides.size > 12) {
                        Text("+${rides.size - 12}", style = Skeuo.lcd(Skeuo.LcdDim, 12))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    selected: Boolean,
    plannedKj: Int,
    showBlurb: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Skeuo.Felt else Skeuo.MetalBottom.copy(alpha = 0.55f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Skeuo.Brass else Skeuo.MetalEdgeDark,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = Skeuo.engraved.copy(fontSize = 16.sp))
                    Text(
                        buildString {
                            append(workout.subtitle)
                            append("  ·  ")
                            if (workout.sprintCount > 0) append("${workout.sprintCount} sprints · ")
                            append("~$plannedKj kJ")
                        },
                        style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
                        maxLines = 1,
                    )
                }
                Text(
                    "${workout.minutes} min",
                    style = Skeuo.lcd(if (selected) Skeuo.LcdAmber else Skeuo.LcdDim, 18),
                    modifier = Modifier.padding(end = 8.dp),
                )
                // The description is a paragraph, and three paragraphs is what
                // pushed the programmes off the screen. It lives behind this.
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (showBlurb) Skeuo.Brass else Skeuo.Bezel)
                        .clickable(onClick = onInfo),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "i",
                        style = Skeuo.engraved.copy(
                            fontSize = 15.sp,
                            color = if (showBlurb) Skeuo.Bezel else Skeuo.Rivet,
                        ),
                    )
                }
            }

            // The shape of the session is worth a glance for the one you are
            // about to ride; for the others the name and the length are enough.
            if (selected) {
                Spacer(Modifier.height(6.dp))
                ProfileStrip(workout, Modifier.fillMaxWidth().height(38.dp))
            }

            if (showBlurb) {
                Spacer(Modifier.height(6.dp))
                Text(
                    workout.blurb,
                    style = Skeuo.engraved.copy(fontSize = 12.sp, color = Skeuo.Rivet),
                )
            }
        }
    }
}
