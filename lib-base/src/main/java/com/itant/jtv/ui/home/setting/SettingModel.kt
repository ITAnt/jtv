package com.itant.jtv.ui.home.setting

import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import com.blankj.utilcode.util.RegexUtils
import com.itant.jtv.manager.DataManager
import com.itant.jtv.manager.ThreadManager.handleGsonPool
import com.itant.jtv.net.ApiManager
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.storage.kv.MmGsonUtils
import com.itant.jtv.ui.base.BaseViewModel
import com.itant.jtv.utils.PinyinUtils
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.task.TaskJob
import com.miekir.mvvm.task.launchModelTask
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * 设置任务
 */
class SettingModel: BaseViewModel() {
    val loadChannelLiveData = MutableLiveData<String>()

    /**
     * 加载IPTV列表
     */
    fun loadIptvList(url:String = KeyValue.jtvUrl): TaskJob {
        return launchModelTask(
            {
                val iptvList = ArrayList<IptvBean>()

                // 访问分享地址获得分享内容
                val htmlResult = ApiManager.spider.getHtmlResponse(url).body()?.string()

                // 查找所有匹配项，能get成功才去查
                htmlResult?.let { result ->
                    // 定义正则表达式，开头：不是双引号，不是换行符；中间：?,http；结尾：不是双引号，不是换行符
                    //val regex = Regex("[^\"\\n>]+?[,，]http[^\"\\n<]+")
                    // 在低端手机上会内存溢出
                    //val iptvStringList = regex.findAll(result).map { it.value }.toMutableSet().toTypedArray()

                    // 定义正则表达式
                    val regex = "[^\"\\n>]+?[,，](http|HTTP)[^\"\\n<]+"
                    // 创建Pattern对象
                    val pattern: Pattern = Pattern.compile(regex)
                    // 创建Matcher对象
                    val matcher: Matcher = pattern.matcher(result)
                    val iptvStringList = LinkedHashSet<String>()
                    // 查找所有匹配的内容
                    while (matcher.find()) {
                        iptvStringList.add(matcher.group())
                    }

                    if (iptvStringList.isNotEmpty()) {
                        for (iptvString in iptvStringList) {
                            var nameUrlList = iptvString.split(",")
                            if (nameUrlList.size < 2) {
                                nameUrlList = iptvString.split("，")
                            }
                            var key = nameUrlList[0].replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                            val value = nameUrlList[1].replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                            if (TextUtils.isEmpty(key)) {
                                key = "未知IPTV"
                            }
                            if (RegexUtils.isURL(value)) {
                                val bean = IptvBean(key, value)
                                if (!iptvList.contains(bean)) {
                                    iptvList.add(bean)
                                }
                            }
                        }
                    }
                }

                // 默认自动使用第一个IPTV源
                if (iptvList.isNotEmpty()) {
                    iptvList[0].checked = true
                }

                if (iptvList.isNotEmpty()) {
                    loadChannelLiveData.postValue(iptvList[0].url)
                }

                // 从网络获取的才需要存到本地
                handleGsonPool.submit {
                    KeyValue.iptvListGson = MmGsonUtils.mmGson.toJson(iptvList)
                }
                iptvList
            }, onSuccess = {
                if (!it.isNullOrEmpty()) {
                    DataManager.iptvListLiveData.postValue(it)
                }
            }, onResult = { normalResult, errorResult ->
                if (normalResult.isNullOrEmpty()) {
                    ToastUtils.showShort("IPTV源为空")
                } else {
                    ToastUtils.showShort("获取IPTV源成功")
                }
            }
        )
    }

