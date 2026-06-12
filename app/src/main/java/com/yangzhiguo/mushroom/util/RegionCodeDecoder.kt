package com.yangzhiguo.mushroom.util

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * GB/T 2260 行政区划码 → 中文名解析器。
 *
 * 数据源:`app/src/main/assets/regions/gbt_2260.json`,精简版(覆盖本数据库
 * 出现过的省/市/县)。该工具是 **lazy + 软加载**:第一次解析时载入资源、
 * 之后纯内存查表;解析不到的编码返回 null(不抛异常,调用方决定写 WARN)。
 */
object RegionCodeDecoder {

    private const val TAG = "RegionCodeDecoder"
    private const val ASSET_PATH = "regions/gbt_2260.json"

    @Volatile private var provinceCache: Map<String, String>? = null
    @Volatile private var districtCache: Map<String, String>? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun ensureLoaded(context: Context) {
        if (provinceCache != null) return
        synchronized(this) {
            if (provinceCache != null) return
            try {
                val text = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = json.parseToJsonElement(text).jsonObject
                val out = HashMap<String, String>(64)
                val districts = HashMap<String, String>(64)
                for ((key, value) in root) {
                    if (key == "districts" || key.startsWith("_")) continue
                    val v = (value as? JsonPrimitive)?.content ?: continue
                    out[key] = v
                }
                val distObj = root["districts"] as? JsonObject
                distObj?.let { obj ->
                    for ((k, v) in obj) {
                        val s = (v as? JsonPrimitive)?.content ?: continue
                        districts[k] = s
                    }
                }
                provinceCache = out
                districtCache = districts
                Log.i(TAG, "Loaded ${out.size} province/city codes and ${districts.size} district codes")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to load $ASSET_PATH; region names will be null", t)
                provinceCache = emptyMap()
                districtCache = emptyMap()
            }
        }
    }

    /**
     * 把 6 位编码解析成中文。优先按 district(6 位全码)查,再回退到
     * 前 4 位 city,再前 2 位 province。**全部命中失败返回 null**。
     */
    fun decode(context: Context, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val c = code.trim()
        if (c.length != 6 || !c.all { it.isDigit() }) return null
        ensureLoaded(context)
        districtCache?.get(c)?.let { return it }
        provinceCache?.get(c)?.let { return it }
        val city4 = c.substring(0, 4) + "00"
        provinceCache?.get(city4)?.let { return it }
        val prov2 = c.substring(0, 2) + "0000"
        provinceCache?.get(prov2)?.let { return it }
        return null
    }

    /** 仅返回省级中文(只查前 2 位)。 */
    fun decodeProvince(context: Context, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val c = code.trim()
        if (c.length != 6 || !c.all { it.isDigit() }) return null
        ensureLoaded(context)
        return provinceCache?.get(c.substring(0, 2) + "0000")
    }

    /** 仅返回地级中文(查前 4 位)。 */
    fun decodeCity(context: Context, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val c = code.trim()
        if (c.length != 6 || !c.all { it.isDigit() }) return null
        ensureLoaded(context)
        return provinceCache?.get(c.substring(0, 4) + "00")
            ?: provinceCache?.get(c.substring(0, 2) + "0000")
    }
}
