package com.itant.jtv.vlc.route

import com.itant.jtv.manager.IRoute
import com.itant.jtv.vlc.ui.home.video.VideoVlcActivity
import com.miekir.mvvm.core.view.base.BasicActivity
import com.miekir.mvvm.extension.openActivity

class TvRoute: IRoute {
    override fun navigateVideo(activity: BasicActivity) {
        activity.openActivity<VideoVlcActivity>()
    }
}