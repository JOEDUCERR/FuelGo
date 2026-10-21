package com.fuelgo.app

import android.app.Application
import com.fuelgo.app.di.AppContainer
import com.fuelgo.app.work.scheduleWidgetWork
import com.fuelgo.app.widget.WidgetLocationTracker

class FuelGoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Default 15-minute widget refresh; re-scheduled when the user changes
        // the interval in Settings.
        scheduleWidgetWork(this, 15)
        // Passive location -> live-ish widget distance while driving, at
        // effectively zero battery cost (PASSIVE provider only).
        WidgetLocationTracker.start(this)
    }
}
