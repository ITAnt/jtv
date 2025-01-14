package com.itant.jtv.exo

import android.app.Application
import com.itant.jtv.exo.route.TvRoute
import com.itant.jtv.manager.RouteManager

class TvApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        RouteManager.currentRoute = TvRoute()
    }
}