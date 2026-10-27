package com.telecomprofi.kickrhiit.data.prefs

import com.telecomprofi.kickrhiit.core.RiderProfile
import com.telecomprofi.kickrhiit.core.Workouts

data class MusicSettings(
    val musicTreeUri: String? = null,
    val enabled: Boolean = true,
    val useExternalPlayer: Boolean = false,
    val matchBpm: Boolean = true,
    val maxStretchPercent: Int = 8,
    val allowOctaveMatch: Boolean = true,
    val volumePercent: Int = 80,
    /**
     * How loud the music stays while the coach talks, as a percentage. Low on
     * purpose: a spoken sprint countdown that arrives under the music is worse
     * than no countdown, because you brace for the wrong second.
     */
    val duckPercent: Int = 6,
    val normaliseLoudness: Boolean = true,
    val maxCutDb: Int = 8,
    val maxBoostDb: Int = 6,
)

data class DeviceSettings(
    val trainerAddress: String? = null,
    val trainerName: String? = null,
    val hrmAddress: String? = null,
    val hrmName: String? = null,
    /** Feed synthetic power and heart rate so the app can be tried off the bike. */
    val simulate: Boolean = false,
)

data class CoachSettings(
    val voiceEnabled: Boolean = true,
    /** Counted down before every work step, because a sprint start has to be exact. */
    val countdownSeconds: Int = 5,
    val speechRate: Float = 1.05f,
    val announceEveryStep: Boolean = true,
)

data class RideSettings(
    /** Brake setting used for all-out sprints, 0-100. */
    val sprintResistance: Int = Workouts.DEFAULT_SPRINT_RESISTANCE,
    /** Which programme the Start button will run. */
    val selectedWorkoutId: String = "sit-6x30",
    val keepScreenOn: Boolean = true,
    /** Seconds of trace kept on the effort graph. */
    val graphWindowSec: Int = 300,
)

data class AppSettings(
    val rider: RiderProfile = RiderProfile(),
    val devices: DeviceSettings = DeviceSettings(),
    val music: MusicSettings = MusicSettings(),
    val coach: CoachSettings = CoachSettings(),
    val ride: RideSettings = RideSettings(),
    val onboarded: Boolean = false,
)
