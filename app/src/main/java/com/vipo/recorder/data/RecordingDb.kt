package com.vipo.recorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [SessionEntity::class, SegmentEntity::class],
  version = 1,
  exportSchema = false
)
abstract class RecordingDb : RoomDatabase() {
  abstract fun dao(): RecordingDao

  companion object {
    @Volatile private var INSTANCE: RecordingDb? = null

    fun get(ctx: Context): RecordingDb {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          ctx.applicationContext,
          RecordingDb::class.java,
          "recordings.db"
        ).build().also { INSTANCE = it }
      }
    }
  }
}
