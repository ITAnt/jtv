package com.itant.jtv.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itant.jtv.R
import com.miekir.mvvm.widget.loading.TaskLoading

/**
 * @author 詹子聪
 * @date 2021-11-30 19:59
 */
class AnimationLoading : TaskLoading() {
    private var lv_circle: ImageView? = null
    private var tv_loading: TextView? = null
    private var loadingAnimator: ValueAnimator? = null

    override fun newLoadingDialog(activity: AppCompatActivity): Dialog {
        // 首先得到整个View
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.view_loading, null)
        // 获取整个布局
        val dialogLayout = dialogView.findViewById<View>(R.id.dialog_view) as LinearLayout
        // 页面中的LoadingView
        lv_circle = dialogView.findViewById(R.id.lv_circle)
        // 页面中显示文本
        tv_loading = dialogView.findViewById<View>(R.id.tv_loading) as TextView
        if (!TextUtils.isEmpty(mMessage)) {
            tv_loading!!.text = mMessage
            tv_loading!!.visibility = View.VISIBLE
        } else {
            tv_loading!!.visibility = View.GONE
        }
        val dialog = Dialog(activity, R.style.LoadingDialog)
        dialog.setContentView(
            dialogLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        mDialog = dialog
        return dialog
    }

    override fun onShow() {
        if (!TextUtils.isEmpty(mMessage)) {
            tv_loading?.text = mMessage
        }

        if (lv_circle != null && loadingAnimator == null) {
            loadingAnimator = ObjectAnimator.ofFloat(lv_circle, View.ROTATION, 0f, 360f).apply {
                setDuration(1800)
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
            }.apply {
                repeatMode = ValueAnimator.RESTART
                startDelay = 0L
                start()
            }
        }
    }

    override fun onDismiss() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        lv_circle = null
    }
}
