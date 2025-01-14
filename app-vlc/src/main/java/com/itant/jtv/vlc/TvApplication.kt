package com.itant.jtv.vlc

import android.app.Application
import com.itant.jtv.manager.RouteManager
import com.itant.jtv.vlc.route.TvRoute

class TvApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        RouteManager.currentRoute = TvRoute()
    }
}