package com.itant.jtv.ui.home

import android.text.TextUtils
import com.google.gson.reflect.TypeToken
import com.itant.jtv.manager.DataManager
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.base.BaseViewModel
import com.itant.jtv.ui.home.setting.IptvBean
import com.miekir.mvvm.task.TaskJob
import com.miekir.mvvm.task.launchModelTask


/**
 * 首页任务
 */
class HomeModel: BaseViewModel() {

    /**
     * 加载本地直播列表和收藏列表
     */
    fun loadLocalChannelList(): TaskJob {
        return launchModelTask(
            {
                loadLocalData()
            }
        )
    }

    private fun loadLocalData() {
        val iptvListGson = KeyValue.iptvListGson
        val iptvList = ArrayList<IptvBean>()
        if (!TextUtils.isEmpty(iptvListGson)) {
            try {
                iptvList.addAll(
                    MmGsonUtils.mmGson.fromJson<MutableList<IptvBean>>(iptvListGson, object : TypeToken<List<IptvBean>>() {}.type)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 读的过程中有异常，比如后台线程还没写完，重新加载
                clearLocalData()
                return
            }
        }

        // 直播列表
        val liveListGson = KeyValue.liveListGson
        val liveList = ArrayList<IptvBean>()
        if (!TextUtils.isEmpty(liveListGson)) {
            try {
                liveList.addAll(
                    MmGsonUtils.mmGson.fromJson<MutableList<IptvBean>>(liveListGson, object : TypeToken<List<IptvBean>>() {}.type)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 读的过程中有异常，比如后台线程还没写完，重新加载
                clearLocalData()
                return
            }
        }

        // 收藏列表
        val starListGson = KeyValue.starListGson
        val starList = ArrayList<IptvBean>()
        if (!TextUtils.isEmpty(starListGson)) {
            try {
                starList.addAll(
                    MmGsonUtils.mmGson.fromJson<MutableList<IptvBean>>(starListGson, object : TypeToken<List<IptvBean>>() {}.type)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 读的过程中有异常，比如后台线程还没写完，重新加载
                clearLocalData()
                return
            }
        }

        DataManager.iptvListLiveData.postValue(iptvList)
        DataManager.liveChannelListLiveData.postValue(liveList)
        DataManager.starChannelListLiveData.postValue(starList)
    }

    private fun clearLocalData() {
        val emptyList = MmGsonUtils.mmGson.toJson(emptyList<IptvBean>())
        KeyValue.iptvListGson = emptyList
        KeyValue.liveListGson = emptyList
        KeyValue.starListGson = emptyList
        KeyValue.neverSucceed = true
    }
}