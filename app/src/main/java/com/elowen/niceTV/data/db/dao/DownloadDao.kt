package com.elowen.niceTV.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elowen.niceTV.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY addedTimestamp DESC")
    suspend fun getAllDownloadsOnce(): List<DownloadEntity>



    @Query("SELECT * FROM downloads WHERE postUrl = :postUrl")
    suspend fun getDownload(postUrl: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE postUrl = :postUrl")
    fun observeDownload(postUrl: String): Flow<DownloadEntity?>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE postUrl = :postUrl")
    suspend fun deleteByUrl(postUrl: String)

    @Query("UPDATE downloads SET mergedPath = :mergedPath, mergeState = :mergeState WHERE postUrl = :postUrl")
    suspend fun updateMergeState(postUrl: String, mergedPath: String?, mergeState: Int)


    @Query("SELECT postUrl FROM downloads")
    suspend fun getPostUrls(): List<String>

    @Query("SELECT url FROM downloads")
    suspend fun getVideoUrls(): List<String>

    @Query("SELECT * FROM downloads WHERE url = :videoUrl")
    suspend fun getDownloadByVideoUrl(videoUrl: String): DownloadEntity?

    @Query("SELECT mergedPath FROM downloads WHERE mergedPath IS NOT NULL")
    suspend fun getAllMergedPaths(): List<String>
}
