package com.example.tennisscorer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MatchRecord::class, PointEvent::class], version = 1, exportSchema = false)
abstract class TennisScorerDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao

    companion object {
        @Volatile private var INSTANCE: TennisScorerDatabase? = null

        fun getInstance(context: Context): TennisScorerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TennisScorerDatabase::class.java,
                    "tennis_scorer.db"
                ).build().also { INSTANCE = it }
            }
    }
}
