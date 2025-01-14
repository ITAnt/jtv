package com.itant.jtv.ui.home.video

import androidx.lifecycle.MutableLiveData
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.ui.base.BaseViewModel
import com.miekir.mvvm.task.TaskJob
import com.miekir.mvvm.task.launchModelTask
import kotlinx.coroutines.delay


/**
 * finish调用后，ViewModel的onCleared先被调用，Activity的onDestroy才被调用
 */
class VideoModel: BaseViewModel() {
    /**
     * 播放重试
     */
    val retryPlayLiveData = MutableLiveData<Boolean>()

    /**
     * 播放出错重试
     */
    private var retryJob: TaskJob? = null
    private val cacheEnable = KeyValue.cacheEnable

    fun delayToRetryPlay() {
        if (cacheEnable) {
            return
        }

        cancelRetryPlay()
        retryJob = launchModelTask(
            {
                delay(15_000L)
                retryPlayLiveData.postValue(true)
                retryJob = null
            }
        )
    }

    fun cancelRetryPlay() {
        retryJob?.cancel()
        retryPlayLiveData.postValue(false)
        retryJob = null
    }
}