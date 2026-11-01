package com.jamilcpu.homestudio

import android.app.Application

class HomeStudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
