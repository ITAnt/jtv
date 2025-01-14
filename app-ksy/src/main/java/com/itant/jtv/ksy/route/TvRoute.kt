package com.itant.jtv.ksy.route

import com.itant.jtv.ksy.ui.home.video.VideoKsyActivity
import com.itant.jtv.manager.IRoute
import com.miekir.mvvm.core.view.base.BasicActivity
import com.miekir.mvvm.extension.openActivity

class TvRoute: IRoute {
    override fun navigateVideo(activity: BasicActivity) {
        activity.openActivity<VideoKsyActivity>()
    }
}