package com.itant.jtv.ijk

import android.app.Application
import android.content.Context
import com.itant.jtv.ijk.route.TvRoute
import com.itant.jtv.manager.RouteManager
import tv.danmaku.ijk.custom.TvMediaPlayer
import xyz.doikki.videoplayer.PlayerHelper
import xyz.doikki.videoplayer.player.PlayerFactory
import xyz.doikki.videoplayer.player.VideoViewConfig
import xyz.doikki.videoplayer.player.VideoViewManager

class TvApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        RouteManager.currentRoute = TvRoute()

        PlayerHelper.init()
        VideoViewManager.setConfig(VideoViewConfig.newBuilder()
            //使用使用IjkPlayer解码
            //.setPlayerFactory(IjkPlayerFactory.create())
            .setPlayerFactory(object : PlayerFactory<TvMediaPlayer?>() {
                override fun createPlayer(context: Context): TvMediaPlayer {
                    return TvMediaPlayer(context)
                }
            })
            //使用ExoPlayer解码
            //.setPlayerFactory(ExoMediaPlayerFactory.create())
            //使用MediaPlayer解码
            //.setPlayerFactory(AndroidMediaPlayerFactory.create())
            .build()
        )
    }
}