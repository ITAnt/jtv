package com.itant.jtv.ksy

import android.app.Application
import com.itant.jtv.ksy.route.TvRoute
import com.itant.jtv.manager.RouteManager

class TvApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        RouteManager.currentRoute = TvRoute()
    }
}