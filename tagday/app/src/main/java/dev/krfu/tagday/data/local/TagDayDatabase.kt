package dev.krfu.tagday.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance

@Database(
    entities = [Tag::class, TagInstance::class],
    version = 3,
    exportSchema = false,
)
abstract class TagDayDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun tagInstanceDao(): TagInstanceDao
}
