package net.codeocean.cheese.core.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.elvishew.xlog.XLog
import com.hjq.toast.Toaster
import net.codeocean.cheese.backend.impl.PathImpl
import net.codeocean.cheese.core.CoreEnv
import net.codeocean.cheese.core.CoreFactory
import net.codeocean.cheese.core.Script
import net.codeocean.cheese.core.runtime.debug.remote.DebugController
import net.codeocean.cheese.core.utils.AssetsUtils
import net.codeocean.cheese.core.utils.HttpUtils
import net.codeocean.cheese.core.utils.ZipUtils
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.File
import java.util.concurrent.Future
import kotlin.concurrent.thread

object ScriptExecutionController {
    fun Map<String, Future<*>>.cancelAndClean(key: String): Boolean {
        return this[key]?.let { future ->
            if (!future.isDone && !future.isCancelled) {
                future.cancel(true)
            } else {
                true
            }
        } ?: true
    }

    fun runRelease(script: Script) {
        XLog.i("开始运行1")



        thread {
            if (CoreEnv.isFirstLaunch()) {
                XLog.i("正在加载JS依赖文件")
                Toaster.show("正在加载JS依赖文件")
                AssetsUtils.copyFileToSD(
                    CoreEnv.envContext.context,
                    "release.zip",
                    PathImpl.WORKING_DIRECTORY.path + "/release.zip"
                )
                ZipUtils.decompress(
                    "${PathImpl.WORKING_DIRECTORY.path}/release.zip",
                    PathImpl.WORKING_DIRECTORY.path, ""
                )
                Toaster.show("JS依赖文件加载完毕")
                XLog.i("JS依赖文件加载完毕")
            }
        }

        CoreFactory.getWebView().runWebView("Keep")
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                println("WebView 已完成加载")

                if (CoreEnv.executorMap.cancelAndClean("run")) {
                    val executor = DebugController.createNamedThreadPool()
                    CoreEnv.executorMap["run"] = executor.submit {
                        script.run()
                    }
                }
                // 一次性监听，接收后立即销毁
                LocalBroadcastManager.getInstance(CoreEnv.envContext.context)
                    .unregisterReceiver(this)
            }
        }

        val filter = IntentFilter("webview.loaded")
        LocalBroadcastManager.getInstance(CoreEnv.envContext.context)
            .registerReceiver(broadcastReceiver, filter)

    }

    fun downloadAndExtractDebugZip(
        downloadUrl: String = "download",
        targetDir: String = PathImpl.WORKING_DIRECTORY.path
    ): Boolean {
        val debugZip = File("${targetDir}/debug.zip").apply {
            if (exists()) delete()
        }
        return try {
            // 下载文件
            HttpUtils.downloadFile(
                "http://${CoreEnv.runTime.ip}/$downloadUrl",
                debugZip.path
            )
            // 验证并解压
            if (debugZip.exists()) {
                ZipUtils.decompress(
                    debugZip.path,
                    targetDir,
                    ""
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            XLog.e("下载解压debug.zip失败", e)
            false
        }
    }


    fun parseCheeseToml(workingDir: File): TomlParseResult? {
        return try {

            val tomlFile = File("$workingDir/cheese.toml")
            if (!tomlFile.exists()) {
                System.err.println("Error: cheese.toml file not found at $workingDir")
                return null
            }
            val result = Toml.parse(tomlFile.inputStream().bufferedReader().use { it.readText() })
            result.errors().takeIf { it.isNotEmpty() }?.forEach { error ->
                System.err.println("TOML Parsing Error: ${error.message} at ${error.position()}")
            }
            result
        } catch (e: Exception) {
            System.err.println("Failed to parse cheese.toml: ${e.message}")
            e.printStackTrace()
            null
        }
    }


}