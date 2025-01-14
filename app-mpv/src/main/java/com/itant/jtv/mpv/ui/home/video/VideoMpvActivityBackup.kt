//package com.itant.jtv.mpv.ui.home.video
//
//import android.net.Uri
//import android.text.TextUtils
//import android.view.KeyEvent
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewTreeObserver
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import androidx.recyclerview.widget.RecyclerView.OnScrollListener
//import com.blankj.utilcode.util.RegexUtils
//import com.blankj.utilcode.util.ScreenUtils
//import com.itant.jtv.utils.ToastUtils
//import com.google.gson.reflect.TypeToken
//import com.itant.jtv.R
//import com.itant.jtv.manager.DataManager
//import com.itant.jtv.mpv.databinding.ActivityVideoMpvBinding
//import com.itant.jtv.mpv.widget.VideoView
//import com.itant.jtv.storage.kv.KeyValue
//import com.itant.jtv.storage.kv.MmGsonUtils
//import com.itant.jtv.ui.adapter.ChannelAdapter
//import com.itant.jtv.ui.base.BaseBindingActivity
//import com.itant.jtv.ui.home.HomeModel
//import com.itant.jtv.ui.home.setting.IptvAdapter
//import com.itant.jtv.ui.home.setting.IptvBean
//import com.itant.jtv.ui.home.setting.SettingModel
//import com.itant.jtv.ui.home.video.VideoModel
//import com.miekir.mvvm.core.view.base.withLoadingDialog
//import com.miekir.mvvm.core.vm.base.viewModel
//import com.miekir.mvvm.extension.setSingleClick
//import com.miekir.mvvm.widget.loading.LoadingType
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//
//
///**
// * 视频页（MPV SDK实现，支持硬解码）
// */
//class VideoMpvActivity : BaseBindingActivity<ActivityVideoMpvBinding>(), VideoView.PlayerCallback {
//    override fun onBindingInflate() = ActivityVideoMpvBinding.inflate(layoutInflater)
//
//    private val videoViewModel: VideoModel by viewModel()
//    private val homeViewModel: HomeModel by viewModel()
//    private val settingViewModel: SettingModel by viewModel()
//
//    private var currentVideoWidth = 0.0
//    private var currentVideoHeight = 0.0
//
//    /**
//     * 上一次弹出的视频播放信息对应的URL
//     */
//    private var lastToastUrl: String? = null
//    private val focusObserver = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
//        resetGoneChannel()
//        resetGoneMenu()
//        resetGoneIptv()
//    }
//
//    /**
//     * 频道适配器
//     */
//    private val channelAdapter by lazy {
//        ChannelAdapter { list, item ->
//            // 播放当前频道
//            playVideo(item.url)
//            lifecycleScope.launch(Dispatchers.IO) {
//                if (KeyValue.lastViewLive) {
//                    KeyValue.liveListGson = MmGsonUtils.mmGson.toJson(list)
//                } else {
//                    KeyValue.starListGson = MmGsonUtils.mmGson.toJson(list)
//                }
//            }
//        }
//    }
//
//    /**
//     * IPTV直播源适配器
//     */
//    private val iptvAdapter by lazy {
//        IptvAdapter { list, item ->
//            // 加载当前选中的IPTV对应的频道列表
//            lifecycleScope.launch(Dispatchers.IO) { KeyValue.iptvListGson = MmGsonUtils.mmGson.toJson(list) }
//            DataManager.liveChannelListLiveData.value = emptyList()
//            DataManager.liveChannelListLiveData.observe(this) {
//                if (it.isNotEmpty()) {
//                    DataManager.liveChannelListLiveData.removeObservers(this@VideoMpvActivity)
//                    onDataReady(it)
//                }
//            }
//            settingViewModel.loadChannelLiveData.postValue(item.url)
//        }
//    }
//
//    private val scrollListener = object : OnScrollListener() {
//        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//            super.onScrollStateChanged(recyclerView, newState)
//            resetGoneChannel()
//        }
//    }
//
//
//
//    /**
//     * AndroidStudio有Bug，如果是直接运行跑起来的话，按Home键后重新从桌面进入Activity会重新触发onCreate(默认启动模式)
//     */
//    override fun onInit() {
//        channelAdapter.isStateViewEnable = true
//        channelAdapter.stateView = LayoutInflater.from(this).inflate(R.layout.view_empty, null)
//        binding.rvChannel.layoutManager = LinearLayoutManager(this)
//        binding.rvChannel.adapter = channelAdapter
//        binding.rvChannel.addOnScrollListener(scrollListener)
//
//        binding.videoView.playerCallback = this
//
//        // 点击左侧弹出频道列表
//        binding.viewLeft.setSingleClick {
//            if (binding.viewIptv.visibility == View.VISIBLE) {
//                // 隐藏IPTV列表
//                hideIptvList()
//            } else if (binding.viewMenu.visibility == View.VISIBLE) {
//                hideMenu()
//            } else {
//                showChannelList()
//            }
//        }
//        // 长按左侧切换视频比例
//        binding.viewLeft.setOnLongClickListener {
//            changeVideoScaleType()
//            true
//        }
//
//        binding.viewCenter.setSingleClick {
//            if (binding.viewChannel.visibility == View.VISIBLE) {
//                hideChannelList()
//            } else if (binding.viewIptv.visibility == View.VISIBLE) {
//                hideIptvList()
//            } else {
//                // todo 弹出节目单 showMenu()
//            }
//        }
//        // 长按中间区域进行收藏
//        binding.viewCenter.setOnLongClickListener {
//            starOrUnstar()
//            true
//        }
//
//        // 点击右侧隐藏频道列表，弹IPTV列表
//        binding.viewRight.setSingleClick {
//            if (binding.viewChannel.visibility == View.VISIBLE) {
//                hideChannelList()
//            } else if (binding.viewMenu.visibility == View.VISIBLE) {
//                hideMenu()
//            } else {
//                // 弹IPTV源列表
//                if (KeyValue.lastViewLive) {
//                    showIptvList()
//                }
//            }
//        }
//        // 长按右侧改变视频源
//        binding.viewRight.setOnLongClickListener {
//            switchChannelSource()
//            true
//        }
//
//        // 焦点变动自动续时
//        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(focusObserver)
//
//        // 区分是直播还是收藏，获取到频道列表
//        if (KeyValue.lastViewLive) {
//            DataManager.liveChannelListLiveData.observe(this) {
//                DataManager.liveChannelListLiveData.removeObservers(this@VideoMpvActivity)
//                onDataReady(it)
//            }
//        } else {
//            DataManager.starChannelListLiveData.observe(this) {
//                DataManager.starChannelListLiveData.removeObservers(this@VideoMpvActivity)
//                val starList = it.filter { bean -> bean.starred }
//                onDataReady(starList)
//                lifecycleScope.launch(Dispatchers.IO) {
//                    KeyValue.starListGson = MmGsonUtils.mmGson.toJson(starList)
//                }
//            }
//        }
//
//        videoViewModel.retryPlayLiveData.observe(this) {
//            if (it) {
//                if (!TextUtils.isEmpty(currentVideoUrl)) {
//                    ToastUtils.showShort("重试中...")
//                }
//                playCurrentVideo()
//            }
//        }
//
//        binding.rbRefreshIptv.setSingleClick {
//            DataManager.liveChannelListLiveData.value = emptyList()
//            DataManager.liveChannelListLiveData.observe(this) {
//                if (it.isNotEmpty()) {
//                    DataManager.liveChannelListLiveData.removeObservers(this@VideoMpvActivity)
//                    onDataReady(it)
//                }
//            }
//            settingViewModel.loadIptvList()
//        }
//
//        setupVideoScaleType()
//        // 应用在后台被杀，重新启动
//        if (DataManager.firstStartUp) {
//            DataManager.firstStartUp = false
//            withLoadingDialog(loadingType = LoadingType.STICKY) {
//                homeViewModel.loadLocalChannelList()
//            }
//        }
//        loadIptvList()
//    }
//
//    private fun loadIptvList() {
//        if (!KeyValue.lastViewLive) {
//            return
//        }
//        iptvAdapter.isStateViewEnable = true
//        iptvAdapter.stateView = LayoutInflater.from(this).inflate(R.layout.view_empty, null)
//        binding.rvIptv.layoutManager = LinearLayoutManager(this)
//        binding.rvIptv.adapter = iptvAdapter
//
//        // 获取到IPTV列表
//        settingViewModel.iptvListLiveData.observe(this) {
//            iptvAdapter.submitList(it)
//        }
//
//        settingViewModel.loadChannelLiveData.observe(this) { url->
//            withLoadingDialog(loadingType = LoadingType.STICKY) {
//                settingViewModel.loadLiveChannelList(url)
//            }
//        }
//        settingViewModel.loadIptvList(true)
//    }
//
//    /**
//     * 数据加载完成
//     */
//    private fun onDataReady(itemList: List<IptvBean>) {
//        for (item in itemList) {
//            if (item.checked) {
//                channelAdapter.lastCheckedBean = item
//                break
//            }
//        }
//
//        var currentChannelIndex = itemList.indexOf(channelAdapter.lastCheckedBean)
//        if (currentChannelIndex == -1 && itemList.isNotEmpty()) {
//            currentChannelIndex = 0
//            channelAdapter.lastCheckedBean = itemList[currentChannelIndex]
//            itemList[currentChannelIndex].checked = true
//        }
//
//        binding.viewChannel.visibility = View.VISIBLE
//        binding.viewIptv.visibility = View.GONE
//        channelAdapter.submitList(itemList)
//
//        if (itemList.isNotEmpty()) {
//            binding.rvChannel.scrollToPosition(currentChannelIndex)
//            playVideo(itemList[currentChannelIndex].url)
//        }
//        resetGoneChannel()
//    }
//
//    /**
//     * 频道列表自动隐藏
//     */
//    private var resetGoneChannelJob: Job? = null
//    private var lastResetGoneChannelMillis = 0L
//    private fun resetGoneChannel() {
//        if (System.currentTimeMillis() - lastResetGoneChannelMillis < 1000L) {
//            return
//        }
//        lastResetGoneChannelMillis = System.currentTimeMillis()
//
//        if (binding.viewChannel.visibility != View.VISIBLE) {
//            return
//        }
//        resetGoneChannelJob?.cancel()
//        resetGoneChannelJob = lifecycleScope.launch {
//            delay(10_000L)
//            binding.viewChannel.visibility = View.GONE
//        }
//    }
//
//    /**
//     * 节目单自动隐藏
//     */
//    private var resetGoneMenuJob: Job? = null
//    private fun resetGoneMenu() {
//        if (binding.viewMenu.visibility != View.VISIBLE) {
//            return
//        }
//        resetGoneMenuJob?.cancel()
//        resetGoneMenuJob = lifecycleScope.launch {
//            delay(10_000L)
//            binding.viewMenu.visibility = View.GONE
//        }
//    }
//
//    /**
//     * 节目单自动隐藏
//     */
//    private var resetGoneIptvJob: Job? = null
//    private fun resetGoneIptv() {
//        if (binding.viewIptv.visibility != View.VISIBLE) {
//            return
//        }
//        resetGoneIptvJob?.cancel()
//        resetGoneIptvJob = lifecycleScope.launch {
//            delay(10_000L)
//            binding.viewIptv.visibility = View.GONE
//        }
//    }
//
//    /**
//     * 设置当前视频比例
//     */
//    private fun setupVideoScaleType(fromManually: Boolean = false) {
//        if (currentVideoWidth <=0 || currentVideoHeight <= 0) {
//            // 需要获取到当前视频尺寸才可以动态修改
//            return
//        }
//
//        when (KeyValue.videoScaleType) {
//            0 -> {
//                if (fromManually) {
//                    ToastUtils.showShort("默认")
//                }
//                scaleViewSize(currentVideoWidth, currentVideoHeight)
//            }
//
//            1 -> {
//                // 0f表示拉伸填充
//                if (fromManually) {
//                    ToastUtils.showShort("16:9")
//                }
//                scaleViewSize(16.0, 9.0)
//            }
//
//            2 -> {
//                if (fromManually) {
//                    ToastUtils.showShort("4:3")
//                }
//                scaleViewSize(4.0, 3.0)
//            }
//
//            3 -> {
//                if (fromManually) {
//                    ToastUtils.showShort("填充")
//                }
//                scaleViewSize(ScreenUtils.getScreenWidth()*1.0, ScreenUtils.getScreenHeight()*1.0)
//            }
//            else -> {}
//        }
//    }
//
//    private fun scaleViewSize(currentVideoWidth: Double, currentVideoHeight: Double) {
//        var scaledHeight = ScreenUtils.getScreenHeight()
//        val calWidth = (scaledHeight*currentVideoWidth*1.0f/currentVideoHeight).toInt()
//        val scaledWidth = if (calWidth > ScreenUtils.getScreenWidth()) {
//            scaledHeight = (ScreenUtils.getScreenWidth()*currentVideoHeight*1.0f/ ScreenUtils.getScreenWidth()).toInt()
//            ScreenUtils.getScreenWidth()
//        } else {
//            calWidth
//        }
//        binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
//            width = scaledWidth
//            height = scaledHeight
//        }
//        binding.videoView.postInvalidate()
//    }
//
//    /**
//     * 播放当前视频
//     */
//    private fun playCurrentVideo() {
//        if (RegexUtils.isURL(currentVideoUrl)) {
//            currentVideoUrl?.let {
//                playVideo(it)
//            }
//        }
//    }
//
//    private var currentVideoUrl: String? = ""
//    /**
//     * 播放频道，每播放一个频道，展示当前频道序号名称，视频分辨率
//     */
//    private fun playVideo(url: String) {
//        videoViewModel.cancelRetryPlay()
//
//        currentVideoWidth = 0.0
//        currentVideoHeight = 0.0
//        currentVideoUrl = url
//
//        // 开始播放视频
//        binding.videoView.playFile(Uri.parse(url).toString())
//
//        channelAdapter.lastCheckedBean?.let {
//            it.url = url
//            saveCurrentChannelList()
//        }
//    }
//
//    private fun starOrUnstar() {
//        if (canPerformKeyboard()) {
//            channelAdapter.lastCheckedBean?.let { targetBean ->
//                val index = channelAdapter.items.indexOf(targetBean)
//                if (index >= 0) {
//                    targetBean.starred = !targetBean.starred
//                    channelAdapter.notifyItemChanged(index)
//                    if (targetBean.starred) {
//                        ToastUtils.showShort("收藏成功")
//                    } else {
//                        ToastUtils.showShort("取消收藏")
//                    }
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        // 保存收藏频道列表
//                        val starList = ArrayList<IptvBean>()
//                        try {
//                            starList.addAll(MmGsonUtils.mmGson.fromJson<MutableList<IptvBean>>(KeyValue.starListGson, object : TypeToken<List<IptvBean>>() {}.type))
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//
//                        if (targetBean.starred) {
//                            // 添加收藏
//                            for (bean in starList) {
//                                // 已收藏
//                                if (TextUtils.equals(bean.name, targetBean.name) && TextUtils.equals(bean.url, targetBean.url)) {
//                                    return@launch
//                                }
//                            }
//                            starList.add(0, targetBean.copy(checked = false))
//                        } else {
//                            // 取消收藏
//                            var unstarIndex = -1
//                            for ((i, bean) in starList.withIndex()) {
//                                if (TextUtils.equals(bean.name, targetBean.name) && TextUtils.equals(bean.url, targetBean.url)) {
//                                    unstarIndex = i
//                                    break
//                                }
//                            }
//                            if (unstarIndex != -1) {
//                                starList.removeAt(unstarIndex)
//                            }
//                        }
//                        KeyValue.starListGson = MmGsonUtils.mmGson.toJson(starList)
//                        homeViewModel.loadLocalChannelList()
//                    }
//                }
//            }
//        }
//    }
//
//    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
//        // 所有的按键都需要在没有弹窗的时候生效（实测所有长按事件都没有触发）
//        if (!canPerformKeyboard()) {
//            return super.onKeyLongPress(keyCode, event)
//        }
//        when (keyCode) {
//            KeyEvent.KEYCODE_DPAD_LEFT -> {
//
//            }
//
//            KeyEvent.KEYCODE_DPAD_CENTER -> {
//
//            }
//
//            KeyEvent.KEYCODE_DPAD_RIGHT -> {
//
//            }
//        }
//        return super.onKeyLongPress(keyCode, event)
//    }
//
//    private var leftJob: Job? = null
//    private var rightJob: Job? = null
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        // 所有的按键都需要在没有弹窗的时候生效
//        if (!canPerformKeyboard()) {
//            return super.onKeyDown(keyCode, event)
//        }
//
//        when (keyCode) {
//            KeyEvent.KEYCODE_DPAD_LEFT -> {
//                if (leftJob != null) {
//                    leftJob?.cancel()
//                    // 相当于双击左键键切换视频比例！
//                    changeVideoScaleType()
//                    leftJob = null
//                } else {
//                    leftJob = lifecycleScope.launch {
//                        delay(1000L)
//                        // 相当于点击屏幕左侧
//                        showChannelList()
//                        leftJob = null
//                    }
//                }
//            }
//
//            KeyEvent.KEYCODE_DPAD_CENTER -> {
//                // todo 双击屏幕中间，弹出节目单 showMenu()
//                // 点击中间收藏/取消收藏
//                starOrUnstar()
//            }
//
//            KeyEvent.KEYCODE_DPAD_RIGHT -> {
//                if (rightJob != null) {
//                    rightJob?.cancel()
//                    // 双击右键切换频道源
//                    switchChannelSource()
//                    rightJob = null
//                } else {
//                    rightJob = lifecycleScope.launch {
//                        delay(1000L)
//                        // 短按右键，弹IPTV源
//                        if (KeyValue.lastViewLive) {
//                            showIptvList()
//                        }
//                        rightJob = null
//                    }
//                }
//            }
//
//            KeyEvent.KEYCODE_DPAD_UP -> {
//                // 切换到上一频道，前提是没有弹窗
//                switchChannel(false)
//            }
//
//            KeyEvent.KEYCODE_DPAD_DOWN -> {
//                // 切换到下一频道，前提是没有弹窗
//                switchChannel(true)
//            }
//        }
//        return super.onKeyDown(keyCode, event)
//    }
//
//    /**
//     * 切换视频源，前提是有更多的源
//     */
//    private fun switchChannelSource() {
//        if (canPerformKeyboard()) {
//            channelAdapter.lastCheckedBean?.let {
//                val currentSourceIndex = it.getSafeSourceList().indexOf(currentVideoUrl)
//                if (currentSourceIndex != -1 && it.getSafeSourceList().size > 1) {
//                    val newSourceIndex = if (currentSourceIndex + 1 >= it.getSafeSourceList().size) 0 else currentSourceIndex + 1
//                    ToastUtils.showShort("换源成功[${newSourceIndex+1}/${it.getSafeSourceList().size}]")
//                    playVideo(it.getSafeSourceList()[newSourceIndex])
//                } else {
//                    ToastUtils.showShort("当前频道只有一个视源")
//                }
//            }
//        }
//    }
//
//    /**
//     * @param next: true-下一频道，false-上一频道
//     */
//    private fun switchChannel(next: Boolean) {
//        if (canPerformKeyboard() && channelAdapter.lastCheckedBean != null) {
//            val index = channelAdapter.items.indexOf(channelAdapter.lastCheckedBean)
//            val newIndex = if (next) index + 1 else index - 1
//            if (newIndex < 0 || newIndex >= channelAdapter.items.size) {
//                return
//            }
//            channelAdapter.items[index].checked = false
//            channelAdapter.items[newIndex].checked = true
//            channelAdapter.lastCheckedBean = channelAdapter.items[newIndex]
//            channelAdapter.submitList(channelAdapter.items)
//            playVideo(channelAdapter.items[newIndex].url)
//            saveCurrentChannelList()
//        }
//    }
//
//    private fun saveCurrentChannelList() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            if (KeyValue.lastViewLive) {
//                KeyValue.liveListGson = MmGsonUtils.mmGson.toJson(channelAdapter.items)
//            } else {
//                KeyValue.starListGson = MmGsonUtils.mmGson.toJson(channelAdapter.items)
//            }
//        }
//    }
//
//    /**
//     * 展示频道列表
//     */
//    private fun showChannelList() {
//        if (canPerformKeyboard()) {
//            binding.viewChannel.visibility = View.VISIBLE
//            // 滚动到当前播放位置
//            val index = channelAdapter.items.indexOf(channelAdapter.lastCheckedBean)
//            if (index != -1) {
//                binding.rvChannel.scrollToPosition(index)
//            }
//            resetGoneChannel()
//        }
//    }
//
//    private fun hideChannelList() {
//        resetGoneChannelJob?.cancel()
//        binding.viewChannel.visibility = View.GONE
//    }
//
//    /**
//     * 弹出节目单
//     */
//    private fun showMenu() {
//        if (canPerformKeyboard()) {
//            binding.viewMenu.visibility = View.VISIBLE
//            resetGoneMenu()
//        }
//    }
//
//    private fun hideMenu() {
//        resetGoneMenuJob?.cancel()
//        binding.viewMenu.visibility = View.GONE
//    }
//
//    private fun showIptvList() {
//        if (canPerformKeyboard()) {
//            binding.viewIptv.visibility = View.VISIBLE
//            // 滚动到当前位置
//            for ((index, item) in iptvAdapter.items.withIndex()) {
//                if (item.checked) {
//                    binding.rvIptv.scrollToPosition(index)
//                    break
//                }
//            }
//            resetGoneIptv()
//        }
//    }
//
//    private fun hideIptvList() {
//        resetGoneIptvJob?.cancel()
//        binding.viewIptv.visibility = View.GONE
//    }
//
//    /**
//     * 改变当前视频比例
//     */
//    private fun changeVideoScaleType() {
//        if (canPerformKeyboard()) {
//            var scaleType = KeyValue.videoScaleType + 1
//            if (scaleType > 3) {
//                scaleType = 0
//            }
//            KeyValue.videoScaleType = scaleType
//            setupVideoScaleType(true)
//        }
//    }
//
//    /**
//     * 频道和节目单弹窗，任意一个在显示就不响应按键
//     */
//    private fun canPerformKeyboard(): Boolean {
//        return binding.viewChannel.visibility != View.VISIBLE && binding.viewMenu.visibility != View.VISIBLE && binding.viewIptv.visibility != View.VISIBLE
//    }
//
//    override fun onPause() {
//        super.onPause()
//        videoViewModel.cancelRetryPlay()
//        binding.videoView.paused = true
//    }
//
//    override fun onResume() {
//        super.onResume()
//        playCurrentVideo()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        binding.rvChannel.removeOnScrollListener(scrollListener)
//        window.decorView.viewTreeObserver.removeOnGlobalFocusChangeListener(focusObserver)
//    }
//
//    private var lastBackMillis = 0L
//    override fun onBackPressed() {
//        if (binding.viewChannel.visibility == View.VISIBLE || binding.viewMenu.visibility == View.VISIBLE || binding.viewIptv.visibility == View.VISIBLE) {
//            hideChannelList()
//            hideMenu()
//            hideIptvList()
//        } else {
//            if (System.currentTimeMillis() - lastBackMillis > 2000) {
//                ToastUtils.showShort("再按一次退出视频")
//                lastBackMillis = System.currentTimeMillis()
//            } else {
//                super.onBackPressed()
//            }
//        }
//    }
//
//    override fun onBuffering() {
//        videoViewModel.delayToRetryPlay()
//    }
//
//    override fun onPositionChanged() {
//        videoViewModel.cancelRetryPlay()
//    }
//
//    override fun onVideoSizeChanged(videoWidth: Double, videoHeight: Double) {
//        // 提示当前播放的频道
//        var toastInfo = channelAdapter.lastCheckedBean?.name ?: ""
//        if (!TextUtils.isEmpty(toastInfo) &&
//            !TextUtils.equals(lastToastUrl, currentVideoUrl) &&
//            videoWidth > 0 && videoHeight > 0) {
//            currentVideoWidth = videoWidth
//            currentVideoHeight = videoHeight
//            videoViewModel.retryPlayLiveData.postValue(false)
//            lastToastUrl = currentVideoUrl
//            channelAdapter.lastCheckedBean?.sourceList?.let {
//                if (it.indexOf(currentVideoUrl) != -1 && it.size > 1) {
//                    toastInfo = toastInfo.plus("[源${it.indexOf(currentVideoUrl)+1}/${it.size}]")
//                }
//            }
//            toastInfo = toastInfo.plus(" ${videoWidth} x ${videoHeight}")
//            ToastUtils.showShort(toastInfo)
//            setupVideoScaleType()
//        }
//    }
//}