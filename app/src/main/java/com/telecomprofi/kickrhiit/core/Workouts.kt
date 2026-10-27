package com.telecomprofi.kickrhiit.core

/**
 * The programmes that ship with the app.
 *
 * Written as data rather than code so a new one is a list of steps, not a new
 * screen. Every power figure is a share of FTP; the sprints are the exception
 * and are expressed as a brake setting, because you cannot prescribe a maximum
 * effort in watts — that is the number you are trying to find out.
 */
object Workouts {

    /** Default brake setting for an all-out sprint, adjustable in Settings. */
    const val DEFAULT_SPRINT_RESISTANCE = 65

    private fun warmup(cadence: Int = 90) = listOf(
        Step(
            StepKind.WARMUP, 3 * 60, StepMode.ERG, pctFtp = 0.45, cadenceRpm = cadence,
            label = "Easy spin", cue = "Easy spin to start. Let the legs wake up.",
        ),
        Step(
            StepKind.WARMUP, 3 * 60, StepMode.ERG, pctFtp = 0.55, toPctFtp = 0.72,
            cadenceRpm = cadence, label = "Ramp",
            cue = "Building now. The resistance will climb on its own.",
        ),
        Step(
            StepKind.WARMUP, 60, StepMode.ERG, pctFtp = 0.90, cadenceRpm = 100,
            label = "Opener", cue = "One minute opener. Lift the cadence to a hundred.",
        ),
        Step(
            StepKind.WARMUP, 60, StepMode.ERG, pctFtp = 0.45, cadenceRpm = 85,
            label = "Settle", cue = "Spin it out. The first sprint is coming.",
        ),
    )

    private fun cooldown(minutes: Int = 5) = listOf(
        Step(
            StepKind.COOLDOWN, (minutes - 2) * 60, StepMode.ERG, pctFtp = 0.45,
            cadenceRpm = 85, label = "Cool-down", cue = "That is the work done. Spin easy.",
        ),
        Step(
            StepKind.COOLDOWN, 2 * 60, StepMode.ERG, pctFtp = 0.38, toPctFtp = 0.33,
            cadenceRpm = 80, label = "Wind down", cue = "Last two minutes. Bring the heart rate down.",
        ),
    )

    private fun sprintSet(count: Int, recoverySec: Int, resistance: Int): List<Step> =
        buildList {
            for (i in 1..count) {
                add(
                    Step(
                        StepKind.SPRINT, 30, StepMode.RESISTANCE, resistance = resistance,
                        cadenceRpm = 110, label = "Sprint $i of $count",
                        cue = when (i) {
                            1 -> "Sprint one. Everything you have. Go!"
                            count -> "Last one. Empty the tank. Go!"
                            else -> "Sprint $i. Go!"
                        },
                    )
                )
                add(
                    Step(
                        StepKind.RECOVERY, recoverySec, StepMode.ERG, pctFtp = 0.45,
                        cadenceRpm = 85, label = "Recovery $i",
                        cue = "Ease off. Keep the legs turning, do not stop.",
                    )
                )
            }
        }

    /**
     * The classic sprint-interval session: six thirty-second maximal efforts on
     * four minutes of recovery each. The long recovery is not generosity — it is
     * what lets the sixth sprint be as hard as the first, which is the whole
     * point of the protocol.
     */
    fun sit6x30(resistance: Int = DEFAULT_SPRINT_RESISTANCE) = Workout(
        id = "sit-6x30",
        name = "SIT 6 × 30″",
        subtitle = "All-out sprints · 4 min recovery",
        blurb = "Six thirty-second maximal efforts with four minutes of easy spinning between " +
            "them. The sprints run in resistance mode, not ERG, so the trainer sets the brake " +
            "and you produce whatever you can against it. Recovery is held at 45% of FTP.",
        steps = warmup() + sprintSet(6, 4 * 60, resistance) + cooldown(5),
    )

    /** The same shape, half the sprints, for a day with less time in it. */
    fun quickSit4x30(resistance: Int = DEFAULT_SPRINT_RESISTANCE) = Workout(
        id = "sit-4x30",
        name = "Quick SIT 4 × 30″",
        subtitle = "Short session · 3 min recovery",
        blurb = "Four sprints on three minutes, warm-up and cool-down trimmed. Twenty-four " +
            "minutes door to door, and still the hardest two minutes of your week.",
        steps = listOf(
            Step(
                StepKind.WARMUP, 4 * 60, StepMode.ERG, pctFtp = 0.45, toPctFtp = 0.65,
                cadenceRpm = 90, label = "Warm-up", cue = "Four minutes to warm up. Building gently.",
            ),
            Step(
                StepKind.WARMUP, 60, StepMode.ERG, pctFtp = 0.90, cadenceRpm = 100,
                label = "Opener", cue = "Opener. Lift the cadence.",
            ),
            Step(
                StepKind.WARMUP, 60, StepMode.ERG, pctFtp = 0.45, cadenceRpm = 85,
                label = "Settle", cue = "Spin easy. First sprint coming.",
            ),
        ) + sprintSet(4, 3 * 60, resistance) + cooldown(4),
    )

