package com.fuelgo.app

import android.app.Application
import com.fuelgo.app.di.AppContainer
import com.fuelgo.app.work.scheduleWidgetWork

class FuelGoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Default 15-minute widget refresh; re-scheduled when the user changes
        // the interval in Settings.
        scheduleWidgetWork(this, 15)
    }
}
