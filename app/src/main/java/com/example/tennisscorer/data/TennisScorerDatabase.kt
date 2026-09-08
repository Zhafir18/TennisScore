package com.example.tennisscorer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MatchRecord::class, PointEvent::class, BounceRecord::class],
    version = 2,
    exportSchema = false
)
abstract class TennisScorerDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
    abstract fun bounceDao(): BounceDao

    companion object {
        @Volatile private var INSTANCE: TennisScorerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS bounce_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        matchId INTEGER NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        player INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getInstance(context: Context): TennisScorerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TennisScorerDatabase::class.java,
                    "tennis_scorer.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
