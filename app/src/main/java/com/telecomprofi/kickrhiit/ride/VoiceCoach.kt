package com.telecomprofi.kickrhiit.ride

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "VoiceCoach"

/** How often the engine is asked whether it is still talking. */
private const val POLL_MS = 300L

/**
 * Spoken cues, through the platform engine, with navigation-guidance audio
 * attributes so they cut through the music rather than fighting it.
 *
 * A sprint session needs the voice more than a steady ride does: thirty seconds
 * is short enough that reading a screen costs you a tenth of the effort, and the
 * start has to be exact — hence the countdown rather than a single "go".
 */
class VoiceCoach(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit = {},
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Boolean>>()
    private var counter = 0
    /**
     * Whether the coach is talking. Decided by polling the engine's own
     * [TextToSpeech.isSpeaking] rather than by counting callbacks: an utterance
     * flushed by an urgent cue is often never reported done, and a count that
     * sticks above zero leaves the music ducked — playing, and inaudible — for
     * the rest of the ride.
     */
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var quietPolls = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                Log.w(TAG, "TTS unavailable ($status)")
                return@TextToSpeech
            }
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_MEDIA, not NAVIGATION_GUIDANCE: guidance is mixed at
                    // the system's own idea of a suitable level, which on most
                    // phones is well below the music. The cue has to be the
                    // loudest thing in the room during a sprint countdown, and
                    // the music is ducked under it anyway.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                // The duck is raised in speakNow, before the engine is even
                // asked, so the first word of a sprint countdown is already over
                // quiet music. Done and error only hurry the poll along.
                override fun onStart(utteranceId: String?) = setActive(true)

                override fun onDone(utteranceId: String?) = pollSoon()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = pollSoon()

                override fun onError(utteranceId: String?, errorCode: Int) = pollSoon()
            })
            while (pending.isNotEmpty()) {
                val (text, flush) = pending.removeFirst()
                speakNow(text, flush)
            }
        }
    }

    private val speechWatch = object : Runnable {
        override fun run() {
            val busy = try {
                tts?.isSpeaking == true
            } catch (e: Exception) {
                false
            }
            if (busy) {
                quietPolls = 0
            } else if (++quietPolls >= 2) {
                setActive(false)
                return
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun setActive(value: Boolean) {
        handler.post {
            if (active == value) {
                if (value) armPoll()
                return@post
            }
            active = value
            quietPolls = 0
            onSpeaking(value)
            if (value) armPoll() else handler.removeCallbacks(speechWatch)
        }
    }

    private fun armPoll() {
        handler.removeCallbacks(speechWatch)
        handler.postDelayed(speechWatch, POLL_MS)
    }

    private fun pollSoon() {
        handler.removeCallbacks(speechWatch)
        handler.postDelayed(speechWatch, 120)
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun say(text: String, interrupt: Boolean = false) {
        if (text.isBlank()) return
        if (!ready) {
            pending += text to interrupt
            while (pending.size > 4) pending.removeFirst()
            return
        }
        speakNow(text, interrupt)
    }

    private fun speakNow(text: String, interrupt: Boolean) {
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
        setActive(true)
        val result = tts?.speak(text, mode, params, "kh-${counter++}")
        // An engine that refuses the utterance will never call onStart either.
        if (result != TextToSpeech.SUCCESS) pollSoon()
    }

    fun stopSpeaking() {
        tts?.stop()
        setActive(false)
    }

    fun shutdown() {
        // Torn down mid-sentence, the music would otherwise be left quiet.
        handler.removeCallbacks(speechWatch)
        setActive(false)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown failed", e)
        }
        tts = null
        ready = false
    }
}

/** Everything spoken, in one place. */
object Cues {

    fun rideStart(name: String, minutes: Int, sprints: Int): String = buildString {
        append("$name. $minutes minutes.")
        if (sprints > 0) append(" $sprints sprints. Spin up and we will begin.")
        else append(" Let's go.")
    }

    fun countdown(n: Int): String = n.toString()

    fun sprintImminent(): String = "Get ready."

    fun ergTarget(watts: Int, cadence: Int): String = "$watts watts, $cadence r p m."

    fun freeRide(): String = "Free ride. The brake is set — give it everything."

    fun halfway(): String = "Halfway."

    fun lastMinute(): String = "One minute of work left."

    fun trainerLost(): String = "Lost the trainer. Keep pedalling, still recording."

    fun trainerBack(): String = "Trainer back."

    fun noControl(): String =
        "The trainer will not accept control. Set the resistance yourself and I will call the steps."

    fun sprintResult(watts: Int, best: Int): String = when {
        best > 0 && watts >= best -> "$watts watts. That is your best yet."
        watts > 0 -> "$watts watts."
        else -> ""
    }

    fun finished(minutes: Int, kj: Int, np: Int, best: Int): String = buildString {
        append("Ride complete. $minutes minutes, $kj kilojoules, ")
        append("normalised power $np watts.")
        if (best > 0) append(" Best thirty seconds, $best watts.")
    }
}
