package com.itant.jtv.ijk.route

import com.itant.jtv.ijk.ui.home.video.VideoIjkActivity
import com.itant.jtv.manager.IRoute
import com.miekir.mvvm.core.view.base.BasicActivity
import com.miekir.mvvm.extension.openActivity

class TvRoute: IRoute {
    override fun navigateVideo(activity: BasicActivity) {
        activity.openActivity<VideoIjkActivity>()
    }
}