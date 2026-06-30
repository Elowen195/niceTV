package com.elowen.niceTV.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val postUrl: String, // Stable ID: The page link from which the video was scraped
    val url: String,     // Volatile ID: The actual video file link
    val title: String,
    val coverUrl: String,
    val maker: String? = null,
    val tags: String? = null,
    val cast: String? = null,
    val mergedPath: String? = null,
    val mergeState: Int = MERGE_NONE,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val MERGE_NONE = 0
        const val MERGE_IN_PROGRESS = 1
        const val MERGE_COMPLETED = 2
        const val MERGE_FAILED = 3
    }
}
