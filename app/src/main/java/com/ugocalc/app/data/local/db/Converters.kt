package com.ugocalc.app.data.local.db

import androidx.room.TypeConverter
import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.domain.model.OfficialFileType

class Converters {

    @TypeConverter
    fun fromOfficialFileType(value: OfficialFileType): String = value.name

    @TypeConverter
    fun toOfficialFileType(value: String): OfficialFileType = OfficialFileType.valueOf(value)

    @TypeConverter
    fun fromNormalizationStatus(value: NormalizationStatus): String = value.name

    @TypeConverter
    fun toNormalizationStatus(value: String): NormalizationStatus = NormalizationStatus.valueOf(value)
}
