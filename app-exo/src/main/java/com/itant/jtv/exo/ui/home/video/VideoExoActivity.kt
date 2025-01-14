package com.itant.jtv.exo.ui.home.video

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.blankj.utilcode.util.RegexUtils
import com.blankj.utilcode.util.ScreenUtils
import com.itant.jtv.R
import com.itant.jtv.exo.databinding.ActivityVideoExoBinding
import com.itant.jtv.exo.player.PlayStatusListener
import com.itant.jtv.exo.player.Players
import com.itant.jtv.manager.DataManager
import com.itant.jtv.manager.ThreadManager
import com.itant.jtv.manager.ThreadManager.handleGsonPool
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.adapter.ChannelAdapter
import com.itant.jtv.ui.base.BaseBindingActivity
import com.itant.jtv.ui.home.HomeModel
import com.itant.jtv.ui.home.setting.IptvAdapter
import com.itant.jtv.ui.home.setting.IptvBean
import com.itant.jtv.ui.home.setting.SettingModel
import com.itant.jtv.ui.home.video.VideoModel
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.core.view.base.withLoadingDialog
import com.miekir.mvvm.core.vm.base.viewModel
import com.miekir.mvvm.extension.setSingleClick
import com.miekir.mvvm.widget.loading.LoadingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * 视频页
 */
@OptIn(UnstableApi::class)
class VideoExoActivity : BaseBindingActivity<ActivityVideoExoBinding>(), PlayStatusListener {
    override fun onBindingInflate() = ActivityVideoExoBinding.inflate(layoutInflater)

    private val videoViewModel: VideoModel by viewModel()
    private val homeViewModel: HomeModel by viewModel()
    private val settingViewModel: SettingModel by viewModel()

    private val mPlayers by lazy { Players(this) }

    /**
     * 上次换台时间
     */
    private var lastUpDownloadMillis = 0L

