package com.itant.jtv.ui.home.setting

import android.content.Context
import android.view.ViewGroup
import com.blankj.utilcode.util.ClipboardUtils
import com.chad.library.adapter4.BaseQuickAdapter
import com.chad.library.adapter4.viewholder.DataBindingHolder
import com.itant.jtv.R
import com.itant.jtv.databinding.ItemIptvBinding
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.extension.setSingleClick

class IptvAdapter(private val onClickListener: (list: List<IptvBean>, bean: IptvBean) -> Unit) : BaseQuickAdapter<IptvBean, DataBindingHolder<ItemIptvBinding>>() {

    override fun onCreateViewHolder(context: Context, parent: ViewGroup, viewType: Int): DataBindingHolder<ItemIptvBinding> {
        return DataBindingHolder<ItemIptvBinding>(R.layout.item_iptv, parent)
    }

    private var lastCheckedBean: IptvBean? = null
    override fun onBindViewHolder(holder: DataBindingHolder<ItemIptvBinding>, position: Int, item: IptvBean?) {
        item?.let { bean ->
            holder.binding.tvName.text = bean.name
            holder.binding.tvName.setSingleClick {
                if (lastCheckedBean != bean) {
                    lastCheckedBean?.let {
                        it.checked = false
                        val lastCheckIndex = items.indexOf(it)
                        if (lastCheckIndex != -1) {
                            notifyItemChanged(lastCheckIndex)
                        }
                    }
                    // 选中了该项，需要高亮并持久化，需要区分IPTV和频道
                    bean.checked = !bean.checked
                    notifyItemChanged(position)
                    lastCheckedBean = bean
                }
                onClickListener.invoke(items, bean)
            }

            holder.binding.tvName.setOnLongClickListener {
                ClipboardUtils.copyText(item.url)
                ToastUtils.showShort("IPTV地址已复制")
                true
            }

            if (item.checked) {
                lastCheckedBean = bean
            }
            holder.binding.tvName.isChecked = item.checked
        }
    }
}