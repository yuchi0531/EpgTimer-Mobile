package com.starrow.epgtimer

import android.app.Application
import com.starrow.epgtimer.di.AppContainer

class EpgTimerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
