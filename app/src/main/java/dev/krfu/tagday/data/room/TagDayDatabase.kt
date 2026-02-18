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
abstract class TagDayDatabase : RoomDatabase() {
    abstract fun tagDayDao(): TagDayDao

    companion object {
        @Volatile
        private var instance: TagDayDatabase? = null

        fun getInstance(context: Context): TagDayDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TagDayDatabase::class.java,
                    "tagday.db"
                ).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
