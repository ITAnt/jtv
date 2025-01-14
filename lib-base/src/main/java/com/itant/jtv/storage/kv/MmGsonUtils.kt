package com.itant.jtv.storage.kv

import com.google.gson.Gson

object MmGsonUtils {
    val mmGson: Gson by lazy { Gson() }


    val isGsonAvailable: Boolean by lazy {
        try {
            Class.forName("com.google.gson.Gson")
            true
        } catch (e: Exception) {
            false
        }
    }
}