    /**
     * 加载直播频道列表
     */
    fun loadLiveChannelList(iptvUrl: String? = null): TaskJob {
        var channelList = ArrayList<IptvBean>()
        return launchModelTask(
            {
                if (!TextUtils.isEmpty(iptvUrl)) {
                    // 访问分享地址获得分享内容
                    val htmlResult = ApiManager.spider.getHtmlResponse(iptvUrl!!).body()?.string()

                    // 查找所有匹配项，能get成功才去查
                    htmlResult?.let { result ->
                        // 定义正则表达式，开头：不是双引号，不是换行符；中间：?,http；结尾：不是双引号，不是换行符
                        // todo 以后可能要兼容其他协议，如mitv之类
                        //val regex = Regex("[^\"\\n>]+?[,，]http[^\"\\n<]+")
                        // 在低端手机上会内存溢出
                        //val channelStringList = regex.findAll(result).map { it.value }.toMutableSet().toTypedArray()

                        // 定义正则表达式
                        val regex = "[^\"\\n>]+?[,，](http|rtmp|rtsp|HTTP|RTMP|RTSP)[^\"\\n<]+"
                        // 创建Pattern对象
                        val pattern: Pattern = Pattern.compile(regex)
                        // 创建Matcher对象
                        val matcher: Matcher = pattern.matcher(result)
                        val channelStringList = LinkedHashSet<String>()
                        // 查找所有匹配的内容
                        while (matcher.find()) {
                            channelStringList.add(matcher.group())
                        }

                        val ipv4Only = KeyValue.ipv4Only
                        if (channelStringList.isNotEmpty()) {
                            // TVBox源
                            for (channelString in channelStringList) {
                                var nameUrlList = channelString.split(",")
                                if (nameUrlList.size < 2) {
                                    nameUrlList = channelString.split("，")
                                }
                                val key = nameUrlList[0].replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                                var value = nameUrlList[1].replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                                if (value.contains("$")) {
                                    value = value.substring(0, value.indexOf("$"))
                                }
                                if (!TextUtils.isEmpty(key) && RegexUtils.isURL(value)) {
                                    val willAdd = !ipv4Only || !value.contains("""://[""")
                                    if (willAdd) {
                                        if (KeyValue.combineChannel) {
                                            var channelBean = channelList.find { it.name == key }
                                            if (channelBean == null) {
                                                channelBean = IptvBean(key, value)
                                                channelBean.getSafeSourceList().add(value)
                                                channelList.add(channelBean)
                                            } else {
                                                channelBean.getSafeSourceList().add(value)
                                            }
                                        } else {
                                            val channelBean = IptvBean(key, value)
                                            channelBean.getSafeSourceList().add(value)
                                            channelList.add(channelBean)
                                        }
                                    }
                                }
                            }
                        } else {
                            // m3u源
                            val lines = result.lines()
                            val keyValue = arrayOf("", "")
                            for (line in lines) {
                                if (line.contains(",") || lines.contains("，")) {
                                    var key = "未知频道"
                                    try {
                                        key = line.substring(line.lastIndexOf(",")+1).replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    keyValue[0] = key
                                } else if (line.startsWith("http:")) {
                                    var value = line.replace(" ", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(",", "").replace("，", "")
                                    if (value.contains("$")) {
                                        value = value.substring(0, value.indexOf("$"))
                                    }
                                    val willAdd = !ipv4Only || !value.contains("""://[""")
                                    if (RegexUtils.isURL(value) && willAdd) {
                                        keyValue[1] = value
                                    }
                                }
                                if (!TextUtils.isEmpty(keyValue[0]) && RegexUtils.isURL(keyValue[1])) {
                                    if (KeyValue.combineChannel) {
                                        var channelBean = channelList.find { it.name == keyValue[0] }
                                        if (channelBean == null) {
                                            channelBean = IptvBean(keyValue[0], keyValue[1])
                                            channelBean.getSafeSourceList().add(keyValue[1])
                                            channelList.add(channelBean)
                                        } else {
                                            channelBean.getSafeSourceList().add(keyValue[1])
                                        }
                                    } else {
                                        val channelBean = IptvBean(keyValue[0], keyValue[1])
                                        channelBean.getSafeSourceList().add(keyValue[1])
                                        channelList.add(channelBean)
                                    }
                                    // 必须放这里，否则解析不到完整一条数据
                                    keyValue[0] = ""
                                    keyValue[1] = ""
                                }
                            }
                        }
                    }

                    // 加载新的频道列表
                    if (channelList.isNotEmpty()) {
                        if (KeyValue.greenMode) {
                            channelList = channelList.filter {
                                !it.name.contains("carib", true) &&
                                        !it.name.contains("swag", true) &&
                                        !it.name.contains("MyCamTv", true) &&
                                !it.name.contains("TokyoHot", true) &&
                                !it.name.contains("一本道", true)
                            } as ArrayList<IptvBean>
                        }

                        if (channelList.isNotEmpty()) {
                            // 排序
                            if (KeyValue.channelSort) {
                                channelList.sortWith { bean1, bean2 ->
                                    PinyinUtils.ccs2Pinyin(bean1.name).compareTo(PinyinUtils.ccs2Pinyin(bean2.name))
                                }
                            }
                            channelList[0].checked = true
                            KeyValue.neverSucceed = false
                        }
                    }
                }

                KeyValue.liveListGson = MmGsonUtils.mmGson.toJson(channelList)
                channelList
            }, onResult = { normalResult, errorResult ->
                if (normalResult.isNullOrEmpty()) {
                    ToastUtils.showShort("当前IPTV下没有直播频道")
                } else {
                    DataManager.liveChannelListLiveData.postValue(channelList)
                    if (channelList.isNotEmpty()) {
                        ToastUtils.showShort("获取直播频道成功")
                    }
                }
            }
        )
    }
}