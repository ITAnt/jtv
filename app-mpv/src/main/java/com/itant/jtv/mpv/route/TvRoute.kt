package com.itant.jtv.mpv.route

import com.itant.jtv.manager.IRoute
import com.itant.jtv.mpv.ui.home.video.VideoMpvActivity
import com.miekir.mvvm.core.view.base.BasicActivity
import com.miekir.mvvm.extension.openActivity

class TvRoute: IRoute {
    override fun navigateVideo(activity: BasicActivity) {
        activity.openActivity<VideoMpvActivity>()
    }
}