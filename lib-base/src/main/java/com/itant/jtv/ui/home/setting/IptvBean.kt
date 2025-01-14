package com.itant.jtv.ui.home.setting

import androidx.annotation.Keep

@Keep
data class IptvBean(
    val name: String,
    var url: String,
    var sourceList: MutableList<String>? = arrayListOf(),
    /**
     * 是否选中
     */
    var checked: Boolean = false,
    /**
     * 是否已收藏
     */
    var starred: Boolean = false
) {
    fun getSafeSourceList(): MutableList<String> {
        if (sourceList == null) {
            sourceList = arrayListOf()
        }
        return sourceList!!
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IptvBean

        return url == other.url
    }
}
