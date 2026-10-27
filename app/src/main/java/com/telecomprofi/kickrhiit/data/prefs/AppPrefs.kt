package com.telecomprofi.kickrhiit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.telecomprofi.kickrhiit.core.RiderProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kickr_hiit_settings")

/** Every setting, on the device, in one store. Nothing leaves the phone. */
class AppPrefs(private val context: Context) {

    private object K {
        val ftp = intPreferencesKey("ftp_watts")
        val weight = doublePreferencesKey("weight_kg")
        val age = intPreferencesKey("age")
        val restingHr = intPreferencesKey("resting_hr")
        val maxHr = intPreferencesKey("measured_max_hr")

        val trainerAddress = stringPreferencesKey("trainer_address")
        val trainerName = stringPreferencesKey("trainer_name")
        val hrmAddress = stringPreferencesKey("hrm_address")
        val hrmName = stringPreferencesKey("hrm_name")
        val simulate = booleanPreferencesKey("simulate")

        val musicTree = stringPreferencesKey("music_tree_uri")
        val musicEnabled = booleanPreferencesKey("music_enabled")
        val externalPlayer = booleanPreferencesKey("external_player")
        val matchBpm = booleanPreferencesKey("match_bpm")
        val maxStretch = intPreferencesKey("max_stretch")
        val octave = booleanPreferencesKey("octave_match")
        val volume = intPreferencesKey("volume_pct")
        val duck = intPreferencesKey("duck_pct")
        val normalise = booleanPreferencesKey("normalise_loudness")
        val maxCut = intPreferencesKey("level_max_cut_db")
        val maxBoost = intPreferencesKey("level_max_boost_db")

        val voice = booleanPreferencesKey("voice_enabled")
        val countdown = intPreferencesKey("countdown_sec")
        val speechRate = floatPreferencesKey("speech_rate")
        val announceSteps = booleanPreferencesKey("announce_steps")

        val sprintResistance = intPreferencesKey("sprint_resistance")
        val selectedWorkout = stringPreferencesKey("selected_workout")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val graphWindow = intPreferencesKey("graph_window_sec")

        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            rider = RiderProfile(
                ftpWatts = this[K.ftp] ?: d.rider.ftpWatts,
                weightKg = this[K.weight] ?: d.rider.weightKg,
                ageYears = this[K.age] ?: d.rider.ageYears,
                restingHr = this[K.restingHr] ?: d.rider.restingHr,
                measuredMaxHr = this[K.maxHr] ?: d.rider.measuredMaxHr,
            ),
            devices = DeviceSettings(
                trainerAddress = this[K.trainerAddress],
                trainerName = this[K.trainerName],
                hrmAddress = this[K.hrmAddress],
                hrmName = this[K.hrmName],
                simulate = this[K.simulate] ?: d.devices.simulate,
            ),
            music = MusicSettings(
                musicTreeUri = this[K.musicTree],
                enabled = this[K.musicEnabled] ?: d.music.enabled,
                useExternalPlayer = this[K.externalPlayer] ?: d.music.useExternalPlayer,
                matchBpm = this[K.matchBpm] ?: d.music.matchBpm,
                maxStretchPercent = this[K.maxStretch] ?: d.music.maxStretchPercent,
                allowOctaveMatch = this[K.octave] ?: d.music.allowOctaveMatch,
                volumePercent = this[K.volume] ?: d.music.volumePercent,
                duckPercent = this[K.duck] ?: d.music.duckPercent,
                normaliseLoudness = this[K.normalise] ?: d.music.normaliseLoudness,
                maxCutDb = this[K.maxCut] ?: d.music.maxCutDb,
                maxBoostDb = this[K.maxBoost] ?: d.music.maxBoostDb,
            ),
            coach = CoachSettings(
                voiceEnabled = this[K.voice] ?: d.coach.voiceEnabled,
                countdownSeconds = this[K.countdown] ?: d.coach.countdownSeconds,
                speechRate = this[K.speechRate] ?: d.coach.speechRate,
                announceEveryStep = this[K.announceSteps] ?: d.coach.announceEveryStep,
            ),
            ride = RideSettings(
                sprintResistance = this[K.sprintResistance] ?: d.ride.sprintResistance,
                selectedWorkoutId = this[K.selectedWorkout] ?: d.ride.selectedWorkoutId,
                keepScreenOn = this[K.keepScreenOn] ?: d.ride.keepScreenOn,
                graphWindowSec = this[K.graphWindow] ?: d.ride.graphWindowSec,
            ),
            onboarded = this[K.onboarded] ?: false,
        )
    }

    suspend fun updateRider(rider: RiderProfile) = context.dataStore.edit {
        it[K.ftp] = rider.ftpWatts
        it[K.weight] = rider.weightKg
        it[K.age] = rider.ageYears
        it[K.restingHr] = rider.restingHr
        it[K.maxHr] = rider.measuredMaxHr
    }

    suspend fun updateDevices(devices: DeviceSettings) = context.dataStore.edit {
        devices.trainerAddress?.let { a -> it[K.trainerAddress] = a } ?: it.remove(K.trainerAddress)
        devices.trainerName?.let { n -> it[K.trainerName] = n } ?: it.remove(K.trainerName)
        devices.hrmAddress?.let { a -> it[K.hrmAddress] = a } ?: it.remove(K.hrmAddress)
        devices.hrmName?.let { n -> it[K.hrmName] = n } ?: it.remove(K.hrmName)
        it[K.simulate] = devices.simulate
    }

    suspend fun updateMusic(music: MusicSettings) = context.dataStore.edit {
        music.musicTreeUri?.let { uri -> it[K.musicTree] = uri } ?: it.remove(K.musicTree)
        it[K.musicEnabled] = music.enabled
        it[K.externalPlayer] = music.useExternalPlayer
        it[K.matchBpm] = music.matchBpm
        it[K.maxStretch] = music.maxStretchPercent
        it[K.octave] = music.allowOctaveMatch
        it[K.volume] = music.volumePercent
        it[K.duck] = music.duckPercent
        it[K.normalise] = music.normaliseLoudness
        it[K.maxCut] = music.maxCutDb
        it[K.maxBoost] = music.maxBoostDb
    }

    suspend fun updateCoach(coach: CoachSettings) = context.dataStore.edit {
        it[K.voice] = coach.voiceEnabled
        it[K.countdown] = coach.countdownSeconds
        it[K.speechRate] = coach.speechRate
        it[K.announceSteps] = coach.announceEveryStep
    }

    suspend fun updateRide(ride: RideSettings) = context.dataStore.edit {
        it[K.sprintResistance] = ride.sprintResistance
        it[K.selectedWorkout] = ride.selectedWorkoutId
        it[K.keepScreenOn] = ride.keepScreenOn
        it[K.graphWindow] = ride.graphWindowSec
    }

    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[K.onboarded] = value }
}
