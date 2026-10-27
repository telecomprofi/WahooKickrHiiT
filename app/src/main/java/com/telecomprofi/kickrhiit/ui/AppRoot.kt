package com.telecomprofi.kickrhiit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.ride.RideStatus
import com.telecomprofi.kickrhiit.ui.screens.DataScreen
import com.telecomprofi.kickrhiit.ui.screens.HomeScreen
import com.telecomprofi.kickrhiit.ui.screens.MusicScreen
import com.telecomprofi.kickrhiit.ui.screens.RideScreen
import com.telecomprofi.kickrhiit.ui.screens.SettingsScreen
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.RootViewModel

private enum class Panel(val label: String) {
    HOME("Home"),
    RIDE("Ride"),
    DATA("Data"),
    MUSIC("Music"),
    SETUP("Setup"),
}

/**
 * The case the four panels sit in. Tabs are buttons on a machined strip along
 * the bottom, because that is where a device puts its controls.
 *
 * The screen-on flag is held here rather than on the ride panel: a tab change
 * must not put the phone to sleep in the middle of an interval.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier, vm: RootViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val ride by vm.ride.collectAsState()
    // One pager for the five panels: the tab strip and the swipe are two ways of
    // driving the same thing, which is why the current page comes from the pager
    // rather than from a variable the two would have to keep in step.
    val pagerState = rememberPagerState(initialPage = Panel.HOME.ordinal) { Panel.entries.size }
    val scope = rememberCoroutineScope()
    val panel = Panel.entries[pagerState.currentPage.coerceIn(0, Panel.entries.lastIndex)]

    fun go(target: Panel) {
        scope.launch { pagerState.animateScrollToPage(target.ordinal) }
    }

    // A finished ride belongs on the front panel with its numbers, not on a
    // cockpit showing zeroes. Keyed on the status itself so it fires once.
    LaunchedEffect(ride.status) {
        if (ride.status == RideStatus.FINISHED) pagerState.animateScrollToPage(Panel.HOME.ordinal)
    }

    val context = LocalContext.current
    val keepAwake = settings.ride.keepScreenOn &&
        (ride.status == RideStatus.RUNNING || ride.status == RideStatus.PAUSED)
    DisposableEffect(keepAwake) {
        val window = context.findActivity()?.window
        if (keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(modifier.fillMaxSize().background(Skeuo.Bezel)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // Each panel keeps its own scroll position and its own state as you
            // swipe past it, which is the whole point of using a pager here.
            key = { Panel.entries[it].name },
        ) { page ->
            when (Panel.entries[page]) {
                Panel.HOME -> HomeScreen(onStarted = { go(Panel.RIDE) })
                Panel.RIDE -> RideScreen()
                Panel.DATA -> DataScreen()
                Panel.MUSIC -> MusicScreen()
                Panel.SETUP -> SettingsScreen()
            }
        }
        TabStrip(panel, riding = ride.isActive) { go(it) }
    }
}

@Composable
private fun TabStrip(current: Panel, riding: Boolean, onSelect: (Panel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Skeuo.MetalMid, Skeuo.MetalBottom, Color(0xFF1B1F24))
                )
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Panel.entries.forEach { p ->
            val lit = p == current
            val alert = p == Panel.RIDE && riding && !lit
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            lit -> Skeuo.Brass
                            alert -> Skeuo.Go
                            else -> Skeuo.Felt
                        }
                    )
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.label,
                    style = Skeuo.engraved.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                        color = if (lit || alert) Skeuo.Bezel else Skeuo.Rivet,
                    ),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
