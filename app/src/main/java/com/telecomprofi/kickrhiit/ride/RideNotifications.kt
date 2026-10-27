package com.telecomprofi.kickrhiit.ride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.telecomprofi.kickrhiit.MainActivity
import com.telecomprofi.kickrhiit.R
import com.telecomprofi.kickrhiit.core.TimeMath

object RideNotifications {

    const val CHANNEL = "ride_live"
    const val ID = 2001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live ride", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Power, cadence and interval progress while you ride"
                setShowBadge(false)
                enableVibration(false)
            }
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, what: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context,
            code,
            Intent(context, RideService::class.java).setAction(what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun build(context: Context, state: RideUiState, musicMuted: Boolean): Notification {
        val title = buildString {
            append(state.step?.label ?: state.workoutName)
            if (state.status == RideStatus.PAUSED) append(" (paused)")
        }
        val body = buildString {
            if (state.step?.mode == com.telecomprofi.kickrhiit.core.StepMode.ERG) {
                append("${state.powerW} W of ${state.targetW}")
            } else {
                append("${state.powerW} W free ride")
            }
            append(" · ${state.cadenceRpm} rpm")
            if (state.hr > 0) append(" · ${state.hr} bpm")
            append(" · ${TimeMath.mmss(state.stepRemainingSec)} left")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bike)
            .setContentTitle(title)
            .setContentText(body)
            .setOnlyAlertOnce(true)
            .setOngoing(state.status == RideStatus.RUNNING)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(state.totalSec, state.elapsedSec, false)

        if (state.status == RideStatus.RUNNING) {
            builder.addAction(R.drawable.ic_stat_bike, "Pause", action(context, RideService.ACTION_PAUSE, 11))
        } else {
            builder.addAction(R.drawable.ic_stat_bike, "Resume", action(context, RideService.ACTION_PAUSE, 12))
        }
        if (state.nowPlaying != null) {
            builder.addAction(
                R.drawable.ic_stat_bike,
                if (musicMuted) "Play music" else "Mute music",
                action(context, RideService.ACTION_TOGGLE_MUSIC, 13),
            )
        }
        builder.addAction(R.drawable.ic_stat_bike, "Finish", action(context, RideService.ACTION_STOP, 14))
        return builder.build()
    }
}
