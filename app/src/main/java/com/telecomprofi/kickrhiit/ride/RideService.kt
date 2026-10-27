package com.telecomprofi.kickrhiit.ride

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.telecomprofi.kickrhiit.Graph
import com.telecomprofi.kickrhiit.core.Workouts
import com.telecomprofi.kickrhiit.hrm.BleHeartRateMonitor
import com.telecomprofi.kickrhiit.hrm.HeartRateSource
import com.telecomprofi.kickrhiit.hrm.SimulatedHeartRateSource
import com.telecomprofi.kickrhiit.music.WorkoutMusicPlayer
import com.telecomprofi.kickrhiit.trainer.TrainerControl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "RideService"

/**
 * Holds the ride together while the screen is off: owns the engine, the trainer
 * connection, the strap, the music and the voice for one session.
 */
class RideService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.telecomprofi.kickrhiit.START"
        const val ACTION_PAUSE = "com.telecomprofi.kickrhiit.PAUSE"
        const val ACTION_STOP = "com.telecomprofi.kickrhiit.STOP"
        const val ACTION_SKIP = "com.telecomprofi.kickrhiit.SKIP"
        const val ACTION_NEXT_TRACK = "com.telecomprofi.kickrhiit.NEXT_TRACK"
        const val ACTION_TOGGLE_MUSIC = "com.telecomprofi.kickrhiit.TOGGLE_MUSIC"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RideService::class.java).setAction(ACTION_START)
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RideService::class.java).setAction(action))
        }
    }

    private var engine: RideEngine? = null
    private var trainer: TrainerControl? = null
    private var source: HeartRateSource? = null
    private var music: WorkoutMusicPlayer? = null
    private var coach: VoiceCoach? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var musicMuted = false
    private var notificationTick = 0
    private var starting = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                promote(RideHub.state.value)
                if (engine == null && !starting) begin()
            }
            ACTION_PAUSE -> engine?.togglePause()
            ACTION_SKIP -> engine?.skipStep()
            ACTION_NEXT_TRACK -> engine?.nextTrack()
            ACTION_TOGGLE_MUSIC -> engine?.toggleMusic()
            ACTION_STOP -> engine?.stop() ?: stopSelf()
        }
        return START_STICKY
    }

    private fun promote(state: RideUiState) {
        ServiceCompat.startForeground(
            this,
            RideNotifications.ID,
            RideNotifications.build(this, state, musicMuted),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun begin() {
        Graph.init(this)
        starting = true
        lifecycleScope.launch {
            try {
                beginInner()
            } finally {
                starting = false
            }
        }
    }

    private suspend fun beginInner() {
        val settings = Graph.container.prefs.settings.first()
        RideHub.setNote(null)
        acquireWakeLock()

        val workout = Workouts.byId(settings.ride.selectedWorkoutId, settings.ride.sprintResistance)

        val player = if (settings.music.enabled && !settings.music.useExternalPlayer) {
            WorkoutMusicPlayer(this).apply {
                prepare(Graph.container.db.trackDao().allOnce(), settings.music)
                // A file this device can never decode leaves the queue rather
                // than being skipped again on every future ride.
                onTrackUnplayable = { uri, reason ->
                    lifecycleScope.launch {
                        Log.w(TAG, "dropping unplayable track ($reason): $uri")
                        Graph.container.db.trackDao().setMissing(uri, true)
                    }
                }
            }
        } else {
            null
        }
        music = player

        val voice = VoiceCoach(this) { speaking -> player?.duck(speaking) }
        coach = voice

        val bike = settings.devices.trainerAddress?.let { address ->
            TrainerControl(this).apply { setDevice(address, settings.devices.trainerName) }
        }
        trainer = bike
        if (bike == null && !settings.devices.simulate) {
            RideHub.setNote("No trainer paired — pair the KICKR in Settings")
        }

        val hr: HeartRateSource? = when {
            settings.devices.simulate -> null
            settings.devices.hrmAddress != null -> BleHeartRateMonitor(this).apply {
                setDevice(settings.devices.hrmAddress, settings.devices.hrmName)
            }
            else -> null
        }
        source = hr

        val engine = RideEngine(
            scope = lifecycleScope,
            workout = workout,
            rider = settings.rider,
            coachSettings = settings.coach,
            rideSettings = settings.ride,
            trainer = bike,
            hrSource = hr,
            music = player,
            coach = voice,
            simulate = settings.devices.simulate,
            previousBestSprint = Graph.container.rideDao.bestSprintFor(workout.id),
            onFinished = { ride -> onRideFinished(ride) },
        )
        this.engine = engine
        RideHub.attach(engine)

        // Tracks added on the Music tab mid-ride are picked up without a restart.
        player?.let { p ->
            // Music trouble is said out loud rather than being silence.
            lifecycleScope.launch {
                p.problem.collect { message -> if (message != null) RideHub.setNote(message) }
            }
            lifecycleScope.launch {
                Graph.container.db.trackDao().all().collect { tracks ->
                    p.prepare(tracks, settings.music)
                }
            }
            lifecycleScope.launch { p.mutedState.collect { musicMuted = it } }
        }

        lifecycleScope.launch {
            engine.state.collect { state ->
                RideHub.publish(state)
                if (notificationTick++ % 2 == 0) {
                    try {
                        promote(state)
                    } catch (e: Exception) {
                        Log.w(TAG, "notification update failed", e)
                    }
                }
            }
        }

        engine.start()
    }

    private suspend fun onRideFinished(ride: com.telecomprofi.kickrhiit.data.db.RideEntity) {
        // A minute of pedalling is a false start, not a session.
        if (ride.elapsedSec >= 60) {
            val id = Graph.container.rideDao.insert(ride)
            RideHub.publishRide(ride.copy(id = id))
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(9_000)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KickrHiit::ride").apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake lock unavailable", e)
        }
    }

    override fun onDestroy() {
        engine?.let { if (it.isActive) it.stop() }
        RideHub.reset()
        source?.stop()
        trainer?.stop()
        music?.release()
        coach?.shutdown()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "wake lock release failed", e)
        }
        wakeLock = null
        super.onDestroy()
    }
}
