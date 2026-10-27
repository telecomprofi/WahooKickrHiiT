package com.telecomprofi.kickrhiit.ride

import com.telecomprofi.kickrhiit.core.EffortTrace
import com.telecomprofi.kickrhiit.core.RideMetrics
import com.telecomprofi.kickrhiit.core.RiderProfile
import com.telecomprofi.kickrhiit.core.Step
import com.telecomprofi.kickrhiit.core.StepKind
import com.telecomprofi.kickrhiit.core.StepMode
import com.telecomprofi.kickrhiit.core.TracePoint
import com.telecomprofi.kickrhiit.core.Workout
import com.telecomprofi.kickrhiit.data.db.RideEntity
import com.telecomprofi.kickrhiit.data.prefs.CoachSettings
import com.telecomprofi.kickrhiit.data.prefs.RideSettings
import com.telecomprofi.kickrhiit.hrm.HeartRateSource
import com.telecomprofi.kickrhiit.hrm.HrmState
import com.telecomprofi.kickrhiit.music.NowPlaying
import com.telecomprofi.kickrhiit.music.WorkoutMusicPlayer
import com.telecomprofi.kickrhiit.trainer.TrainerControl
import com.telecomprofi.kickrhiit.trainer.TrainerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Seconds without a reading before the trainer counts as silent. */
private const val TRAINER_STALE_MS = 6_000L
private const val HR_STALE_MS = 8_000L

/** Only rewrite the ERG target when it has moved by more than this. */
private const val ERG_DEADBAND_W = 3

/**
 * Runs one workout: walks the step list, drives the trainer, folds power,
 * cadence and heart rate into the running totals, and tells the coach what to
 * say.
 *
 * The interesting part is that two different things are being asked of the
 * trainer. Most steps are ERG — hold this wattage, whatever my cadence — and
 * the engine simply writes a target each time it changes. A sprint is the
 * opposite: the brake is set once to a fixed level and then left alone, because
 * the whole point is to find out what you can produce, and ERG would cap it at
 * the number we guessed.
 */
