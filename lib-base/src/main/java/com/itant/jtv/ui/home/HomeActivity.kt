package com.itant.jtv.ui.home

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClipboardUtils
import com.itant.jtv.databinding.ActivityHomeBinding
import com.itant.jtv.manager.DataManager
import com.itant.jtv.manager.RouteManager
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.ui.base.BaseBindingActivity
import com.itant.jtv.ui.home.setting.SettingActivity
import com.itant.jtv.ui.home.setting.SettingModel
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.core.view.base.withLoadingDialog
import com.miekir.mvvm.core.vm.base.viewModel
import com.miekir.mvvm.extension.openActivity
import com.miekir.mvvm.extension.setSingleClick
import com.miekir.mvvm.widget.loading.LoadingType

/**
 * 首页
 */
class HomeActivity: BaseBindingActivity<ActivityHomeBinding>() {
    override fun onBindingInflate() = ActivityHomeBinding.inflate(layoutInflater)

    private val homeViewModel: HomeModel by viewModel()
    private val settingViewModel: SettingModel by viewModel()

    override fun onInit() {
        binding.btnLive.setSingleClick {
            // 如果直播列表为空，提示并跳转设置
            if (DataManager.liveChannelListLiveData.value.isNullOrEmpty()) {
                ToastUtils.showShort("当前视频源没有频道，去设置中添加吧")
                openActivity<SettingActivity>()
            } else {
                KeyValue.lastViewLive = true
                RouteManager.currentRoute?.navigateVideo(this)
            }
        }

        binding.btnStar.setSingleClick {
            // 如果收藏列表为空，提示
            if (DataManager.starChannelListLiveData.value?.filter { bean -> bean.starred}.isNullOrEmpty()) {
                if (DataManager.liveChannelListLiveData.value.isNullOrEmpty()) {
                    ToastUtils.showShort("当前视频源没有频道，去设置中添加吧")
                    openActivity<SettingActivity>()
                } else {
                    ToastUtils.showShort("还没有收藏视频，去直播频道看看吧")
                    KeyValue.lastViewLive = true
                    RouteManager.currentRoute?.navigateVideo(this)
                }
            } else {
                KeyValue.lastViewLive = false
                RouteManager.currentRoute?.navigateVideo(this)
            }
        }
        binding.btnStar.setOnLongClickListener {
            DataManager.starChannelListLiveData.value?.let {
                if (it.isNotEmpty()) {
                    val starStringBuilder = StringBuilder()
                    for ((index, bean) in it.withIndex()) {
                        starStringBuilder.append(bean.name).append(",").append(bean.url)
                        if (index != it.size-1) {
                            starStringBuilder.append("\n")
                        }
                    }
                    ClipboardUtils.copyText(starStringBuilder.toString())
                    ToastUtils.showShort("已复制收藏列表")
                }
            }
            true
        }

        // 跳转设置
        binding.btnSetting.setSingleClick { openActivity<SettingActivity>() }

        DataManager.liveChannelListLiveData.observe(this) {
            if (DataManager.firstStartUp && !it.isNullOrEmpty() && KeyValue.lastViewLive) {
                DataManager.firstStartUp = false
                RouteManager.currentRoute?.navigateVideo(this)
            }
        }

        DataManager.starChannelListLiveData.observe(this) {
            if (DataManager.firstStartUp && !it.isNullOrEmpty() && !KeyValue.lastViewLive) {
                DataManager.firstStartUp = false
                RouteManager.currentRoute?.navigateVideo(this)
            }
        }

        if (KeyValue.forceUpdate || KeyValue.neverSucceed) {
            settingViewModel.loadChannelLiveData.observe(this) { url ->
                withLoadingDialog(loadingType = LoadingType.STICKY) {
                    settingViewModel.loadLiveChannelList(url)
                }
            }
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                settingViewModel.loadIptvList()
            }
        } else {
            // 加载本地直播频道和收藏频道
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                homeViewModel.loadLocalChannelList()
            }
        }
    }

    override fun enableDestroyAnimation(): Boolean {
        return false
    }

    /**
     * 退出应用
     */
    private var lastBackMillis = 0L
    @Deprecated("Ignore")
    override fun onBackPressed() {
        if (System.currentTimeMillis()-lastBackMillis > 2000) {
            ToastUtils.showShort("再按一次退出应用")
            lastBackMillis = System.currentTimeMillis()
        } else {
            super.onBackPressed()
            finish()
            AppUtils.exitApp()
        }
    }
}