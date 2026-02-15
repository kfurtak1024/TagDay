package dev.krfu.tagged.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GlobalTagEntity::class, TagEntryEntity::class, AppSettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaggedDatabase : RoomDatabase() {
    abstract fun taggedDao(): TaggedDao

    companion object {
        @Volatile
        private var instance: TaggedDatabase? = null

        fun getInstance(context: Context): TaggedDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaggedDatabase::class.java,
                    "tagged.db"
                ).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
