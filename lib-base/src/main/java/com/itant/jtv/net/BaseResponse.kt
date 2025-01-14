package com.itant.jtv.net

import android.text.TextUtils
import androidx.annotation.Keep
import com.miekir.mvvm.exception.TaskException
import com.miekir.mvvm.task.net.NetResponse
import com.miekir.mvvm.tools.ToastTools

/**
 * 统一返回封装
 * 在launchModelTask/launchGlobalTask使用时，不能点到成员变量data，否则不会处理服务器正常工作时返回的错误码，即返回值必须是BaseResponse；
 * 如果请求正常，但是服务器返回非200，如400，则还要用Response包裹一层，返回Response<BaseResponse<..>>
 * @author Miekir
 */
@Keep
class BaseResponse<T> : NetResponse {
    /**
     * 返回状态代码
     */
    var code = 0

    /**
     * 返回信息
     */
    var message: String? = null

    var extra: String? = null

    /**
     * 返回的实体数据
     */
    var resultObj: T? = null

    /**
     * 调用一下，防止有些不需要使用到结果的接口不断提交失败，及时发现隐藏的重大错误如登录过期等
     */
    override fun valid(): Boolean {
        if (code != 200) {
            if (code == -99/* && UserManager.isLogin*/) {
                // UserManager.logout()
                ToastTools.showShort("登录已过期，请重新登录")
                // 不要轻易使用topActivity，因为其状态是否正在销毁具有不确定性，最好是新开task进行跳转
                // 重新登录 ActivityTools.jump("com.itant.LoginActivity")
                // 过期界面Toast的时候，发现要Toast的字符串为空字符串就不用弹了（封装一下）。
                // 如果必须登录才能获取，如不同的人有不同的内容，则需要关闭所有界面到达登录界面，
                // 否则过期后必须及时刷新界面（BaseActivity/BaseFragment新增全局刷新Observer）
                throw TaskException(code, "")
            }
            if (TextUtils.isEmpty(message)) {
                throw TaskException(code)
            } else {
                throw TaskException(code, message!!)
            }
        }
        return true
    }
}