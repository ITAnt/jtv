package com.itant.jtv.exo.route

import com.itant.jtv.exo.ui.home.video.VideoExoActivity
import com.itant.jtv.manager.IRoute
import com.miekir.mvvm.core.view.base.BasicActivity
import com.miekir.mvvm.extension.openActivity

class TvRoute: IRoute {
    override fun navigateVideo(activity: BasicActivity) {
        activity.openActivity<VideoExoActivity>()
    }
}