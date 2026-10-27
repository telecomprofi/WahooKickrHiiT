package com.telecomprofi.kickrhiit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One audio file queued for a ride, with its measured tempo and loudness. */
/** Where a track's tempo came from. */
object BpmSource {
    const val MEASURED = 0
    const val TAG = 1
    const val MANUAL = 2
}

/** Below this, a measured tempo is kept but not used for matching. */
const val MIN_TRUSTED_CONFIDENCE = 0.35

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    val bpm: Double,
    val bpmConfidence: Double,
    val analyzedAt: Long,
    val playlistOrder: Int,
    val missing: Boolean = false,
    /** Integrated loudness, LUFS; 0.0 means not measured. */
    val loudnessLufs: Double = 0.0,
    /**
     * Where [bpm] came from: 0 measured from the audio, 1 read from the file's
     * own tag, 2 typed in by hand. Only a measured figure can be wrong in the
     * way beat tracking goes wrong, so only a measured one has to earn trust.
     */
    val bpmSource: Int = BpmSource.MEASURED,
) {
    val displayName: String get() = title ?: fileName.substringBeforeLast('.')
    val analysed: Boolean get() = bpm > 0.0

    /** Trustworthy enough to match a step's cadence to. */
    val tempoTrusted: Boolean
        get() = bpm > 0.0 && (bpmSource != BpmSource.MEASURED || bpmConfidence >= MIN_TRUSTED_CONFIDENCE)

    val bpmSourceLabel: String
        get() = when (bpmSource) {
            BpmSource.TAG -> "from the file's tag"
            BpmSource.MANUAL -> "set by hand"
            else -> "measured"
        }
    val loudnessMeasured: Boolean
        get() = com.telecomprofi.kickrhiit.core.LoudnessNormaliser.isMeasured(loudnessLufs)
}

/** Everything found in the scanned folder, so tracks can be searched for. */
@Entity(tableName = "catalogue")
data class CatalogueEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val relPath: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val scannedAt: Long,
) {
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
    val subtitle: String
        get() = listOfNotNull(artist?.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifEmpty { relPath }
}

/** A completed ride. Enough to see whether last week's sprints were better. */
@Entity(tableName = "rides", indices = [Index("startedAt")])
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val workoutId: String,
    val workoutName: String,
    val plannedSec: Int,
    val elapsedSec: Int,
    val avgPowerW: Int,
    val maxPowerW: Int,
    val normalisedPowerW: Int,
    val workKj: Int,
    val avgCadence: Int,
    val avgHr: Int,
    val maxHr: Int,
    val kcal: Int,
    val ftpAtTime: Int,
    val completed: Boolean,
    /** Best 30-second average power of the ride, which is what a sprint session is for. */
    val bestSprintW: Int = 0,
) {
    val minutes: Int get() = elapsedSec / 60
    /** Intensity factor: normalised power against the FTP of the day. */
    val intensityFactor: Double
        get() = if (ftpAtTime <= 0) 0.0 else normalisedPowerW.toDouble() / ftpAtTime
    /** Training stress, the usual formula. */
    val stressScore: Int
        get() = if (ftpAtTime <= 0) 0 else
            ((elapsedSec * normalisedPowerW * intensityFactor) / (ftpAtTime * 36.0)).toInt()
}
