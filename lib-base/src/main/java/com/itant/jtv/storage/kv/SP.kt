package com.itant.jtv.storage.kv

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.annotation.Keep
import androidx.preference.PreferenceManager
import com.miekir.mvvm.context.GlobalContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Type
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * MMKV封装
 * @param name key
 * @param default 默认值
 * @param preferenceName 配置文件名
 * 验证了：Double也可以
 */
@Keep
class SP<T>(
    val name: String,
    private val default: T,
    private val type: Type? = null,
    private val mode: Int = Context.MODE_PRIVATE,
    private val preferenceName:String? = null
) : ReadWriteProperty<Any?, T> {
    private val mKeyValue: SharedPreferences by lazy {
        if (TextUtils.isEmpty(preferenceName)) {
            PreferenceManager.getDefaultSharedPreferences(GlobalContext.getContext())
        } else {
            GlobalContext.getContext().getSharedPreferences(preferenceName, mode)
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return executeGet(name, default)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        executePut(name, value)
    }

    private fun executePut(name: String, value: T) = with(mKeyValue.edit()) {
        when (value) {
            is Long -> putLong(name, value)
            is String -> putString(name, value)
            is Int -> putInt(name, value)
            is Boolean -> putBoolean(name, value)
            is Float -> putFloat(name, value)
            is Double -> putString(name, value.toString())
            else -> {
                if (default == null) {
                    throw IllegalArgumentException("Parameter 'default' cannot be null for $name")
                }
                /*if (type == null || !MmGsonUtils.isGsonAvailable) {
                    //throw IllegalArgumentException("Type parameter required for $name")
                    putString(name, serialize(value))
                } else {
                    putString(name, MmGsonUtils.mmGson.toJson(value))
                }*/
                putString(name, MmGsonUtils.mmGson.toJson(value))
            }
        }
    }.commit()

    private fun executeGet(name: String, default: T): T = with(mKeyValue) {
        val res = when (default) {
            is Long -> getLong(name, default)
            is String -> getString(name, default)
            is Int -> getInt(name, default)
            is Boolean -> getBoolean(name, default)
            is Float -> getFloat(name, default)
            is Double -> getString(name, default.toString())?.toDouble() ?: default
            else -> {
                /*if (type == null || !MmGsonUtils.isGsonAvailable) {
                    decodeFromObject()
                } else {
                    val stringValue: String? = getString(name, "")
                    try {
                        MmGsonUtils.mmGson.fromJson<T>(stringValue, type)
                        //gson.fromJson<T>(stringValue, TypeToken.getParameterized(default!!::class.java).type)
                    } catch (e: Exception) {
                        default
                    }
                }*/
                val stringValue: String? = getString(name, "")
                try {
                    MmGsonUtils.mmGson.fromJson<T>(stringValue, type)
                    //gson.fromJson<T>(stringValue, TypeToken.getParameterized(default!!::class.java).type)
                } catch (e: Exception) {
                    default
                }
            }
        } ?: return@with default
        return try {
            default!!::class.java.cast(res) ?: default
        } catch (e: Exception) {
            default
        }
    }

    private fun decodeFromObject(): T {
        val stringValue: String? = this.mKeyValue.getString(name, "")
        return if (TextUtils.isEmpty(stringValue)) {
            default
        } else {
            try {
                deSerialization(stringValue!!)
            } catch (e: Exception) {
                default
            }
        }
    }

    /**
     * 序列化对象
     * @return
     */
    private fun <A> serialize(obj: A): String? {
        var serStr: String?

        ByteArrayOutputStream().use { byteArrayOutputStream ->
            ObjectOutputStream(byteArrayOutputStream).use { objectOutputStream ->
                objectOutputStream.writeObject(obj)
                serStr = byteArrayOutputStream.toString("ISO-8859-1")
                serStr = java.net.URLEncoder.encode(serStr, "UTF-8")
            }
        }

        return serStr
    }

    /**
     * 反序列化对象
     * @param str
     */
    private fun deSerialization(str: String): T {
        val redStr = java.net.URLDecoder.decode(str, "UTF-8")
        val byteArrayInputStream = ByteArrayInputStream(
            redStr.toByteArray(charset("ISO-8859-1"))
        )
        val objectInputStream = ObjectInputStream(
            byteArrayInputStream
        )
        val obj = objectInputStream.readObject()
        objectInputStream.close()
        byteArrayInputStream.close()
        return try {
            default!!::class.java.cast(obj) ?: default
        } catch (e: Exception) {
            default
        }
    }

    /**
     * 删除全部数据
     */
    fun clearPreference() {
        mKeyValue.edit().clear().commit()
    }

    /**
     * 根据key删除存储数据
     */
    fun clearPreference(key: String) {
        mKeyValue.edit().remove(key).commit()
    }

    /**
     * 查询某个key是否已经存在
     * @param key
     * @return
     */
    fun contains(key: String): Boolean {
        return mKeyValue.contains(key)
    }

    /**
     * @return 返回所有的键值对
     */
    fun getAll(): Map<String, *> {
        return mKeyValue.all
    }
}