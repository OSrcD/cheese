package net.codeocean.cheese.backend.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import dalvik.system.DexClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.codeocean.cheese.core.BaseEnv
import net.codeocean.cheese.core.CoreFactory
import net.codeocean.cheese.core.Misc.extractPackageName
import net.codeocean.cheese.core.api.Plugins
import java.io.File
import java.io.FileOutputStream
import com.elvishew.xlog.XLog

class PluginsImpl() : Plugins, BaseEnv {
    private val mCacheDir = File("")
    private  lateinit var resources: Resources
    var pkg:String =""
//    override fun install(path: String): Boolean = runBlocking {
//        pkg = extractPackageName(path).toString()
//        var pluginsPath:File
////        if (!getCachePath(pkg).exists()) {
////            makeAppPath(pkg, path)
////            extractLibsFromApk(path, getAppLibPath(pkg).toString())
////            pluginsPath =File(getCachePath(pkg),"base.apk")
////            File (path).copyTo(pluginsPath)
////            pluginsPath=getCachePath(pkg)
////        }else{
////            pluginsPath =File(path)
////        }
//
//        pluginsPath =File(path)
//
//
//        val deferredRes = async { loadRes(pluginsPath.absolutePath) }
//        val deferredDex = async {
//            loadDex(
//                pluginsPath.absolutePath,
//                File(File(getAppLibPath(pkg).toString()).absolutePath + File.separator + Build.CPU_ABI).absolutePath
//            ).also {
//                mDexClassLoaders[pkg] = it
//            }
//        }
//        // 等待异步任务完成
//        deferredRes.await()
//        deferredDex.await()
//        return@runBlocking getCachePath(pkg).exists()
//    }

    // 在 PluginsImpl 类中


// ... 在 PluginsImpl 类内部 ...

    override fun install(path: String): Boolean = runBlocking {
        XLog.i( "========== 开始加载插件流程 ==========")
        XLog.i( "1. 原始路径 (Source): $path")

        val sourceFile = File(path)

        // [检查 1] 源文件是否存在
        if (!sourceFile.exists()) {
            XLog.e("❌ 错误: 源文件不存在于路径: $path")
            return@runBlocking false
        }

        // 定义内部存储路径 (使用 context.codeCacheDir 是最安全的做法)
        // 这里的 cx 是你的 BaseEnv 中的 context
//        val internalDir = File(cx.codeCacheDir, "plugin_libs")
        val internalDir = File("/storage/emulated/0/Android/data/com.twigent/files/", "plugin_libs")
//        val internalDir = File("/storage/emulated/0/Download/", "plugin_libs")
        if (!internalDir.exists()) {
            val mkdirResult = internalDir.mkdirs()
            XLog.d("2. 创建内部目录: ${internalDir.absolutePath}, 结果: $mkdirResult")
        }

        // 目标文件路径
        val targetFile = File(internalDir, sourceFile.name)
        XLog.i("3. 目标内部路径 (Target): ${targetFile.absolutePath}")

        try {
            // [关键步骤 2] 复制文件
            if (!targetFile.exists()) {
                XLog.e(" 目标文件已存在，正在删除旧文件...")
//                targetFile.delete()
                // [关键步骤 2] 使用输入/输出流进行可靠的文件复制
                XLog.d("   正在将 APK 从外部存储复制到内部存储 (使用流)...")
                sourceFile.copyTo(targetFile, overwrite = true)
                val targetSize = targetFile.length()
                XLog.i( "复制完成。目标文件大小: $targetSize 字节")
                // 验证复制是否成功
                if (targetFile.length() == 0L || !targetFile.exists()) {
                    XLog.e("❌ 文件复制失败! 目标文件为空或不存在。")
                    return@runBlocking false
                }
                XLog.i("复制完成。目标文件大小: ${targetFile.length()} 字节")

                // [关键步骤 3] 设置只读 (Android 14 核心修复)
                val setReadable = targetFile.setReadable(true, true)
                val setWritableFalse = targetFile.setWritable(false) // 禁止写入
                val setReadOnly = targetFile.setReadOnly() // 再次确保只读

                XLog.d("4. 权限设置结果 -> setWritable(false): $setWritableFalse, setReadOnly(): $setReadOnly")

                // [检查 4] 验证文件是否真的不可写 (如果这里是 true，DexClassLoader 依然会崩溃)
                if (targetFile.canWrite()) {
                    XLog.e(" 严重警告: 目标文件依然是 '可写' 的！Android 14 可能会报错。")
                } else {
                    XLog.i(" 验证通过: 目标文件已设为 '只读'。")
                }
            }
        } catch (e: Exception) {
            XLog.e("文件操作发生异常", e)
            return@runBlocking false
        }

        // [关键步骤 5] 解析包名 (注意：必须解析 targetFile，虽然包名一样，但为了逻辑严谨)
        pkg = extractPackageName(targetFile.absolutePath).toString()
        XLog.i("5. 解析包名成功: $pkg")

        // 使用内部存储的文件对象
        val pluginsPath = targetFile

        // [关键步骤 6] 加载资源和 Dex
        try {
            XLog.d("   开始异步加载资源和 Dex...")
            val deferredRes = async {
                XLog.d("   -> LoadRes 开始")
                loadRes(pluginsPath.absolutePath)
                XLog.d("   -> LoadRes 完成")
            }

            val deferredDex = async {
                val libPath = File(File(getAppLibPath(pkg).toString()).absolutePath + File.separator + Build.CPU_ABI).absolutePath
                XLog.d("   -> LoadDex 开始, LibPath: $libPath")

                // 注意：这里 loadDex 传入的是 pluginsPath.absolutePath (内部路径)
                loadDex(
                    pluginsPath.absolutePath,
                    libPath
                ).also {
                    mDexClassLoaders[pkg] = it
                    XLog.d("   -> LoadDex 完成, ClassLoader 已缓存")
                }
            }

            deferredRes.await()
            deferredDex.await()

            val isSuccess = getCachePath(pkg).exists()
            XLog.i("========== 流程结束: 结果 = $isSuccess ==========")
            return@runBlocking isSuccess

        } catch (e: Exception) {
            XLog.e("加载 Dex/Res 期间发生崩溃", e)
            // 打印具体堆栈，看是否还是 SecurityException
            e.printStackTrace()
            return@runBlocking false
        }
    }