class RideEngine(
    private val scope: CoroutineScope,
    val workout: Workout,
    private val rider: RiderProfile,
    private val coachSettings: CoachSettings,
    private val rideSettings: RideSettings,
    private val trainer: TrainerControl?,
    private val hrSource: HeartRateSource?,
    private val music: WorkoutMusicPlayer?,
    private val coach: VoiceCoach?,
    private val simulate: Boolean = false,
    private val previousBestSprint: Int = 0,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFinished: suspend (RideEntity) -> Unit,
) {
    private val metrics = RideMetrics()
    private val trace = EffortTrace()

    private val _state = MutableStateFlow(
        RideUiState(
            workoutId = workout.id,
            workoutName = workout.name,
            totalSec = workout.totalSec,
            stepCount = workout.steps.size,
        )
    )
    val state: StateFlow<RideUiState> = _state.asStateFlow()

    private var status = RideStatus.IDLE
    private var startedAt = 0L
    private var pausedAtWall = 0L
    private var pausedMs = 0L

    private var lastPower: Int? = null
    private var lastCadence: Int? = null
    private var lastTrainerAt = 0L
    private var trainerState: TrainerState = TrainerState.Idle
    private var trainerWasLost = false
    private var noControlWarned = false

    private var lastHr: Int? = null
    private var lastHrAt = 0L
    private var hrmState: HrmState = HrmState.Idle

    private var currentStepIndex = -1
    private var lastErgTarget = -1
    private var countdownSpokenFor = -1
    private var halfwaySpoken = false
    /** Best 30 s in the ride so far when the last sprint began, to call a new best. */
    private var bestBeforeStep = 0
    private var nowPlaying: NowPlaying? = null
    private var musicStatus = com.telecomprofi.kickrhiit.music.MusicStatus()
    private var musicMuted = false

    private var tickJob: Job? = null
    private val watchers = mutableListOf<Job>()

    val isActive: Boolean get() = status == RideStatus.RUNNING || status == RideStatus.PAUSED

    fun start() {
        if (status != RideStatus.IDLE) return
        startedAt = clock()
        status = RideStatus.RUNNING

        coach?.setSpeechRate(coachSettings.speechRate)

        hrSource?.let { source ->
            source.start()
            watchers += scope.launch {
                source.samples.collect { sample ->
                    lastHr = sample.hr
                    lastHrAt = clock()
                }
            }
            watchers += scope.launch { source.state.collect { hrmState = it; publish() } }
        }

        trainer?.let { t ->
            t.start()
            watchers += scope.launch {
                t.data.collect { bike ->
                    bike.powerW?.let { lastPower = it }
                    bike.cadenceRpm?.let { lastCadence = it.roundToInt() }
                    lastTrainerAt = clock()
                    if (trainerWasLost) {
                        trainerWasLost = false
                        say(Cues.trainerBack())
                    }
                }
            }
            watchers += scope.launch {
                t.state.collect { s ->
                    trainerState = s
                    if (s is TrainerState.Connected) {
                        if (s.hasControl) {
                            // Re-assert on reconnect: a trainer that dropped and
                            // came back is sitting wherever it defaulted to.
                            lastErgTarget = -1
                            workout.stepAt(elapsed())?.let { applyStep(it, announce = false) }
                        } else if (!noControlWarned) {
                            noControlWarned = true
                            say(Cues.noControl())
                        }
                    }
                    publish()
                }
            }
        }

        music?.let { player ->
            watchers += scope.launch { player.nowPlaying.collect { nowPlaying = it; publish() } }
            watchers += scope.launch { player.status.collect { musicStatus = it; publish() } }
            watchers += scope.launch { player.mutedState.collect { musicMuted = it; publish() } }
        }

        say(Cues.rideStart(workout.name, workout.minutes, workout.sprintCount), interrupt = true)
        workout.steps.firstOrNull()?.let { enterStep(0, it) }

        tickJob = scope.launch {
            while (isActive) {
                delay(500)
                if (status == RideStatus.RUNNING) tick()
            }
        }
        publish()
    }

    private fun say(text: String, interrupt: Boolean = false) {
        if (!coachSettings.voiceEnabled) return
        coach?.say(text, interrupt)
    }

    private fun elapsed(): Int {
        if (startedAt == 0L) return 0
        if (status == RideStatus.FINISHED) return metrics.elapsedSec
        val extra = if (status == RideStatus.PAUSED) clock() - pausedAtWall else 0L
        return (((clock() - startedAt) - pausedMs - extra) / 1000L).toInt().coerceAtLeast(0)
    }

    private fun freshPower(): Int? = when {
        simulate -> simulatedPower()
        lastPower != null && clock() - lastTrainerAt < TRAINER_STALE_MS -> lastPower
        else -> null
    }

    private fun freshCadence(): Int? = when {
        simulate -> simulatedCadence()
        lastCadence != null && clock() - lastTrainerAt < TRAINER_STALE_MS -> lastCadence
        else -> null
    }

    private fun freshHr(): Int? = when {
        lastHr != null && clock() - lastHrAt < HR_STALE_MS -> lastHr
        simulate -> simulatedHr()
        else -> null
    }

    // --- the simulator, so the app can be tried off the bike -----------------

    private var simHr = 95.0

    private fun simulatedPower(): Int {
        val step = workout.stepAt(elapsed()) ?: return 0
        val into = workout.elapsedInStep(elapsed())
        return if (step.mode == StepMode.ERG) {
            (step.targetWatts(rider, into) * (0.97 + Random.nextDouble() * 0.06)).roundToInt()
        } else {
            // A sprint: enormous, then fading, the way one actually goes.
            val decay = 1.0 - 0.35 * (into.toDouble() / step.seconds.coerceAtLeast(1))
            ((rider.ftpWatts * 2.6) * decay * (0.95 + Random.nextDouble() * 0.1)).roundToInt()
        }
    }

    private fun simulatedCadence(): Int {
        val step = workout.stepAt(elapsed()) ?: return 0
        return (step.cadenceRpm + Random.nextInt(-4, 5)).coerceAtLeast(0)
    }

    private fun simulatedHr(): Int {
        val step = workout.stepAt(elapsed())
        val pct = if (step == null) 0.4 else if (step.isSprint) 1.6 else step.pctAt(workout.elapsedInStep(elapsed()))
        val aim = rider.restingHr + rider.hrReserve * (pct * 0.72).coerceIn(0.15, 1.0)
        simHr += (aim - simHr) * 0.06
        return simHr.roundToInt()
    }

    // --- the tick ------------------------------------------------------------

    private fun tick() {
        val now = elapsed()
        val dt = now - metrics.elapsedSec
        if (dt <= 0) {
            publish()
            return
        }

        val index = workout.indexAt(now)
        if (index < 0) {
            finish(completed = true)
            return
        }
        val step = workout.steps[index]
        val into = workout.elapsedInStep(now)

        val power = freshPower()
        val cadence = freshCadence()
        val hr = freshHr()

        if (power == null && lastTrainerAt != 0L && !trainerWasLost && !simulate) {
            trainerWasLost = true
            say(Cues.trainerLost())
        }

        metrics.tick(dt, power, cadence, hr)
        trace.add(
            TracePoint(
                tSec = now,
                targetW = if (step.mode == StepMode.ERG) step.targetWatts(rider, into) else 0,
                powerW = power ?: 0,
                cadenceRpm = cadence ?: 0,
                hr = hr ?: 0,
            )
        )

        if (index != currentStepIndex) enterStep(index, step) else holdStep(step, into)

        maybeCountdown(now, index, step)

        if (!halfwaySpoken && now >= workout.totalSec / 2) {
            halfwaySpoken = true
            say(Cues.halfway())
        }

        publish()
        if (workout.isFinished(now)) finish(completed = true)
    }

    /**
     * A step has begun: set the trainer, tell the rider, point the music at the
     * new cadence.
     */
    private fun enterStep(index: Int, step: Step) {
        // Report the sprint that just ended before announcing the next thing.
        val previous = workout.steps.getOrNull(currentStepIndex)
        if (previous?.isSprint == true) {
            val achieved = metrics.bestThirtySecondW
            if (achieved > 0) {
                say(Cues.sprintResult(achieved, maxOf(previousBestSprint, bestBeforeStep)))
            }
        }
        if (step.isSprint) bestBeforeStep = metrics.bestThirtySecondW

        currentStepIndex = index
        countdownSpokenFor = -1
        lastErgTarget = -1
        applyStep(step, announce = coachSettings.announceEveryStep)
        music?.setTargetCadence(step.cadenceRpm, switchTrack = true)
    }

    /** Mid-step: follow any ramp, but only write when the number really moved. */
    private fun holdStep(step: Step, into: Int) {
        if (step.mode != StepMode.ERG) return
        val target = step.targetWatts(rider, into)
        if (abs(target - lastErgTarget) < ERG_DEADBAND_W) return
        lastErgTarget = target
        trainer?.setTargetPower(target)
    }

    private fun applyStep(step: Step, announce: Boolean) {
        when (step.mode) {
            StepMode.ERG -> {
                val target = step.targetWatts(rider, 0)
                lastErgTarget = target
                trainer?.setTargetPower(target)
                if (announce) {
                    val text = step.cue.ifBlank { Cues.ergTarget(target, step.cadenceRpm) }
                    say(text, interrupt = true)
                }
            }
            StepMode.RESISTANCE -> {
                val level = if (step.resistance > 0) step.resistance else rideSettings.sprintResistance
                trainer?.setTargetResistance(level)
                if (announce) say(step.cue.ifBlank { Cues.freeRide() }, interrupt = true)
            }
            StepMode.FREE -> {
                if (announce) say(step.cue.ifBlank { Cues.freeRide() }, interrupt = true)
            }
        }
    }

    /**
     * Count into every work step. Thirty seconds is short enough that a late
     * start is a meaningful share of the effort, so the numbers matter.
     */
    private fun maybeCountdown(now: Int, index: Int, step: Step) {
        val seconds = coachSettings.countdownSeconds
        if (seconds <= 0) return
        val next = workout.steps.getOrNull(index + 1) ?: return
        if (next.kind != StepKind.SPRINT && next.kind != StepKind.WORK) return
        val remaining = workout.remainingInStep(now)
        if (remaining > seconds) return
        if (countdownSpokenFor == index && remaining > 1) {
            if (remaining in 1..3) say(Cues.countdown(remaining), interrupt = true)
            return
        }
        if (countdownSpokenFor != index) {
            countdownSpokenFor = index
            say(Cues.sprintImminent(), interrupt = true)
        }
    }

    fun togglePause() {
        when (status) {
            RideStatus.RUNNING -> {
                status = RideStatus.PAUSED
                pausedAtWall = clock()
                music?.pause()
                coach?.stopSpeaking()
                // Take the load off rather than leaving it pinned while you rest.
                trainer?.setTargetPower(rider.watts(0.35))
                publish()
            }
            RideStatus.PAUSED -> {
                pausedMs += clock() - pausedAtWall
                status = RideStatus.RUNNING
                lastErgTarget = -1
                music?.play()
                publish()
            }
            else -> Unit
        }
    }

    /** Jump to the next step, crediting nothing extra. */
    fun skipStep() {
        val now = elapsed()
        val index = workout.indexAt(now)
        if (index < 0) return
        val jump = workout.remainingInStep(now)
        if (jump > 0) startedAt -= jump * 1000L
        tick()
    }

    fun nextTrack() = music?.nextTrack()

    fun toggleMusic() {
        val player = music ?: return
        player.setMuted(!player.isMuted)
    }

    fun stop() {
        if (!isActive) return
        finish(completed = workout.isFinished(elapsed()))
    }

    private fun finish(completed: Boolean) {
        if (status == RideStatus.FINISHED) return
        status = RideStatus.FINISHED
        tickJob?.cancel()
        watchers.forEach { it.cancel() }
        watchers.clear()
        hrSource?.stop()
        // Release the brake rather than leaving the last target dialled in.
        trainer?.stop()
        music?.pause()

        val ride = RideEntity(
            startedAt = startedAt,
            endedAt = clock(),
            workoutId = workout.id,
            workoutName = workout.name,
            plannedSec = workout.totalSec,
            elapsedSec = metrics.elapsedSec,
            avgPowerW = metrics.avgPowerW,
            maxPowerW = metrics.maxPowerW,
            normalisedPowerW = metrics.normalisedPowerW,
            workKj = metrics.workKj.roundToInt(),
            avgCadence = metrics.avgCadence,
            avgHr = metrics.avgHr,
            maxHr = metrics.maxHr,
            kcal = metrics.kcal,
            ftpAtTime = rider.ftpWatts,
            completed = completed,
            bestSprintW = metrics.bestThirtySecondW,
        )
        say(
            Cues.finished(
                minutes = ride.minutes,
                kj = ride.workKj,
                np = ride.normalisedPowerW,
                best = if (workout.sprintCount > 0) ride.bestSprintW else 0,
            ),
            interrupt = true,
        )
        publish()
        scope.launch { onFinished(ride) }
    }

    private fun publish() {
        val now = elapsed()
        val index = workout.indexAt(now)
        val step = workout.steps.getOrNull(index)
        val into = if (index < 0) 0 else workout.elapsedInStep(now)
        val connected = trainerState is TrainerState.Connected
        val controlling = (trainerState as? TrainerState.Connected)?.hasControl == true

        _state.value = RideUiState(
            status = status,
            workoutId = workout.id,
            workoutName = workout.name,
            elapsedSec = now,
            totalSec = workout.totalSec,
            step = step,
            stepIndex = index.coerceAtLeast(0),
            stepCount = workout.steps.size,
            stepElapsedSec = into,
            stepRemainingSec = workout.remainingInStep(now),
            nextStep = workout.nextStep(now),
            countdown = countdownRemaining(now, index),
            targetW = if (step?.mode == StepMode.ERG) step.targetWatts(rider, into) else 0,
            resistanceTarget = if (step?.mode == StepMode.RESISTANCE) {
                if (step.resistance > 0) step.resistance else rideSettings.sprintResistance
            } else {
                0
            },
            powerW = freshPower() ?: 0,
            cadenceRpm = freshCadence() ?: 0,
            hr = freshHr() ?: 0,
            avgPowerW = metrics.avgPowerW,
            maxPowerW = metrics.maxPowerW,
            normalisedPowerW = metrics.normalisedPowerW,
            workKj = metrics.workKj,
            kcal = metrics.kcal,
            avgCadence = metrics.avgCadence,
            avgHr = metrics.avgHr,
            bestThirtyW = metrics.bestThirtySecondW,
            intensityFactor = metrics.intensityFactor(rider.ftpWatts),
            trace = trace.window(rideSettings.graphWindowSec),
            trainerConnected = connected || simulate,
            trainerControlling = controlling || simulate,
            trainerNote = (trainerState as? TrainerState.Failed)?.message,
            hrmConnected = hrmState.isLive || simulate,
            simulated = simulate,
            nowPlaying = nowPlaying,
            musicStatus = musicStatus,
            musicMuted = musicMuted,
        )
    }

    private fun countdownRemaining(now: Int, index: Int): Int {
        if (index < 0) return 0
        val next = workout.steps.getOrNull(index + 1) ?: return 0
        if (next.kind != StepKind.SPRINT && next.kind != StepKind.WORK) return 0
        val remaining = workout.remainingInStep(now)
        return if (remaining <= coachSettings.countdownSeconds) remaining else 0
    }
}
