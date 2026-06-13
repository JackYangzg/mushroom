package com.yangzhiguo.mushroom.data.local

import androidx.room.TypeConverter
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room type converters. Enums stored as their `.name` (string).
 *
 * `List<String>` 序列化为 JSON 数组字符串:
 *  - Room 不直接支持 [List],需要 `@TypeConverter` 转成单列(此处存为 JSON 字符串)
 *  - 在 DAO 的 SELECT 阶段,我们直接用 `LIKE '%foo%' COLLATE NOCASE` 在该 JSON 字符串里
 *    搜索子串即可(对单行内的 list 元素查找足够;不需要 FTS5 倒排)
 */
class Converters {

    private val listJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @TypeConverter
    fun useTypeToString(value: UseType?): String? = value?.name

    @TypeConverter
    fun stringToUseType(value: String?): UseType? = value?.let { UseType.valueOf(it) }

    @TypeConverter
    fun edibilityToString(value: Edibility?): String? = value?.name

    @TypeConverter
    fun stringToEdibility(value: String?): Edibility? = value?.let { Edibility.valueOf(it) }

    @TypeConverter
    fun toxicityToInt(value: ToxicityLevel?): Int? = value?.level

    @TypeConverter
    fun intToToxicity(value: Int?): ToxicityLevel? = value?.let { ToxicityLevel.fromInt(it) }

    @TypeConverter
    fun stringListToString(value: List<String>?): String? =
        if (value == null) null
        else listJson.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun stringToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching {
            listJson.decodeFromString(ListSerializer(String.serializer()), value)
        }.getOrDefault(emptyList())
}
