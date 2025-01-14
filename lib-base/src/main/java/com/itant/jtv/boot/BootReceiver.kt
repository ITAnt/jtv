package com.itant.jtv.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.blankj.utilcode.util.AppUtils
import com.itant.jtv.storage.kv.KeyValue

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (TextUtils.equals(intent?.action, Intent.ACTION_BOOT_COMPLETED) && KeyValue.autoLaunch) {
            AppUtils.launchApp(context?.packageName)
        }
    }
}