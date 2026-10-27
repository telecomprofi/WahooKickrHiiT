package com.telecomprofi.kickrhiit.music

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.telecomprofi.kickrhiit.core.LoudnessNormaliser
import com.telecomprofi.kickrhiit.data.db.TrackEntity
import com.telecomprofi.kickrhiit.data.prefs.MusicSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

private const val TAG = "MusicPlayer"

/** How hard the player tries to read a file before it reports a failure. */
private const val IO_RETRIES = 6

/** Attempts at one track before the player gives up on it for this session. */
private const val RETRY_LIMIT = 3

/** Long enough for a sleeping card to wake, short enough to go unnoticed. */
private const val RETRY_DELAY_MS = 700L

/** No cue in this app runs longer than this. */
private const val DUCK_LIMIT_MS = 15_000L

/** How many songs back the player remembers, for avoiding near-duplicates. */
private const val SONG_MEMORY = 40

/**
 * How long the music stays down after a cue ends. A countdown is spoken as
 * separate utterances — "five", "four", "three" — and without this the music
 * swells back up between the digits and drowns the next one.
 */
private const val DUCK_LINGER_MS = 1_200L

data class NowPlaying(
    val title: String,
    val artist: String?,
    val trackBpm: Double,
    val effectiveBpm: Double,
    val targetBpm: Int,
    val speed: Float,
    val multiplier: Double,
    /** Measured loudness of this track, LUFS; 0.0 when it has none. */
    val loudnessLufs: Double = 0.0,
    /** Attenuation applied to level it with the rest of the playlist, dB. */
    val gainDb: Double = 0.0,
) {
    val stretchPercent: Int get() = ((speed - 1f) * 100).roundToInt()
    val levelled: Boolean get() = gainDb < -0.4 || gainDb > 0.4
}

/**
 * Pace-matched music, the Lolo BeatBurn way: each interval gets a track whose
 * tempo is close to the target walking cadence, then the track is time-stretched
 * (pitch-corrected, via ExoPlayer's Sonic pipeline) to sit exactly on it.
 */
@OptIn(markerClass = [UnstableApi::class])
/** A once-a-second picture of the player, for the screen and for diagnosis. */
data class MusicStatus(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** The volume actually applied, 0-100 — levelling, ducking and all. */
    val volumePercent: Int = 0,
    val ducked: Boolean = false,
    val muted: Boolean = false,
) {
    val hasDuration: Boolean get() = durationMs > 0
}

class WorkoutMusicPlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null

    private var tracks: List<TrackEntity> = emptyList()
    private var settings: MusicSettings = MusicSettings()
    private val recent = ArrayDeque<String>()
    /** The songs behind the recent URIs: three versions of one track are one song. */
    private val recentSongs = ArrayDeque<String>()
    private var targetBpm: Int = 0
    private var currentUri: String? = null
    private var ducked = false
    private var muted = false
    /**
     * The level the playlist is being held at — its median loudness — and the
     * attenuation the current track needs to reach it. Recomputed whenever the
     * playlist changes.
     */
    private var levelReference: Double? = null
    private var trackGain = 1f

    /**
     * The only way to get above unity gain. The player's own volume is capped at
     * 1.0, so without this the quiet half of a playlist could not be lifted and
     * the only way to match tracks would be to pull the loud half a long way
     * down — which is exactly what made the app half as loud as everything else.
     * Unavailable on some devices, in which case levelling is attenuation-only.
     */
    private var enhancer: LoudnessEnhancer? = null
    /**
     * Bumped by every fade. A step whose generation is stale does nothing, which
     * is what stops a track crossfade and a coaching duck from overwriting each
     * other — they used to collide at exactly the moment a cue was spoken.
     */
    private var fadeGeneration = 0

    /**
     * ExoPlayer may only be touched from the thread it was built on. The coach's
     * callbacks arrive on a TTS binder thread, where reading `player.volume`
     * throws inside a binder transaction — an exception that is logged and
     * swallowed rather than crashing. That is how a duck could be applied and
     * never taken off, leaving the music quiet until something else happened to
     * write the volume, half a minute later.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post { block() }
    }

    /** A cue that never reports finishing must not hold the music down forever. */
    private val unduckWatchdog = Runnable {
        if (ducked) {
            Log.w(TAG, "duck outlasted the cue; lifting it")
            ducked = false
            setBoost(LoudnessNormaliser.boostMillibels(currentGainDb))
            settleTo(targetVolume(), 400)
        }
    }

    /** Total levelling gain applied to the current track, dB, for the readout. */
    private var currentGainDb = 0.0

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /**
     * Mirrors [muted] so the rest of the app can follow a pause that came from
     * the notification shade rather than from our own buttons.
     */
    /** Why there is no music, in words a rider can act on. */
    /**
     * Without this a silent ride is unreadable: a track whose title changed but
     * whose volume is a tenth of a per cent looks exactly like a paused one, and
     * the two have entirely different causes.
     */
    private val _status = MutableStateFlow(MusicStatus())
    val status: StateFlow<MusicStatus> = _status.asStateFlow()

    private val statusTicker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                _status.value = MusicStatus(
                    playing = p.isPlaying,
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = p.duration.takeIf { it > 0 } ?: 0L,
                    volumePercent = (p.volume * 100).roundToInt(),
                    ducked = ducked,
                    muted = muted,
                )
            }
            handler.postDelayed(this, 1_000)
        }
    }

    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    private val failed = mutableSetOf<String>()

    /** A track that can never play here; the service drops it for good. */
    var onTrackUnplayable: ((uri: String, reason: String) -> Unit)? = null
    private var droppedCount = 0

    /**
     * Failures per track this session. They turned out to be transient — a
     * track skipped on one ride plays on the next — so the first response to a
     * failure is another attempt, not a conclusion.
     */
    private val attempts = mutableMapOf<String, Int>()
    private var consecutiveFailures = 0

    private val _mutedState = MutableStateFlow(false)
    val mutedState: StateFlow<Boolean> = _mutedState.asStateFlow()

    /**
     * Publishes the playlist to the system, which is what puts a card with
     * play/pause and next in the notification shade and on the lock screen.
     */
    private var session: MediaSession? = null

    /** Session-long audio focus. A transient loss is this app's own coach. */
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var yielded = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                yielded = true
                onMain { player?.pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (yielded) {
                    yielded = false
                    onMain { if (!muted) player?.play() }
                }
            }
            // Transient losses are ignored on purpose: the coach's cues arrive
            // as exactly those, and a ride should not stop for them.
            else -> Unit
        }
    }

    private fun takeFocus() {
        val manager = audioManager ?: return
        if (focusRequest != null) return
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.w(TAG, "audio focus request failed", e)
            focusRequest = null
        }
    }

    private fun releaseFocus() {
        val manager = audioManager ?: return
        focusRequest?.let {
            try {
                manager.abandonAudioFocusRequest(it)
            } catch (e: Exception) {
                Log.w(TAG, "abandoning focus failed", e)
            }
        }
        focusRequest = null
    }

    val hasMusic: Boolean get() = tracks.isNotEmpty()

    /**
     * Point the player at a playlist. Safe to call repeatedly during a session:
     * the engine re-reads the track table whenever it changes, so tracks added
     * on the Music tab are picked up without restarting the walk.
     */
    fun prepare(tracks: List<TrackEntity>, settings: MusicSettings) {
        this.tracks = tracks
        this.settings = settings
        levelReference = if (settings.normaliseLoudness) {
            LoudnessNormaliser.reference(tracks.map { it.loudnessLufs })
        } else {
            null
        }
        // A playlist change can move the target, so the track already playing
        // needs its attenuation recomputed rather than waiting for the next bout.
        currentUri?.let { uri ->
            tracks.firstOrNull { it.uri == uri }?.let { applyLevelling(it, fade = true) }
        }
        if (player == null) {
            val sessionId = try {
                Util.generateAudioSessionIdV21(context)
            } catch (e: Exception) {
                C.AUDIO_SESSION_ID_UNSET
            }
            player = ExoPlayer.Builder(context)
                // Retry a failed read six times instead of three before giving
                // up on it. The failures seen in the wild are transient — an SD
                // card waking up, a provider briefly refusing — and a track
                // that is skipped on one walk plays fine on the next, so the
                // cheapest fix is to let the player try harder before it
                // reports anything at all.
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(IO_RETRIES))
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus = false, and this is load-bearing. The
                    // coach speaks through the platform TTS engine, which takes
                    // audio focus of its own for every cue; a player managing
                    // its own focus pauses for its own app's coach, and when the
                    // loss it hears is a plain one rather than a transient one,
                    // it never resumes. Focus for the session is taken once,
                    // below; a call is caught by the ride service instead.
                    /* handleAudioFocus = */ false,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { p ->
                    // A session id of our own, so the loudness effect can be
                    // attached to it before anything plays: left to itself
                    // ExoPlayer does not settle on one until the first track
                    // starts. This is a method on the player, not the builder.
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                        try {
                            p.audioSessionId = sessionId
                        } catch (e: Exception) {
                            Log.w(TAG, "could not set audio session id", e)
                        }
                    }
                    p.repeatMode = Player.REPEAT_MODE_OFF
                    takeFocus()
                    handler.post(statusTicker)
                    p.volume = targetVolume()
                    p.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) nextTrack()
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                consecutiveFailures = 0
                                currentUri?.let { attempts.remove(it) }
                                if (_problem.value != null) _problem.value = null
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            onTrackFailed(error)
                        }

                    })
                }
            enhancer = if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try {
                    LoudnessEnhancer(sessionId).apply { enabled = true }
                } catch (e: Exception) {
                    // Not every device ships the effect; levelling then only
                    // ever turns tracks down, which is still an improvement.
                    Log.w(TAG, "loudness enhancer unavailable", e)
                    null
                }
            } else {
                null
            }
            session = try {
                // The session is handed a wrapper, not the raw player, so that
                // play/pause from the shade, the lock screen or a headset button
                // goes through the app's own mute flag. Pausing the ExoPlayer
                // directly would not stick: the next interval change picks a
                // track and calls play() again, which reads as the app ignoring
                // you. Routing through setMuted() makes the external control and
                // the in-app Mute button the same switch.
                val sessionPlayer = object : ForwardingPlayer(player!!) {
                    override fun play() = this@WorkoutMusicPlayer.setMuted(false)

                    override fun pause() = this@WorkoutMusicPlayer.setMuted(true)

                    override fun setPlayWhenReady(playWhenReady: Boolean) =
                        this@WorkoutMusicPlayer.setMuted(!playWhenReady)
                }
                MediaSession.Builder(context, sessionPlayer).setId("kickr-hiit").build()
            } catch (e: Exception) {
                // A session is a nicety; never let it take the workout down.
                null
            }
        }
    }

    /**
     * Point the music at a new cadence.
     *
     * @param switchTrack true at interval boundaries: pick a fresh track that
     *        fits the new pace. False mid-interval: just nudge the speed.
     */
    fun setTargetCadence(bpm: Int, switchTrack: Boolean) {
        targetBpm = bpm
        if (!settings.enabled || tracks.isEmpty()) return
        if (switchTrack || _nowPlaying.value == null) {
            crossfadeTo(chooseNext())
        } else {
            retune()
        }
    }

    /**
     * The next track: one of the ones that fit this cadence, at random.
     *
     * Drawn from a pool of good fits rather than being the single best fit — a
     * deterministic best match turns a thousand-track playlist into a rotation
     * as long as the memory of what was played, and no longer. Tracks whose
     * tempo has not been measured yet still get their share of the turns.
     */
    private fun chooseNext(): TrackEntity? {
        if (tracks.isEmpty()) return null
        if (failed.size >= tracks.size) failed.clear()
        val playable = tracks.filter {
            it.uri !in failed && PlayableFormats.isPlayable(it.fileName)
        }.ifEmpty { tracks.filter { t -> PlayableFormats.isPlayable(t.fileName) } }
        if (playable.isEmpty()) return null

        // A tempo measured with little confidence is played but not *matched*:
        // claiming 103 for a 155 BPM track is worse than claiming nothing.
        val fresh = playable.filterNot {
            TrackDeduper.songKey(it.title, it.fileName) in recentSongs
        }.ifEmpty { playable }
        val analysed = fresh.filter { it.tempoTrusted }
        val unanalysed = fresh.filter { !it.tempoTrusted }

        if (!settings.matchBpm || targetBpm <= 0 || analysed.isEmpty()) {
            return randomFrom(fresh) ?: fresh.firstOrNull()
        }

        val match = TrackMatcher.pickRandom(
            candidates = analysed.map { MatchCandidate(it.uri, it.bpm) },
            targetBpm = targetBpm,
            maxStretchPercent = settings.maxStretchPercent,
            allowOctave = settings.allowOctaveMatch,
            recentlyPlayed = recent,
        )

        val unmeasuredShare = unanalysed.size.toDouble() / fresh.size.coerceAtLeast(1)
        val takeUnmeasured = unanalysed.isNotEmpty() &&
            (match == null || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < unmeasuredShare * 0.5)
        if (takeUnmeasured) {
            randomFrom(unanalysed)?.let { return it }
        }

        match?.let { result -> tracks.firstOrNull { it.uri == result.id } }?.let { return it }
        return randomFrom(fresh) ?: fresh.firstOrNull()
    }

    /** A track nobody has heard lately, chosen at random. */
    private fun randomFrom(pool: List<TrackEntity>): TrackEntity? {
        if (pool.isEmpty()) return null
        val fresh = pool.filterNot { it.uri in recent }.ifEmpty { pool }
        return fresh[java.util.concurrent.ThreadLocalRandom.current().nextInt(fresh.size)]
    }

    private fun playbackFor(track: TrackEntity): Pair<Float, MatchResult?> {
        if (!settings.matchBpm || track.bpm <= 0.0 || targetBpm <= 0) return 1f to null
        val match = TrackMatcher.pick(
            candidates = listOf(MatchCandidate(track.uri, track.bpm)),
            targetBpm = targetBpm,
            maxStretchPercent = settings.maxStretchPercent,
            allowOctave = settings.allowOctaveMatch,
        ) ?: return 1f to null
        return match.speed.toFloat() to match
    }

    /**
     * A track that will not open. Usually not a broken file but a lost folder
     * permission — and once that has happened every track fails, so the ride
     * runs in silence and skipping does not help. That case is named explicitly.
     */
    private fun onTrackFailed(error: PlaybackException) {
        val uri = currentUri
        val track = tracks.firstOrNull { it.uri == uri }
        val title = track?.displayName ?: _nowPlaying.value?.title ?: "that track"
        val extension = track?.fileName?.let { PlayableFormats.extensionOf(it) }.orEmpty()
        Log.w(TAG, "playback failed for $title: ${error.errorCodeName}", error)

        val lostPermission = error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
            generateSequence(error.cause) { it.cause }.any { it is SecurityException }

        // Only a format with no decoder is hopeless, and the extension decides
        // that — it cannot change between rides. Everything else is bad luck
        // and gets another go.
        val hopeless = track != null && !PlayableFormats.isPlayable(track.fileName)

        val tries = if (uri == null) RETRY_LIMIT else (attempts[uri] ?: 0) + 1
        if (uri != null) attempts[uri] = tries

        if (!lostPermission && !hopeless && tries < RETRY_LIMIT && track != null) {
            Log.i(TAG, "retrying $title (attempt ${tries + 1} of $RETRY_LIMIT)")
            handler.postDelayed({ crossfadeTo(track) }, RETRY_DELAY_MS)
            return
        }

        if (uri != null) failed += uri
        consecutiveFailures++

        val named = if (extension.isNotEmpty()) "$title (.$extension)" else title
        _problem.value = when {
            lostPermission ->
                "Android has forgotten the permission on your music folder. " +
                    "Pick the folder again on the Music tab — the playlist is kept."
            hopeless -> {
                droppedCount++
                onTrackUnplayable?.invoke(uri.orEmpty(), error.errorCodeName)
                val format = track?.fileName?.let { PlayableFormats.unplayableName(it) }
                if (format != null) {
                    "Dropped $named — the player cannot read $format files."
                } else {
                    "Dropped $named — this device has no decoder for it."
                }
            }
            consecutiveFailures >= 3 ->
                "Music is struggling to read your files — ${error.errorCodeName}."
            else -> null
        }

        if (!lostPermission && consecutiveFailures < 4) {
            handler.postDelayed({ nextTrack() }, 400)
        }
    }

    private fun crossfadeTo(track: TrackEntity?) {
        val p = player ?: return
        if (track == null) return
        val start = {
            val (speed, match) = playbackFor(track)
            applyLevelling(track, fade = false)
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.displayName)
                            .setArtist(track.artist)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            )
            p.playbackParameters = PlaybackParameters(speed, 1.0f)
            p.volume = 0f
            p.prepare()
            if (!muted) p.play()
            currentUri = track.uri
            rememberRecent(track.uri)
            _nowPlaying.value = NowPlaying(
                title = track.displayName,
                artist = track.artist,
                trackBpm = track.bpm,
                effectiveBpm = match?.effectiveBpm ?: track.bpm,
                targetBpm = targetBpm,
                speed = speed,
                multiplier = match?.multiplier ?: 1.0,
                loudnessLufs = track.loudnessLufs,
                gainDb = currentGainDb,
            )
            settleTo(targetVolume(), 700)
            // Asked to play and still silent a second and a half later: ask again.
            handler.postDelayed({
                val still = player ?: return@postDelayed
                if (!muted && !yielded && !still.isPlaying &&
                    still.playbackState != Player.STATE_IDLE
                ) {
                    Log.w(TAG, "track did not start; asking again")
                    still.play()
                }
            }, 1_500)
        }
        if (p.isPlaying) {
            fadeTo(0f, 500)
            handler.postDelayed({ start() }, 520)
        } else {
            start()
        }
    }

    private fun retune() {
        val p = player ?: return
        val playing = _nowPlaying.value ?: return
        val track = tracks.firstOrNull { it.uri == currentUri } ?: return
        val (speed, match) = playbackFor(track)
        p.playbackParameters = PlaybackParameters(speed, 1.0f)
        _nowPlaying.value = playing.copy(
            speed = speed,
            targetBpm = targetBpm,
            effectiveBpm = match?.effectiveBpm ?: playing.effectiveBpm,
        )
    }

    fun nextTrack() = onMain {
        crossfadeTo(chooseNext())
    }

    fun play() = onMain {
        if (!muted) player?.play()
    }

    /**
     * The single source of truth for how loud the player should be right now:
     * silent when muted, ducked while the coach is speaking, otherwise the
     * volume the user chose. Every volume change goes through this.
     */
    private fun targetVolume(): Float = when {
        muted -> 0f
        ducked -> baseVolume() * (settings.duckPercent.coerceIn(0, 90) / 100f)
        else -> baseVolume()
    }

    /**
     * Work out how far this track has to come down to sit level with the rest of
     * the playlist. Attenuation only — see [LoudnessNormaliser].
     */
    private fun applyLevelling(track: TrackEntity, fade: Boolean) {
        val gainDb = if (settings.normaliseLoudness) {
            LoudnessNormaliser.gainDb(
                lufs = track.loudnessLufs,
                reference = levelReference,
                maxCutDb = settings.maxCutDb,
                maxBoostDb = settings.maxBoostDb,
            )
        } else {
            0.0
        }
        currentGainDb = gainDb
        // Down through the player's volume, up through the audio effect.
        // While a cue is playing the boost stays off; restoreAfterCue puts the
        // right value back, so nothing here can undo a duck.
        if (!ducked) setBoost(LoudnessNormaliser.boostMillibels(gainDb))
        val factor = LoudnessNormaliser.attenuationFactor(gainDb)
        if (factor == trackGain) return
        trackGain = factor
        if (fade) fadeTo(targetVolume(), 250)
    }

    /**
     * Hand the soundtrack over to another app, or take it back. The flag lives
     * here rather than in the service so that an interval change — which picks a
     * new track and starts it — cannot silently un-mute.
     */
    fun setMuted(value: Boolean) = onMain {
        muted = value
        _mutedState.value = value
        if (value) {
            player?.pause()
        } else {
            // Through fadeTo, not a bare volume assignment: a fade still in
            // flight would otherwise overwrite it a few milliseconds later.
            settleTo(targetVolume(), 0)
            player?.play()
        }
    }

    val isMuted: Boolean get() = muted

    fun pause() = onMain {
        player?.pause()
    }

    /** Lower the music while the voice coach talks. */
    fun duck(enable: Boolean) = onMain {
        handler.removeCallbacks(unduckWatchdog)
        handler.removeCallbacks(restoreAfterCue)
        if (enable) {
            handler.postDelayed(unduckWatchdog, DUCK_LIMIT_MS)
            if (!ducked) {
                ducked = true
                // The effect is the other half of the level: a track being
                // lifted 6 dB would otherwise keep that lift through the cue.
                setBoost(0)
                // Fast, so the first syllable is already over quiet music.
                settleTo(targetVolume(), 80)
            } else {
                // A track that started mid-cue can be sitting at the wrong
                // level even though the flag is right. Re-assert, free.
                settleTo(targetVolume(), 0)
            }
        } else {
            // Not immediately: see DUCK_LINGER_MS.
            handler.postDelayed(restoreAfterCue, DUCK_LINGER_MS)
        }
    }

    /** Brings the music back up once the coach has been quiet for a moment. */
    private val restoreAfterCue = Runnable {
        handler.removeCallbacks(unduckWatchdog)
        ducked = false
        setBoost(LoudnessNormaliser.boostMillibels(currentGainDb))
        settleTo(targetVolume(), 500)
    }

    private fun setBoost(millibels: Int) {
        try {
            enhancer?.setTargetGain(millibels)
        } catch (e: Exception) {
            Log.w(TAG, "boost change failed", e)
        }
    }

    fun setVolumePercent(percent: Int) = onMain {
        settings = settings.copy(volumePercent = percent.coerceIn(0, 100))
        settleTo(targetVolume(), 150)
    }

    private fun baseVolume(): Float =
        (settings.volumePercent.coerceIn(0, 100) / 100f) * trackGain

    /**
     * A fade that ends on the exact number. Twelve steps can be left a hair
     * short by rounding, and a fade that was overtaken never arrives at all;
     * the final write lands after the last step regardless.
     */
    private fun settleTo(target: Float, durationMs: Long) {
        fadeTo(target, durationMs)
        val generation = fadeGeneration
        handler.postDelayed({
            if (generation == fadeGeneration) player?.volume = target.coerceIn(0f, 1f)
        }, durationMs + 40)
    }

    private fun fadeTo(target: Float, durationMs: Long) {
        val p = player ?: return
        val generation = ++fadeGeneration
        val steps = 12
        val start = p.volume
        val delta = (target - start) / steps
        for (i in 1..steps) {
            handler.postDelayed({
                // A fade started later has already taken over; stay out of its way.
                if (generation == fadeGeneration) {
                    player?.volume = (start + delta * i).coerceIn(0f, 1f)
                }
            }, durationMs / steps * i)
        }
    }

    private fun rememberRecent(uri: String) {
        tracks.firstOrNull { it.uri == uri }?.let { track ->
            val song = TrackDeduper.songKey(track.title, track.fileName)
            recentSongs.remove(song)
            recentSongs.addFirst(song)
            while (recentSongs.size > SONG_MEMORY) recentSongs.removeLast()
        }
        recent.remove(uri)
        recent.addFirst(uri)
        val keep = (tracks.size / 4).coerceIn(8, 250)
        while (recent.size > keep) recent.removeLast()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        releaseFocus()
        try {
            enhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "enhancer release failed", e)
        }
        enhancer = null
        session?.release()
        session = null
        player?.release()
        player = null
        _nowPlaying.value = null
    }
}
