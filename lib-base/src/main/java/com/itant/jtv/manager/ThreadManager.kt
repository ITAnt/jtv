package com.itant.jtv.manager

import android.text.TextUtils
import com.blankj.utilcode.util.ThreadUtils
import com.google.gson.reflect.TypeToken
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.home.setting.IptvBean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ThreadManager {
    fun startOrUnStar(targetBean: IptvBean) {
        handleGsonPool.submit {
            // 保存收藏频道列表
            val starList = ArrayList<IptvBean>()
            try {
                starList.addAll(MmGsonUtils.mmGson.fromJson<MutableList<IptvBean>>(KeyValue.starListGson, object : TypeToken<List<IptvBean>>() {}.type))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (targetBean.starred) {
                // 添加收藏
                for (bean in starList) {
                    // 已收藏
                    if (TextUtils.equals(bean.name, targetBean.name) && TextUtils.equals(bean.url, targetBean.url)) {
                        return@submit
                    }
                }
                starList.add(0, targetBean.copy(checked = false))
            } else {
                // 取消收藏
                var unstarIndex = -1
                for ((i, bean) in starList.withIndex()) {
                    if (TextUtils.equals(bean.name, targetBean.name) && TextUtils.equals(bean.url, targetBean.url)) {
                        unstarIndex = i
                        break
                    }
                }
                if (unstarIndex != -1) {
                    starList.removeAt(unstarIndex)
                }
            }
            KeyValue.starListGson = MmGsonUtils.mmGson.toJson(starList)
        }
    }

    /**
     * 注意：不要shutdown，shutdown之后不会执行任务了，导致任务永远不会结束
     */
    val destroyPool: ExecutorService by lazy { ThreadUtils.getFixedPool(10) }

    /**
     * 用于阻塞执行，最多缓存一个任务
     */
    val actionPool: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    /*val actionPool: ExecutorService by lazy {
        ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(1), DiscardOldestPolicy())
    }*/

    /**
     * 用于保存数据执行，最多缓存一个任务
     * 任务完成：handleGsonPool.taskCount == handleGsonPool.completedTaskCount
     */
    val handleGsonPool by lazy {
        ThreadPoolExecutor(
            1, 1,
            0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
        )
    }
}