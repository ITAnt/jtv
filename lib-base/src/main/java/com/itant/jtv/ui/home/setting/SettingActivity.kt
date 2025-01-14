package com.itant.jtv.ui.home.setting

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.RegexUtils
import com.itant.jtv.databinding.ActivitySettingBinding
import com.itant.jtv.manager.DataManager
import com.itant.jtv.manager.ThreadManager.handleGsonPool
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.base.BaseBindingActivity
import com.itant.jtv.ui.home.HomeModel
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.core.view.base.withLoadingDialog
import com.miekir.mvvm.core.vm.base.viewModel
import com.miekir.mvvm.extension.setSingleClick
import com.miekir.mvvm.widget.loading.LoadingType

/**
 * 设置
 */
class SettingActivity: BaseBindingActivity<ActivitySettingBinding>() {
    override fun onBindingInflate() = ActivitySettingBinding.inflate(layoutInflater)

    private val settingViewModel: SettingModel by viewModel()
    private val homeViewModel: HomeModel by viewModel()

    /**
     * IPTV直播源适配器
     */
    private val iptvAdapter by lazy {
        IptvAdapter { list, item ->
            // 加载当前选中的IPTV对应的频道列表
            handleGsonPool.submit {
                KeyValue.iptvListGson = MmGsonUtils.mmGson.toJson(list)
            }

            settingViewModel.loadChannelLiveData.postValue(item.url)
        }
    }

    override fun onInit() {
        binding.cbForceUpdate.isChecked = KeyValue.forceUpdate
        binding.cbForceUpdate.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.forceUpdate = isChecked
        }

        binding.cbCombineChannel.isChecked = KeyValue.combineChannel
        binding.cbCombineChannel.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.combineChannel = isChecked
        }

        binding.cbAutoLaunch.isChecked = KeyValue.autoLaunch
        binding.cbAutoLaunch.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.autoLaunch = isChecked
        }

        binding.cbGreenMode.isChecked = KeyValue.greenMode
        binding.cbGreenMode.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.greenMode = isChecked
        }

        binding.cbIpv4Only.isChecked = KeyValue.ipv4Only
        binding.cbIpv4Only.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.ipv4Only = isChecked
        }

        binding.cbHardCodec.isChecked = KeyValue.hardCodec
        binding.cbHardCodec.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.hardCodec = isChecked
        }

        binding.cbCacheEnable.isChecked = KeyValue.cacheEnable
        binding.cbCacheEnable.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.cacheEnable = isChecked
        }

        binding.cbChannelSort.isChecked = KeyValue.channelSort
        binding.cbChannelSort.setOnCheckedChangeListener { _, isChecked ->
            KeyValue.channelSort = isChecked
        }

        binding.radioGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == binding.rbTips.id) {
                binding.includeTips.root.visibility = View.VISIBLE
                binding.includeVideo.root.visibility = View.GONE
            } else if (checkedId == binding.rbVideo.id) {
                binding.includeTips.root.visibility = View.GONE
                binding.includeVideo.root.visibility = View.VISIBLE
            }
        }

        binding.includeTips.tipsSetVideo.setSingleClick { binding.radioGroup.check(binding.rbVideo.id) }
        binding.includeVideo.btnSave.setSingleClick {
            val url = binding.includeVideo.etUrl.text.toString()
            if (!RegexUtils.isURL(url)) {
                ToastUtils.showShort("请输入有效的视频源分享链接")
                return@setSingleClick
            }
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                settingViewModel.loadIptvList(url = url)
            }
        }

        binding.includeVideo.rvIptv.layoutManager = LinearLayoutManager(this)
        binding.includeVideo.rvIptv.adapter = iptvAdapter

        // 获取到IPTV列表
        DataManager.iptvListLiveData.observe(this) {
            iptvAdapter.submitList(it)
            if (it.isNotEmpty()) {
                KeyValue.jtvUrl = binding.includeVideo.etUrl.text.toString()
            }
        }

        settingViewModel.loadChannelLiveData.observe(this) { url->
            withLoadingDialog(loadingType = LoadingType.STICKY) {
                settingViewModel.loadLiveChannelList(url)
            }
        }

        binding.includeVideo.etUrl.setText(KeyValue.jtvUrl)
    }
}