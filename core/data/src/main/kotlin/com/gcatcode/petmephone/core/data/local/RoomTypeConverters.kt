package com.gcatcode.petmephone.core.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * `domain-time`/`task-persistence` spec: `LocalDate` persists as an ISO-8601 string, so it
 * round-trips with no zone-dependent drift — the date was already computed once in `:core:domain`
 * before it ever reaches SQL. `Instant` persists as epoch milliseconds.
 */
class RoomTypeConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
