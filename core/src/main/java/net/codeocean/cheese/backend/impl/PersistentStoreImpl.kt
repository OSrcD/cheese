package net.codeocean.cheese.backend.impl

import android.content.Context
import android.util.Base64
import com.elvishew.xlog.XLog
import net.codeocean.cheese.core.BaseEnv
import net.codeocean.cheese.core.api.PersistentStore

object PersistentStoreImpl : PersistentStore, BaseEnv {
    // Add a prefix to mark Base64 encoded byte arrays
    private const val BYTE_ARRAY_PREFIX = "BYTES_"

    override fun save(name: String, key: String, value: Any) {
        val sharedPref = cx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        when (value) {
            is String -> {
                editor.putString(key, value)
                XLog.i("存储String值 key:$key value:$value")
            }
            is Int -> {
                editor.putInt(key, value)
                XLog.i("存储Int值 key:$key value:$value")
            }
            is Boolean -> {
                editor.putBoolean(key, value)
                XLog.i("存储Boolean值 key:$key value:$value")
            }
            is ByteArray -> {
                val encodedValue = BYTE_ARRAY_PREFIX + Base64.encodeToString(value, Base64.DEFAULT)
                editor.putString(key, encodedValue)
                XLog.i("存储ByteArray值 key:$key value:${value.contentToString()} encodedValue:$encodedValue")
            }
            else -> return
        }
        editor.apply()
    }

    override fun rm(name: String, key: String) {
        val sharedPref = cx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.remove(key)
        editor.apply()
    }

    override fun get(name: String, key: String): Any? {
        val sharedPref = cx.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (!sharedPref.contains(key)) return null

        val all = sharedPref.all
        val value = all[key] ?: return null

        return when (value) {
            is String -> {
                if (value.startsWith(BYTE_ARRAY_PREFIX)) {
                    try {
                        val base64Str = value.removePrefix(BYTE_ARRAY_PREFIX)
                        val decoded = Base64.decode(base64Str, Base64.DEFAULT)
                        XLog.i("获取ByteArray值 key:$key value:$base64Str decoded:${decoded.contentToString()}")
                        decoded
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                } else {
                    XLog.i("获取String值 key:$key value:$value")
                    value
                }
            }
            is Int -> {
                XLog.i("获取Int值 key:$key value:$value")
                value
            }
            is Boolean -> {
                XLog.i("获取Boolean值 key:$key value:$value")
                value
            }
            is Float -> {
                XLog.i("获取Float值 key:$key value:$value")
                value
            }
            is Long -> {
                XLog.i("获取Long值 key:$key value:$value")
                value
            }
            else -> null
        }
    }
}