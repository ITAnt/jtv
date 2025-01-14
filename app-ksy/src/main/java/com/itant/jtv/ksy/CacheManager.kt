//package com.itant.jtv.ksy
//
//import com.blankj.utilcode.util.RegexUtils
//import com.itant.jtv.storage.kv.KeyValue
//import com.jeffmony.videocache.VideoProxyCacheManager
//import com.jeffmony.videocache.utils.ProxyCacheUtils
//import com.jeffmony.videocache.utils.StorageUtils
//import com.miekir.mvvm.context.GlobalContext
//
//
//class CacheHandler {
//    fun onPlay(url: String): String? {
//        val lastUrl = KeyValue.lastPlayingUrl
//        if (RegexUtils.isURL(lastUrl)) {
//            // 停止视频缓存任务
//            VideoProxyCacheManager.getInstance().stopCacheTask(lastUrl)
//            VideoProxyCacheManager.getInstance().releaseProxyReleases(lastUrl)
//        } else {
//            return null
//        }
//        KeyValue.lastPlayingUrl = url
//        val playUrl = ProxyCacheUtils.getProxyUrl(url, null, null)
//        VideoProxyCacheManager.getInstance().startRequestVideoInfo(url, null, null)
//        VideoProxyCacheManager.getInstance().playingUrlMd5 = ProxyCacheUtils.computeMD5(url)
//        return playUrl
//    }
//
//    fun onPause() {
//        val lastUrl = KeyValue.lastPlayingUrl
//        if (RegexUtils.isURL(lastUrl)) {
//            VideoProxyCacheManager.getInstance().pauseCacheTask(lastUrl)
//        }
//    }
//
//    fun onResume() {
//        val lastUrl = KeyValue.lastPlayingUrl
//        if (RegexUtils.isURL(lastUrl)) {
//            VideoProxyCacheManager.getInstance().resumeCacheTask(lastUrl)
//        }
//    }
//}
//
//object CacheManager {
//    val cacheHandler: CacheHandler? by lazy {
//        if (KeyValue.cbCacheEnable) {
//            val saveFile = StorageUtils.getVideoFileDir(GlobalContext.getContext())
//            if (!saveFile.exists()) {
//                saveFile.mkdir()
//            }
//            val builder = VideoProxyCacheManager.Builder().
//            setFilePath(saveFile.absolutePath)              //缓存存储位置
//                .setConnTimeOut(60 * 1000)                 //网络连接超时
//                .setReadTimeOut(60 * 1000)                  //网络读超时
//                .setExpireTime(2 * 24 * 60 * 60 * 1000)     //2天的过期时间
//                .setMaxCacheSize(8L * 1024 * 1024 * 1024)   //2G的存储上限
//                //.setUseOkHttp(true)
//            VideoProxyCacheManager.getInstance().initProxyConfig(builder.build())
//            CacheHandler()
//        } else {
//            null
//        }
//    }
//}