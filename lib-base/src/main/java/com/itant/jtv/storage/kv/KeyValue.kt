package com.itant.jtv.storage.kv

/**
 * SharedPreference配置清单
 */
object KeyValue {
    /**
     * 直播源IP列表
     */
    var iptvListGson by MM("iptvListGson", "[]")

    /**
     * 当前直播频道列表
     */
    var liveListGson by MM("liveListGson", "[]")

    /**
     * 当前收藏频道列表
     */
    var starListGson by MM("favoriteListGson", "[]")



    /**
     * IPTV源地址
     */
    var jtvUrl by MM("jtvUrl", "")

    /**
     * 上一次看的是直播
     */
    var lastViewLive by MM("lastViewLive", true)

    /**
     * 是否强制更新
     */
    var forceUpdate by MM("forceUpdate", false)

    /**
     * 默认-16:9-4:3-填充
     */
    var videoScaleType by MM("videoScaleType", 0)

    /**
     * 从来没成功过
     */
    var neverSucceed by MM("neverSucceed", true)

    /**
     * 多源模式，即合并同名频道
     */
    var combineChannel by MM("combineChannel", false)

    /**
     * 开机自启
     */
    var autoLaunch by MM("autoLaunch", false)

    /**
     * 绿色上网
     */
    var greenMode by MM("greenMode", true)

    /**
     * 硬解码
     */
    var hardCodec by MM("hardCodec", false)

    /**
     * 使用缓存框架
     */
    var cacheEnable by MM("cacheEnable", false)

    /**
     * 对频道名称进行排序
     */
    var channelSort by MM("channelSort", false)

    /**
     * 仅展示IPV4
     */
    var ipv4Only by MM("ipv4Only", true)

    /**
     * 上一次播放失败的链接
     */
    var lastFailUrl = ""
}