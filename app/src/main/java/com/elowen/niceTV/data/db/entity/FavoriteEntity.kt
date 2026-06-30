package com.elowen.niceTV.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val link: String,
    val title: String,
    val imageUrl: String,
    val maker: String? = null,
    val tags: String? = null,
    val cast: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
