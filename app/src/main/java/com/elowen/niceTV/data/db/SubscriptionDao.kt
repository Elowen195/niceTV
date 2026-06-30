package com.elowen.niceTV.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdTime DESC")
    fun getAllFlow(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY createdTime DESC")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE autoUpdate = 1")
    suspend fun getAutoUpdateSubscriptions(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE subscriptions SET nodeCount = :count, lastUpdateTime = :time WHERE id = :id")
    suspend fun updateNodeCount(id: Long, count: Int, time: Long)
}
