package com.itant.jtv.mpv

import android.app.Application
import com.itant.jtv.manager.RouteManager
import com.itant.jtv.mpv.route.TvRoute

class TvApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        RouteManager.currentRoute = TvRoute()
    }
}