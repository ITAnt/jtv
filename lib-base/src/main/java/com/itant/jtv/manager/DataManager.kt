package com.itant.jtv.manager

import androidx.lifecycle.MutableLiveData
import com.itant.jtv.ui.home.setting.IptvBean

object DataManager {
    /**
     * IPTV列表
     */
    val iptvListLiveData = MutableLiveData<List<IptvBean>>()

    /**
     * 首次启动
     */
    var firstStartUp = true
    /**
     * 直播列表
     */
    val liveChannelListLiveData = MutableLiveData<List<IptvBean>>()

    /**
     * 收藏列表
     */
    val starChannelListLiveData = MutableLiveData<List<IptvBean>>()
}