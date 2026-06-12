package com.yangzhiguo.mushroom.data.local

import androidx.room.TypeConverter
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType

/**
 * Room type converters. Enums stored as their `.name` (string).
 */
class Converters {
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
}
