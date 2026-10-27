package com.telecomprofi.kickrhiit.core

import kotlin.math.roundToInt

/**
 * The rider. Everything a structured workout needs to turn percentages into
 * watts, and heart rate into zones.
 *
 * FTP is typed in rather than tested for: a 20-minute test is its own miserable
 * afternoon, and a number you adjust when the workouts start feeling wrong is
 * good enough for interval training. It is the one figure the whole plan scales
 * from, so it lives at the top of Settings.
 */
data class RiderProfile(
    /** Functional threshold power, watts. The number every workout scales from. */
    val ftpWatts: Int = 200,
    val weightKg: Double = 80.0,
    val ageYears: Int = 40,
    val restingHr: Int = 60,
    val measuredMaxHr: Int = 0,
) {
    /** Tanaka et al.; better than 220 − age past forty. */
    val maxHr: Int
        get() = if (measuredMaxHr > restingHr + 20) measuredMaxHr else (208.0 - 0.7 * ageYears).roundToInt()

    val hrReserve: Int get() = (maxHr - restingHr).coerceAtLeast(1)

    /** Watts for a share of FTP, which is how every step is written. */
    fun watts(pctFtp: Double): Int = (ftpWatts * pctFtp).roundToInt().coerceIn(0, 2000)

    /** Watts per kilo, the number cyclists actually compare. */
    val wattsPerKg: Double get() = if (weightKg <= 0) 0.0 else ftpWatts / weightKg

    fun hrPercent(hr: Int): Double = ((hr - restingHr).toDouble() / hrReserve).coerceIn(0.0, 1.5)

    fun isPlausible(): Boolean =
        ftpWatts in 50..600 && weightKg in 30.0..250.0 && ageYears in 10..100 && restingHr in 30..110
}

/**
 * Power zones, Coggan's, as a share of FTP. Used to colour the effort graph and
 * the gauge so a glance says which system is being worked.
 */
enum class PowerZone(
    val label: String,
    val shortLabel: String,
    val lowPct: Double,
    val highPct: Double,
    val argb: Long,
) {
    RECOVERY("Recovery", "Z1", 0.00, 0.55, 0xFF4A9FD8),
    ENDURANCE("Endurance", "Z2", 0.55, 0.75, 0xFF3FB55C),
    TEMPO("Tempo", "Z3", 0.75, 0.90, 0xFFC9C516),
    THRESHOLD("Threshold", "Z4", 0.90, 1.05, 0xFFE08A1E),
    VO2MAX("VO2 max", "Z5", 1.05, 1.20, 0xFFD9531E),
    ANAEROBIC("Anaerobic", "Z6", 1.20, 1.50, 0xFFC02020),
    NEUROMUSCULAR("Sprint", "Z7", 1.50, 9.99, 0xFF8E2ABF),
    ;

    companion object {
        fun forPct(pct: Double): PowerZone = entries.firstOrNull { pct < it.highPct } ?: NEUROMUSCULAR

        fun forWatts(watts: Int, profile: RiderProfile): PowerZone =
            forPct(if (profile.ftpWatts <= 0) 0.0 else watts.toDouble() / profile.ftpWatts)
    }
}
