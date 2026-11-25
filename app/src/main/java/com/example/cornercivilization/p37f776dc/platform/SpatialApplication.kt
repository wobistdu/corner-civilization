package com.example.cornercivilization.p37f776dc.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.cornercivilization.p37f776dc.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