    override fun createContext(): Context = object : ContextWrapper(cx) {
        override fun getAssets(): AssetManager = resources.assets
    }

    override fun uninstall(): Boolean {
        val pkgPath = getCachePath(pkg)
        if (pkgPath.exists()) {
            mDexClassLoaders.remove(pkg)
            return pkgPath.deleteRecursively()
        }
        return true
    }

    override fun getClassLoader(): ClassLoader? = mDexClassLoaders[pkg]

    private fun loadDex(f: String, librarySearchPath: String): DexClassLoader {
        val file = File(f)
        require(file.exists()) { "File not found: ${file.path}" }
        return DexClassLoader(
            file.path,
            mCacheDir.path,
            librarySearchPath,
            cx.classLoader,
        )
    }

    @SuppressLint("PrivateApi")
    private fun loadRes(apkFilePath: String) {
        try {
            val assetManager = AssetManager::class.java.newInstance()
            AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).invoke(
                assetManager, apkFilePath
            )
            resources = Resources(
                assetManager,
                cx.resources.displayMetrics,
                cx.resources.configuration
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppLibPath(pkg: String): File {
        val appPath = CoreFactory.getPersistentStore().get("app", pkg)
        val pathString = when(appPath) {
            is String -> appPath
            is ByteArray -> String(appPath)
            else -> "${pkg}_unknown"
        }
        val path = File(
            APP_DIRECTORY,
            File(File(pathString), "lib").path
        ).path
        net.codeocean.cheese.core.utils.FilesUtils.create(path)
        return File(path)
    }

    private fun makeAppPath(pkg: String, path: String): File {
        val sha256 = CoreFactory.getAPP().getApkSha256(path)
        val appPath = CoreFactory.getPersistentStore().get("app", pkg)
        if (appPath == null) {
            println("创建App缓存路径")
            val pathString = "${pkg}_${sha256}"
            CoreFactory.getPersistentStore().save("app", pkg, pathString)
            return File(APP_DIRECTORY, pathString)
        }
        // 处理ByteArray和String两种可能的类型
        val pathString = when(appPath) {
            is String -> appPath
            is ByteArray -> String(appPath)
            else -> "${pkg}_${sha256}" // 默认情况
        }
        return File(APP_DIRECTORY, pathString)
    }

    private fun extractLibsFromApk(apkFilePath: String, destinationFolderPath: String) {
        val apkFile = java.util.zip.ZipFile(apkFilePath)
        val entries = apkFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith("lib/")) {
                continue
            }
            val entryName = entry.name
            val libName = entryName.substring(entryName.indexOf('/') + 1)
            val libFile = File(destinationFolderPath + File.separator + libName)
            if (libFile.getParentFile() != null && !libFile.getParentFile()!!.exists()) {
                libFile.getParentFile()?.mkdirs()
            }
            val inputStream = apkFile.getInputStream(entry)
            val outputStream = FileOutputStream(libFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            inputStream.close()
            outputStream.close()
        }
        apkFile.close()
    }
    companion object:BaseEnv {
        private val DATA_DIRECTORY: File = File(cx.cacheDir.parentFile, "data")
        private val APP_DIRECTORY: File = File(DATA_DIRECTORY, "app")
        val mDexClassLoaders: MutableMap<String, DexClassLoader> = HashMap()
        fun getCachePath(pkg: String): File {
            val appPath = CoreFactory.getPersistentStore().get("app", pkg)
            val pathString = when(appPath) {
                is String -> appPath
                is ByteArray -> String(appPath)
                else -> null
            } ?: return File(APP_DIRECTORY, "${pkg}_unknown")
            return File(APP_DIRECTORY, pathString)
        }
    }

}