    private val focusObserver = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        if (System.currentTimeMillis() - lastResetGoneChannelMillis < 1000L) {
            return@OnGlobalFocusChangeListener
        }
        if (System.currentTimeMillis() - lastResetGoneIptvMillis < 1000L) {
            return@OnGlobalFocusChangeListener
        }
        lastResetGoneChannelMillis = System.currentTimeMillis()
        lastResetGoneIptvMillis = System.currentTimeMillis()
        resetGoneChannel()
        resetGoneIptv()
    }

    /**
     * 频道适配器
     */
    private val channelAdapter by lazy {
        ChannelAdapter { item ->
            // 播放当前频道
            playVideo(item.url, 0)
        }
    }

    /**
     * IPTV直播源适配器
     */
    private val iptvAdapter by lazy {
        IptvAdapter { list, item ->
            // 加载当前选中的IPTV对应的频道列表
            handleGsonPool.submit {
                KeyValue.iptvListGson = MmGsonUtils.mmGson.toJson(list)
            }
            binding.viewIptv.visibility = View.GONE
            DataManager.liveChannelListLiveData.value = emptyList()
            DataManager.liveChannelListLiveData.observe(this) {
                if (it.isNotEmpty()) {
                    DataManager.liveChannelListLiveData.removeObservers(this@VideoExoActivity)
                    onDataReady(it)
                }
            }
            settingViewModel.loadChannelLiveData.postValue(item.url)
        }
    }

    private val channelScrollListener = object : OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (System.currentTimeMillis() - lastResetGoneChannelMillis < 1000L) {
                return
            }
            lastResetGoneChannelMillis = System.currentTimeMillis()
            resetGoneChannel()
        }
    }
    private val iptvScrollListener = object : OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (System.currentTimeMillis() - lastResetGoneIptvMillis < 1000L) {
                return
            }
            lastResetGoneIptvMillis = System.currentTimeMillis()
            resetGoneIptv()
        }
    }

    private var controllerEnable = false
        set(value) {
            if (value) {
                binding.viewLeft.visibility = View.GONE
                binding.viewRight.visibility = View.GONE
                binding.viewCenter.visibility = View.GONE
                binding.viewChannel.visibility = View.GONE
                binding.viewIptv.visibility = View.GONE
                binding.viewControllerShadow.visibility = View.VISIBLE
                ToastUtils.showShort("控制开启")
                binding.videoView.useController = true
                binding.videoView.showController()
            } else {
                binding.viewLeft.visibility = View.VISIBLE
                binding.viewRight.visibility = View.VISIBLE
                binding.viewCenter.visibility = View.VISIBLE
                binding.viewControllerShadow.visibility = View.GONE
                ToastUtils.showShort("控制关闭")
                binding.videoView.useController = false
                binding.videoView.hideController()
            }
            field = value
        }

    private val controllerListener = PlayerView.ControllerVisibilityListener { visibility ->
        if (visibility != View.VISIBLE) {
            binding.videoView.useController = false
        }
    }
    /**
     * AndroidStudio有Bug，如果是直接运行跑起来的话，按Home键后重新从桌面进入Activity会重新触发onCreate(默认启动模式)
     */
    override fun onInit() {
        mPlayers.init(binding.videoView)
        mPlayers.setPlayStatusListener(this)
        binding.viewController.setSingleClick {
            controllerEnable = !controllerEnable
        }
        // 触摸动作透传给播放器
        binding.viewControllerShadow.setOnTouchListener { v, event ->
            binding.videoView.onTouchEvent(event)
            false
        }

        channelAdapter.isStateViewEnable = true
        channelAdapter.stateView = LayoutInflater.from(this).inflate(R.layout.view_empty, null)
        binding.rvChannel.layoutManager = LinearLayoutManager(this)
        binding.rvChannel.adapter = channelAdapter
        binding.rvChannel.addOnScrollListener(channelScrollListener)

        // 区分是直播还是收藏，获取到频道列表
        if (KeyValue.lastViewLive) {
            DataManager.liveChannelListLiveData.observe(this) {
                DataManager.liveChannelListLiveData.removeObservers(this@VideoExoActivity)
                onDataReady(it)
            }
        } else {
            DataManager.starChannelListLiveData.observe(this) {
                DataManager.starChannelListLiveData.removeObservers(this@VideoExoActivity)
                val starList = it.filter { bean -> bean.starred }
                onDataReady(starList)
            }
        }

        // 点击左侧弹出频道列表
        binding.viewLeft.setSingleClick {
            if (binding.viewIptv.visibility == View.VISIBLE) {
                // 隐藏IPTV列表
                hideIptvList()
                return@setSingleClick
            }
            showChannelList()
        }
        // 长按左侧切换视频比例
        binding.viewLeft.setOnLongClickListener {
            changeVideoScaleType()
            true
        }

        binding.viewCenter.setSingleClick {
            if (binding.viewChannel.visibility == View.VISIBLE) {
                hideChannelList()
                return@setSingleClick
            }

            if (binding.viewIptv.visibility == View.VISIBLE) {
                hideIptvList()
                return@setSingleClick
            }
        }
        // 长按中间区域进行收藏
        binding.viewCenter.setOnLongClickListener {
            starOrUnstar()
            true
        }

        // 点击右侧隐藏频道列表，弹IPTV列表
        binding.viewRight.setSingleClick {
            if (binding.viewChannel.visibility == View.VISIBLE) {
                hideChannelList()
                return@setSingleClick
            }
            // 弹IPTV源列表
            if (KeyValue.lastViewLive) {
                showIptvList()
            }
        }
        // 长按右侧改变视频源
        binding.viewRight.setOnLongClickListener {
            switchChannelSource()
            true
        }

        // 焦点变动自动续时
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(focusObserver)

        videoViewModel.retryPlayLiveData.observe(this) {
            if (it) {
                if (RegexUtils.isURL(currentVideoUrl)) {
                    ToastUtils.showShort("重试中...")
                    playCurrentVideo()
                } else {
                    videoViewModel.cancelRetryPlay()
                }
            }
        }

        binding.rbRefreshIptv.setSingleClick {
            DataManager.liveChannelListLiveData.value = emptyList()
            DataManager.liveChannelListLiveData.observe(this) {
                if (it.isNotEmpty()) {
                    DataManager.liveChannelListLiveData.removeObservers(this@VideoExoActivity)
                    onDataReady(it)
                }
            }
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                settingViewModel.loadIptvList()
            }
        }

        initIptvList()
        setupVideoScaleType()
        // 应用在后台被杀，重新启动，firstStartUp会恢复为true，且全局变量里的livedata也没数据了，导致列表为空，所以要重新从本地加载
        if (DataManager.firstStartUp) {
            DataManager.firstStartUp = false
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                homeViewModel.loadLocalChannelList()
            }
        }
    }

    private fun initIptvList() {
        if (!KeyValue.lastViewLive) {
            return
        }
        iptvAdapter.isStateViewEnable = true
        iptvAdapter.stateView = LayoutInflater.from(this).inflate(R.layout.view_empty, null)
        binding.rvIptv.layoutManager = LinearLayoutManager(this)
        binding.rvIptv.adapter = iptvAdapter
        binding.rvIptv.addOnScrollListener(iptvScrollListener)

        // 获取到IPTV列表
        DataManager.iptvListLiveData.observe(this) {
            binding.viewIptv.visibility = View.VISIBLE
            iptvAdapter.submitList(it)
            // 滚动到当前位置
            for ((index, item) in it.withIndex()) {
                if (item.checked) {
                    binding.rvIptv.post {
                        binding.rvIptv.scrollToPosition(index)
                        binding.viewIptv.visibility = View.GONE
                    }
                    break
                }
            }
        }

        settingViewModel.loadChannelLiveData.observe(this) { url->
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                settingViewModel.loadLiveChannelList(url)
            }
        }
    }

    /**
     * 数据加载完成
     */
    private fun onDataReady(itemList: List<IptvBean>) {
        for (item in itemList) {
            if (item.checked) {
                channelAdapter.lastCheckedBean = item
                break
            }
        }

        var currentChannelIndex = itemList.indexOf(channelAdapter.lastCheckedBean)
        if (currentChannelIndex == -1 && itemList.isNotEmpty()) {
            currentChannelIndex = 0
            channelAdapter.lastCheckedBean = itemList[currentChannelIndex]
            itemList[currentChannelIndex].checked = true
        }

        // View.VISIBLE放着post里，解决scrollToPosition不准确的问题
        binding.rvChannel.post {
            binding.viewChannel.visibility = View.VISIBLE
            channelAdapter.submitList(itemList)
            if (itemList.isNotEmpty()) {
                binding.rvChannel.scrollToPosition(currentChannelIndex)
                binding.rvChannel.requestFocus()
                playVideo(itemList[currentChannelIndex].url, 0)
            }
            resetGoneChannel()
        }
    }

    /**
     * 频道列表自动隐藏
     */
    private var resetGoneChannelJob: Job? = null
    private var lastResetGoneChannelMillis = 0L
    private fun resetGoneChannel() {
        if (binding.viewChannel.visibility != View.VISIBLE) {
            return
        }

        resetGoneChannelJob?.cancel()
        resetGoneChannelJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(10_000L)
            withContext(Dispatchers.Main) {
                binding.viewChannel.visibility = View.GONE
            }
        }
    }

    /**
     * 节目单自动隐藏
     */
    private var resetGoneIptvJob: Job? = null
    private var lastResetGoneIptvMillis = 0L
    private fun resetGoneIptv() {
        if (binding.viewIptv.visibility != View.VISIBLE) {
            return
        }
        resetGoneIptvJob?.cancel()
        resetGoneIptvJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(10_000L)
            withContext(Dispatchers.Main) {
                binding.viewIptv.visibility = View.GONE
            }
        }
    }

    /**
     * 设置当前视频比例
     */
    private fun setupVideoScaleType(fromManually: Boolean = false) {
        val scaleType = KeyValue.videoScaleType

        binding.videoView.post {
            if (scaleType in 1..3) {
                binding.videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL)
            } else {
                binding.videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
            }

            var toastInfo = "默认"
            when (scaleType) {
                1 -> {
                    toastInfo = "16:9"
                    applyVideoScaleType(binding.videoView, 16, 9)
                    //(binding.videoView.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "16:9"
                }

                2 -> {
                    toastInfo = "4:3"
                    applyVideoScaleType(binding.videoView, 4, 3)
                    //(binding.videoView.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "4:3"
                }

                3 -> {
                    toastInfo = "填充"
                    binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                        width = ScreenUtils.getScreenWidth()
                        height = ScreenUtils.getScreenHeight()
                    }
                    //(binding.videoView.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "${ScreenUtils.getScreenWidth()}:${ScreenUtils.getScreenHeight()}"
                }

                else -> {
                    toastInfo = "默认"
                    binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                        width = ScreenUtils.getScreenWidth()
                        height = ScreenUtils.getScreenHeight()
                    }
                }
            }
            binding.videoView.invalidate()

            if (fromManually) {
                ToastUtils.showShort(toastInfo)
            }
        }
    }

    private fun applyVideoScaleType(view: View, currentVideoWidth: Int, currentVideoHeight: Int) {
        var scaledHeight = ScreenUtils.getScreenHeight()
        val calWidth = (scaledHeight * currentVideoWidth * 1.0f / currentVideoHeight).toInt()
        val scaledWidth = if (calWidth > ScreenUtils.getScreenWidth()) {
            scaledHeight = (ScreenUtils.getScreenWidth() * currentVideoHeight * 1.0f / ScreenUtils.getScreenWidth()).toInt()
            ScreenUtils.getScreenWidth()
        } else {
            calWidth
        }

        view.layoutParams = view.layoutParams.apply {
            width = scaledWidth
            height = scaledHeight
        }
    }

    /**
     * 播放当前视频
     */
    private fun playCurrentVideo() {
        if (RegexUtils.isURL(currentVideoUrl)) {
            currentVideoUrl?.let {
                playVideo(it)
            }
        }
    }

    private var currentVideoUrl: String? = ""
    /**
     * 播放频道，每播放一个频道，展示当前频道序号名称，视频分辨率
     * -1：不提示 0：点击或换台
     */
    private fun playVideo(url: String, type: Int = -1) {
        videoViewModel.cancelRetryPlay()
        if (type == 0) {
            channelAdapter.lastCheckedBean?.let { ToastUtils.showShort(it.name) }
        }
        currentVideoUrl = url

        mPlayers.clear()
        mPlayers.stop()
        mPlayers.setMediaSource(url)

        channelAdapter.lastCheckedBean?.let {
            it.url = url
            saveCurrentChannelList()
        }
    }

    private fun starOrUnstar() {
        if (canPerformKeyboard()) {
            channelAdapter.lastCheckedBean?.let { targetBean ->
                val index = channelAdapter.items.indexOf(targetBean)
                if (index >= 0) {
                    targetBean.starred = !targetBean.starred
                    channelAdapter.notifyItemChanged(index)
                    if (targetBean.starred) {
                        ToastUtils.showShort("收藏成功")
                    } else {
                        ToastUtils.showShort("取消收藏")
                    }
                    ThreadManager.startOrUnStar(targetBean)
                }
            }
        }
    }

    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var lastCenterMillis = 0L
    private val starUnstarRunnable = Runnable { starOrUnstar() }
    private val mainHandler = Handler(Looper.getMainLooper())
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 所有的按键都需要在没有弹窗的时候生效
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPress()
            return true
        }

        if (!canPerformKeyboard()) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val timeSpan = System.currentTimeMillis() - lastSeekMillis
                if (timeSpan < 500L) {
                    return true
                }
                lastSeekMillis = System.currentTimeMillis()

                if (leftJob != null || timeSpan < 1000L) {
                    leftJob?.cancel()
                    leftJob = null
                    // 快退
                    seek(false)
                } else {
                    leftJob = lifecycleScope.launch(Dispatchers.IO) {
                        delay(1000L)
                        withContext(Dispatchers.Main) {
                            // 相当于点击屏幕左侧
                            showChannelList()
                        }
                        lastSeekDuration = 0L
                        lastSeekMillis = 0L
                        leftJob = null
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // 双击屏幕中间，改变分辨率
                if (System.currentTimeMillis() - lastCenterMillis > 3000L) {
                    // 点击中间收藏/取消收藏
                    mainHandler.postDelayed(starUnstarRunnable, 1000L)
                } else {
                    mainHandler.removeCallbacks(starUnstarRunnable)
                    changeVideoScaleType()
                }
                lastCenterMillis = System.currentTimeMillis()
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val timeSpan = System.currentTimeMillis() - lastSeekMillis
                if (timeSpan < 500L) {
                    return true
                }
                lastSeekMillis = System.currentTimeMillis()

                if (leftJob != null || timeSpan < 1000L) {
                    rightJob?.cancel()
                    rightJob = null
                    // 双击右键切换频道源
                    //switchChannelSource()
                    // 快进
                    seek(true)
                } else {
                    if (KeyValue.lastViewLive) {
                        rightJob = lifecycleScope.launch(Dispatchers.IO) {
                            delay(1000L)
                            withContext(Dispatchers.Main) {
                                // 短按右键，弹IPTV源
                                showIptvList()
                            }
                            lastSeekDuration = 0L
                            lastSeekMillis = 0L
                            rightJob = null
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                // 切换到上一频道，前提是没有弹窗
                switchChannel(false)
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // 切换到下一频道，前提是没有弹窗
                switchChannel(true)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private val minSeekDuration = 5 * 60_000L
    private var lastSeekDuration = 0L
    private var lastSeekMillis = 0L
    private fun seek(forward: Boolean) {
        if (mPlayers.duration < minSeekDuration) {
            return
        }

        var targetTime = mPlayers.position
        if (lastSeekDuration == 0L) {
            lastSeekDuration = 30_000L
        }

        if (lastSeekDuration < 120_000L) {
            if (forward) {
                targetTime += lastSeekDuration
            } else {
                targetTime -= lastSeekDuration
            }
            lastSeekDuration += 30_000L
        } else {
            if (forward) {
                targetTime += 120_000L
            } else {
                targetTime -= 120_000L
            }
        }


        if (targetTime <= 0L || targetTime >= mPlayers.duration) {
            return
        }

        binding.videoView.setControllerVisibilityListener(controllerListener)
        mPlayers.seekTo(targetTime)
        binding.videoView.useController = true
        binding.videoView.showController()
        videoViewModel.cancelRetryPlay()
    }

    /**
     * 切换视频源，前提是有更多的源
     */
    private fun switchChannelSource() {
        if (canPerformKeyboard()) {
            channelAdapter.lastCheckedBean?.let {
                val currentSourceIndex = it.getSafeSourceList().indexOf(currentVideoUrl)
                if (currentSourceIndex != -1 && it.getSafeSourceList().size > 1) {
                    val newSourceIndex = if (currentSourceIndex + 1 >= it.getSafeSourceList().size) 0 else currentSourceIndex + 1
                    ToastUtils.showShort("换源成功[${newSourceIndex+1}/${it.getSafeSourceList().size}]")
                    playVideo(it.getSafeSourceList()[newSourceIndex])
                } else {
                    ToastUtils.showShort("当前频道只有一个视源")
                }
            }
        }
    }

    /**
     * @param next: true-下一频道，false-上一频道
     */
    private fun switchChannel(next: Boolean) {
        if (canPerformKeyboard() && channelAdapter.lastCheckedBean != null) {
            // 禁止频繁换台
            if (System.currentTimeMillis() - lastUpDownloadMillis < 1000L) {
                return
            }
            lastUpDownloadMillis = System.currentTimeMillis()

            val index = channelAdapter.items.indexOf(channelAdapter.lastCheckedBean)
            val newIndex = if (next) index + 1 else index - 1
            if (newIndex < 0 || newIndex >= channelAdapter.items.size) {
                ToastUtils.showShort("已到达尽头")
                return
            }
            channelAdapter.items[index].checked = false
            channelAdapter.items[newIndex].checked = true
            channelAdapter.lastCheckedBean = channelAdapter.items[newIndex]
            channelAdapter.submitList(channelAdapter.items)
            playVideo(channelAdapter.items[newIndex].url, 0)
            saveCurrentChannelList()
        }
    }

    private fun saveCurrentChannelList() {
        handleGsonPool.submit {
            if (KeyValue.lastViewLive) {
                KeyValue.liveListGson = MmGsonUtils.mmGson.toJson(channelAdapter.items)
            } else {
                KeyValue.starListGson = MmGsonUtils.mmGson.toJson(channelAdapter.items)
            }
        }
    }

    /**
     * 展示频道列表
     */
    private fun showChannelList() {
        if (canPerformKeyboard()) {
            binding.viewChannel.visibility = View.VISIBLE
            // 滚动到当前播放位置
            val index = channelAdapter.items.indexOf(channelAdapter.lastCheckedBean)
            if (index != -1) {
                binding.rvChannel.post {
                    binding.rvChannel.requestFocus()
                }
            }
            resetGoneChannel()
        }
    }

    private fun hideChannelList() {
        resetGoneChannelJob?.cancel()
        binding.viewChannel.visibility = View.GONE
    }

    private fun showIptvList() {
        if (canPerformKeyboard()) {
            binding.viewIptv.visibility = View.VISIBLE
            binding.rvIptv.post {
                binding.rvIptv.requestFocus()
            }
            binding.viewIptv.post { resetGoneIptv() }
        }
    }

    private fun hideIptvList() {
        resetGoneIptvJob?.cancel()
        binding.viewIptv.visibility = View.GONE
    }

    /**
     * 改变当前视频比例
     */
    private fun changeVideoScaleType() {
        if (canPerformKeyboard()) {
            var scaleType = KeyValue.videoScaleType + 1
            if (scaleType > 3) {
                scaleType = 0
            }
            KeyValue.videoScaleType = scaleType
            setupVideoScaleType(true)
        }
    }

    /**
     * 频道和节目单弹窗，任意一个在显示就不响应按键
     */
    private fun canPerformKeyboard(): Boolean {
        return binding.viewChannel.visibility != View.VISIBLE && binding.viewIptv.visibility != View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        videoViewModel.cancelRetryPlay()
        mPlayers.pause()
    }

    override fun onResume() {
        super.onResume()
        mPlayers.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rvChannel.removeOnScrollListener(channelScrollListener)
        binding.rvIptv.removeOnScrollListener(iptvScrollListener)
        window.decorView.viewTreeObserver.removeOnGlobalFocusChangeListener(focusObserver)
        mPlayers.release()
        mainHandler.removeCallbacks(starUnstarRunnable)
    }

    private var lastBackMillis = 0L
    private fun onBackPress() {
        controllerEnable = false
        if (binding.viewChannel.visibility == View.VISIBLE || binding.viewIptv.visibility == View.VISIBLE) {
            hideChannelList()
            hideIptvList()
        } else {
            if (System.currentTimeMillis() - lastBackMillis > 2000) {
                ToastUtils.showShort("再按一次退出视频")
                lastBackMillis = System.currentTimeMillis()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onError() {
        videoViewModel.delayToRetryPlay()
    }

    override fun onPlay() {
        videoViewModel.cancelRetryPlay()
    }
}