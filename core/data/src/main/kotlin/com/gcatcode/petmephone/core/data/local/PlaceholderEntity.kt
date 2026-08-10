package com.gcatcode.petmephone.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Scaffold entity proving the Room database is wired end-to-end through Hilt. Real entities land
 * with the feature slices that need on-device persistence; this slice's scope is the DI graph
 * itself, per the dependency-injection spec's scope boundary.
 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val note: String,
)
