package com.itant.jtv.manager

import com.miekir.mvvm.core.view.base.BasicActivity

object RouteManager {
    var currentRoute: IRoute? = null
}

interface IRoute {
    fun navigateVideo(activity: BasicActivity)
}