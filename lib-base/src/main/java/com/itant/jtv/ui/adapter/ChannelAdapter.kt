package com.itant.jtv.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.blankj.utilcode.util.ClipboardUtils
import com.chad.library.adapter4.BaseQuickAdapter
import com.chad.library.adapter4.viewholder.DataBindingHolder
import com.itant.jtv.R
import com.itant.jtv.databinding.ItemChannelBinding
import com.itant.jtv.manager.DataManager
import com.itant.jtv.manager.ThreadManager.handleGsonPool
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.home.setting.IptvBean
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.extension.setSingleClick

class ChannelAdapter(private val onClickListener: (bean: IptvBean) -> Unit) : BaseQuickAdapter<IptvBean, DataBindingHolder<ItemChannelBinding>>() {

    override fun onCreateViewHolder(context: Context, parent: ViewGroup, viewType: Int): DataBindingHolder<ItemChannelBinding> {
        return DataBindingHolder<ItemChannelBinding>(R.layout.item_channel, parent)
    }

    var lastCheckedBean: IptvBean? = null
    private val showingStar = !KeyValue.lastViewLive
    override fun onBindViewHolder(holder: DataBindingHolder<ItemChannelBinding>, position: Int, item: IptvBean?) {
        item?.let { bean ->
            holder.binding.tvName.text = "${getFormatIndex(position+1)} ${bean.name}"
            holder.binding.tvName.setSingleClick {
                if (lastCheckedBean != bean) {
                    lastCheckedBean?.let { it.checked = false }
                    // 选中了该项，需要高亮并持久化，需要区分IPTV和频道
                    bean.checked = true
                    lastCheckedBean = bean
                }
                // 替代notifyItemChanged，解决部分手机上刷新item不生效的问题
                notifyDataSetChanged()

                // 解决第一次进来选中没有生效的问题，因为从网络获取到结果post了一次，存到本地，又从本地获取post了一次，
                // 但是因为视频页面观察到之后又remove了observer，即只使用了第一次的结果，退出到主页后，第二次进来使用的是第一次从网络获取到并存到本地的数据
                // 所以要进行同步，这是解决办法之一，另外一个解决办法就是每次收藏/取消收藏后保存到本地的数据，在首页都要重新获取，即在onResume去获取本地数据
                if (showingStar) {
                    if (DataManager.starChannelListLiveData.value != items) {
                        DataManager.starChannelListLiveData.value = items
                    }
                } else {
                    if (DataManager.liveChannelListLiveData.value != items) {
                        DataManager.liveChannelListLiveData.value = items
                    }
                }

                onClickListener.invoke(bean)

                handleGsonPool.submit {
                    if (KeyValue.lastViewLive) {
                        KeyValue.liveListGson = MmGsonUtils.mmGson.toJson(DataManager.liveChannelListLiveData.value)
                    } else {
                        KeyValue.starListGson = MmGsonUtils.mmGson.toJson(DataManager.starChannelListLiveData.value)
                    }
                }
            }

            holder.binding.tvName.setOnLongClickListener {
                ClipboardUtils.copyText(item.url)
                ToastUtils.showShort("频道地址已复制")
                true
            }

            if (showingStar) {
                holder.binding.ivStar.visibility = if (bean.starred) View.VISIBLE else View.INVISIBLE
            } else {
                holder.binding.ivStar.visibility = View.INVISIBLE
            }
            holder.binding.tvName.isChecked = item.checked
        }
    }

    private fun getFormatIndex(position: Int): String {
        if (position < 10) {
            return "00${position} "
        }

        if (position < 100) {
            return "0${position} "
        }

        return position.toString()
    }
}