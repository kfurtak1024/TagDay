package dev.krfu.tagday.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GlobalTagEntity::class, TagEntryEntity::class, AppSettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class tagdayDatabase : RoomDatabase() {
    abstract fun tagdayDao(): tagdayDao

    companion object {
        @Volatile
        private var instance: tagdayDatabase? = null

        fun getInstance(context: Context): tagdayDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    tagdayDatabase::class.java,
                    "tagday.db"
                ).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