    /**
     * Over-unders then a threshold pyramid. Every step is ERG, because holding
     * a number is exactly the skill this session trains — the discomfort of
     * 105% is the discomfort of not being allowed to ease off.
     */
    val pyramid = Workout(
        id = "pyramid-over-under",
        name = "Pyramid Over-Unders",
        subtitle = "Threshold · 45 min",
        blurb = "Three over-under cycles either side of threshold, four minutes of recovery, " +
            "then a pyramid that walks up through sweet spot and over the top and back down. " +
            "Held in ERG throughout: keep the cadence steady and let the trainer do the rest.",
        steps = buildList {
            // Warm-up, 10 minutes.
            add(Step(StepKind.WARMUP, 5 * 60, StepMode.ERG, pctFtp = 0.50, cadenceRpm = 90,
                label = "Easy spin", cue = "Five minutes easy. Ninety revolutions a minute."))
            add(Step(StepKind.WARMUP, 2 * 60, StepMode.ERG, pctFtp = 0.60, toPctFtp = 0.80,
                cadenceRpm = 90, label = "Ramp", cue = "Ramping to eighty per cent."))
            add(Step(StepKind.WARMUP, 60, StepMode.ERG, pctFtp = 1.05, cadenceRpm = 100,
                label = "Opener", cue = "One minute above threshold. High cadence."))
            add(Step(StepKind.WARMUP, 2 * 60, StepMode.ERG, pctFtp = 0.50, cadenceRpm = 90,
                label = "Recover", cue = "Spin light. The main set starts shortly."))
            // Over-unders, 3 cycles of 2 + 2.
            for (i in 1..3) {
                add(Step(StepKind.WORK, 2 * 60, StepMode.ERG, pctFtp = 0.88, cadenceRpm = 88,
                    label = "Under $i of 3",
                    cue = if (i == 1) "Under. Eighty-eight per cent — this is the easy half." else "Under. Settle in."))
                add(Step(StepKind.WORK, 2 * 60, StepMode.ERG, pctFtp = 1.05, cadenceRpm = 98,
                    label = "Over $i of 3",
                    cue = if (i == 3) "Last over. Two minutes, hold it." else "Over. Lift the cadence and hold."))
            }
            add(Step(StepKind.RECOVERY, 4 * 60, StepMode.ERG, pctFtp = 0.50, cadenceRpm = 90,
                label = "Recovery", cue = "Four minutes easy. Drink something."))
            // Threshold pyramid.
            add(Step(StepKind.WORK, 3 * 60, StepMode.ERG, pctFtp = 0.90, cadenceRpm = 90,
                label = "Sweet spot", cue = "Sweet spot. Ninety per cent for three minutes."))
            add(Step(StepKind.WORK, 3 * 60, StepMode.ERG, pctFtp = 1.00, cadenceRpm = 92,
                label = "Threshold", cue = "Threshold now. This is your hour pace."))
            add(Step(StepKind.WORK, 3 * 60, StepMode.ERG, pctFtp = 1.08, cadenceRpm = 95,
                label = "Over threshold", cue = "Over the top. Three minutes, the hardest of the day."))
            add(Step(StepKind.WORK, 3 * 60, StepMode.ERG, pctFtp = 0.90, cadenceRpm = 90,
                label = "Sweet spot", cue = "Back to sweet spot. Ride it out."))
            // Cool-down, 7 minutes.
            add(Step(StepKind.COOLDOWN, 4 * 60, StepMode.ERG, pctFtp = 0.48, cadenceRpm = 85,
                label = "Cool-down", cue = "Work is done. Seven minutes to spin out."))
            add(Step(StepKind.COOLDOWN, 3 * 60, StepMode.ERG, pctFtp = 0.42, toPctFtp = 0.38,
                cadenceRpm = 80, label = "Wind down", cue = "Easing off to the finish."))
        },
    )

    /** Everything on the picker, in the order it is shown. */
    fun all(sprintResistance: Int = DEFAULT_SPRINT_RESISTANCE): List<Workout> = listOf(
        sit6x30(sprintResistance),
        pyramid,
        quickSit4x30(sprintResistance),
    )

    fun byId(id: String, sprintResistance: Int = DEFAULT_SPRINT_RESISTANCE): Workout =
        all(sprintResistance).firstOrNull { it.id == id } ?: sit6x30(sprintResistance)
}
