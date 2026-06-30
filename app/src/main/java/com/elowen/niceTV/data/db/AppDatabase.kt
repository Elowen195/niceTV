package com.elowen.niceTV.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elowen.niceTV.data.db.dao.FavoriteDao
import com.elowen.niceTV.data.db.entity.FavoriteEntity
import com.elowen.niceTV.data.db.dao.DownloadDao
import com.elowen.niceTV.data.db.entity.DownloadEntity

@Database(
    entities = [FavoriteEntity::class, DownloadEntity::class, NodeEntity::class, SubscriptionEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun nodeDao(): NodeDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun legacyTo8Migration(startVersion: Int) = object : Migration(startVersion, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureBaseTables(db)
                ensureDownloadColumns(db)
                ensureFavoriteColumns(db)
                ensureProxyTables(db)
            }
        }

        private val LEGACY_TO_8_MIGRATIONS: Array<Migration> = arrayOf(
            legacyTo8Migration(1),
            legacyTo8Migration(2),
            legacyTo8Migration(3),
            legacyTo8Migration(4),
            legacyTo8Migration(5)
        )

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureColumn(db, "downloads", "mergedPath", "`mergedPath` TEXT")
                ensureColumn(db, "downloads", "mergeState", "`mergeState` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureProxyTables(db)
            }
        }

        private val ALL_MIGRATIONS: Array<Migration> =
            LEGACY_TO_8_MIGRATIONS + arrayOf(MIGRATION_6_7, MIGRATION_7_8)

        private fun ensureBaseTables(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `favorites` (
                    `link` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `imageUrl` TEXT NOT NULL,
                    `maker` TEXT,
                    `tags` TEXT,
                    `cast` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`link`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `downloads` (
                    `postUrl` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT NOT NULL,
                    `maker` TEXT,
                    `tags` TEXT,
                    `cast` TEXT,
                    `mergedPath` TEXT,
                    `mergeState` INTEGER NOT NULL DEFAULT 0,
                    `addedTimestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`postUrl`)
                )
            """.trimIndent())
        }

        private fun ensureFavoriteColumns(db: SupportSQLiteDatabase) {
            ensureColumn(db, "favorites", "title", "`title` TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "favorites", "imageUrl", "`imageUrl` TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "favorites", "maker", "`maker` TEXT")
            ensureColumn(db, "favorites", "tags", "`tags` TEXT")
            ensureColumn(db, "favorites", "cast", "`cast` TEXT")
            ensureColumn(db, "favorites", "createdAt", "`createdAt` INTEGER NOT NULL DEFAULT 0")
        }

        private fun ensureDownloadColumns(db: SupportSQLiteDatabase) {
            ensureColumn(db, "downloads", "url", "`url` TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "downloads", "title", "`title` TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "downloads", "coverUrl", "`coverUrl` TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "downloads", "maker", "`maker` TEXT")
            ensureColumn(db, "downloads", "tags", "`tags` TEXT")
            ensureColumn(db, "downloads", "cast", "`cast` TEXT")
            ensureColumn(db, "downloads", "mergedPath", "`mergedPath` TEXT")
            ensureColumn(db, "downloads", "mergeState", "`mergeState` INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "downloads", "addedTimestamp", "`addedTimestamp` INTEGER NOT NULL DEFAULT 0")
        }

        private fun ensureProxyTables(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `nodes` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `protocol` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `port` INTEGER NOT NULL,
                    `uuid` TEXT NOT NULL,
                    `password` TEXT,
                    `obfsPassword` TEXT,
                    `transport` TEXT NOT NULL,
                    `transportSettings` TEXT,
                    `security` TEXT NOT NULL,
                    `securitySettings` TEXT,
                    `flow` TEXT,
                    `subscriptionId` INTEGER,
                    `latency` INTEGER NOT NULL DEFAULT -1,
                    `lastTestTime` INTEGER NOT NULL DEFAULT 0,
                    `isAvailable` INTEGER NOT NULL DEFAULT 1,
                    `isActive` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_subscriptionId` ON `nodes` (`subscriptionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_isActive` ON `nodes` (`isActive`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_latency` ON `nodes` (`latency`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `subscriptions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `useProxyForUpdate` INTEGER NOT NULL,
                    `autoUpdate` INTEGER NOT NULL,
                    `updateInterval` INTEGER NOT NULL,
                    `lastUpdateTime` INTEGER NOT NULL,
                    `speedTestUrl` TEXT NOT NULL,
                    `speedTestTimeout` INTEGER NOT NULL,
                    `nodeCount` INTEGER NOT NULL,
                    `createdTime` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_lastUpdateTime` ON `subscriptions` (`lastUpdateTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_autoUpdate` ON `subscriptions` (`autoUpdate`)")
        }

        private fun ensureColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            val exists = db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN $columnDefinition")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nice_tv_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
