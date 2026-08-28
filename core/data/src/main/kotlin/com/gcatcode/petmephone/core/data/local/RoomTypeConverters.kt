package com.gcatcode.petmephone.core.data.local

import androidx.room.TypeConverter
import com.gcatcode.petmephone.core.domain.task.CompletionKind
import java.time.Instant
import java.time.LocalDate

/**
 * `domain-time`/`task-persistence` spec: `LocalDate` persists as an ISO-8601 string, so it
 * round-trips with no zone-dependent drift — the date was already computed once in `:core:domain`
 * before it ever reaches SQL. `Instant` persists as epoch milliseconds.
 *
 * `CompletionKind` persists as its enum NAME rather than its ordinal: a name survives someone
 * reordering or inserting a constant, an ordinal silently re-labels every stored row. An unknown
 * name reads back as null — "we do not know how this was completed" — never as a guessed
 * [CompletionKind.FULL], which would invent a record of something nobody did.
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

    @TypeConverter
    fun fromCompletionKind(value: CompletionKind?): String? = value?.name

    @TypeConverter
    fun toCompletionKind(value: String?): CompletionKind? =
        value?.let { name -> CompletionKind.entries.firstOrNull { it.name == name } }
}
