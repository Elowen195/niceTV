package com.elowen.niceTV.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elowen.niceTV.data.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE link = :link)")
    fun isFavorite(link: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE link = :link)")
    suspend fun isFavoriteSync(link: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE link = :link")
    suspend fun delete(link: String)

    @Query("DELETE FROM favorites WHERE link IN (:links)")
    suspend fun deleteAll(links: List<String>)
}
