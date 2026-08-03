package dev.krfu.tagday.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance

// Schemas are exported to `app/schemas` (wired by the Room Gradle plugin) and committed.
// The export is a record of what each `version` looked like, not a promise that the schema
// is settled — it's what makes writing a real `Migration` possible later, since the old
// schema can't be recovered once it's gone. See `docs/DATA_MODEL.md` § Schema history.
@Database(
    entities = [Tag::class, TagInstance::class],
    version = 3,
    exportSchema = true,
)
abstract class TagDayDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun tagInstanceDao(): TagInstanceDao
}
