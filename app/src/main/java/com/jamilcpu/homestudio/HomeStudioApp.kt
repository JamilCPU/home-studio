package com.jamilcpu.homestudio

import android.app.Application

class HomeStudioApp : Application() {
    lateinit var container: AppContainer
        internal set // tests swap in a container wired to a fake server

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
