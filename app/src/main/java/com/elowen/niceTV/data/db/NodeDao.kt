package com.elowen.niceTV.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY rowid ASC")
    fun getAllFlow(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes ORDER BY rowid ASC")
    suspend fun getAll(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getById(id: String): NodeEntity?

    @Query("SELECT * FROM nodes WHERE subscriptionId IS NULL ORDER BY rowid ASC")
    suspend fun getManualNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE subscriptionId = :subscriptionId ORDER BY rowid ASC")
    suspend fun getNodesBySubscription(subscriptionId: Long): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveNode(): NodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Update
    suspend fun update(node: NodeEntity)

    @Delete
    suspend fun delete(node: NodeEntity)

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM nodes WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: Long)

    @Query("UPDATE nodes SET isActive = 0")
    suspend fun clearActiveStatus()

    @Query("UPDATE nodes SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE nodes SET latency = :latency, lastTestTime = :time WHERE id = :id")
    suspend fun updateSpeedTest(id: String, latency: Long, time: Long)
}
