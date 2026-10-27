package com.telecomprofi.kickrhiit

import android.app.Application
import android.content.Context
import com.telecomprofi.kickrhiit.data.db.AppDatabase
import com.telecomprofi.kickrhiit.data.prefs.AppPrefs
import com.telecomprofi.kickrhiit.music.MusicLibrary
import com.telecomprofi.kickrhiit.ride.RideNotifications

/** The whole dependency graph. One user, one device, no framework needed. */
class Container(context: Context) {
    private val appContext = context.applicationContext
    val prefs = AppPrefs(appContext)
    val db = AppDatabase.get(appContext)
    val musicLibrary = MusicLibrary(appContext, db)
    val rideDao = db.rideDao()
}

object Graph {
    @Volatile
    private var instance: Container? = null

    val container: Container
        get() = instance ?: error("Graph.init() has not been called")

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) instance = Container(context)
            }
        }
    }
}

class KickrHiitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        RideNotifications.ensureChannel(this)
    }
}
