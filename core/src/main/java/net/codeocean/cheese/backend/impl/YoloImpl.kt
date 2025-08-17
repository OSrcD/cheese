package net.codeocean.cheese.backend.impl

import android.graphics.Bitmap
import com.tencent.yolov8ncnn.Yolov8Ncnn
import net.codeocean.cheese.core.api.Yolo
import net.codeocean.cheese.core.utils.ConvertersUtils
import net.codeocean.cheese.yolo.YoloHandler


object YoloImpl:Yolo {
    override fun detect(
        bitmap: Bitmap,
        path: String,
        list:  ArrayList<Any>,
        cpugpu: Int
    ): Array<Yolov8Ncnn.Obj?> {
        return YoloHandler.detect(bitmap,path,(ConvertersUtils.arrayToArrayList(list).filterIsInstance<String>().toTypedArray()),cpugpu)
    }

    override fun detect(
        bitmap: Bitmap,
        path: String,
        list:  Any,
        cpugpu: Int
    ): Array<Yolov8Ncnn.Obj?> {
        // 1. 强制转换为 NativeArray
        val nativeArray = list as org.mozilla.javascript.NativeArray

        // 2. 遍历并提取 String 元素
        val stringList: ArrayList<String> = ArrayList<String>().apply {
            for (i in 0 until nativeArray.length.toInt()) {
                val item = nativeArray.get(i, null)
                if (item is String) {
                    add(item)
                }
            }
        }

        return YoloHandler.detect(bitmap,path,(ConvertersUtils.arrayToArrayList(stringList).filterIsInstance<String>().toTypedArray()),cpugpu)
    }

    override fun getSpeed(): Double {
        return  YoloHandler.getSpeed()
    }

    override fun draw(objects: Array<Yolov8Ncnn.Obj>?, b: Bitmap): Bitmap {
        return   YoloHandler.draw(objects,b)
    }
}