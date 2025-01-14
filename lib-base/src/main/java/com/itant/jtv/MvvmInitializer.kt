package com.itant.jtv

import android.content.Context
import androidx.startup.Initializer
import com.itant.jtv.widget.AnimationLoading
import com.miekir.mvvm.MvvmManager
import com.miekir.mvvm.core.view.anim.SlideAnimation
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.mmkv.MMKV


/**
 * 放弃多个Provider初始化的方式，会拖慢APP启动，共用官方的启动Provider
 */
class MvvmInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // 初始化本地存储
        MMKV.initialize(context.applicationContext)

        // MVVM相关设置
        MvvmManager.getInstance()
            .activityAnimation(SlideAnimation())
            .globalTaskLoading(AnimationLoading::class.java)

        /*ToastUtils.getDefaultMaker()
            .setTextSize(16)
            .setBgResource(R.drawable.shape_toast)
            .setTextColor(ContextCompat.getColor(context, R.color.white))
            .setGravity(Gravity.END or Gravity.BOTTOM, 64, 64)*/

        // 在音天项目看
        CrashReport.initCrashReport(context, "7174ccaad0", true)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // 初始化操作不依赖于其他Initializer，返回空列表即可
        //return arrayListOf(ContextInitializer::class.java)
        return emptyList()
    }
}
