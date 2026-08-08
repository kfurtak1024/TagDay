package dev.krfu.tagday.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance

// Schemas are exported to `app/schemas` (wired by the Room Gradle plugin) and committed.
// The export is a record of what each `version` looked like, not a promise that the schema
// is settled — it's what makes writing a real `Migration` possible later, since the old
// schema can't be recovered once it's gone. See `docs/DATA_MODEL.md` § Schema history.
//
// Reset to 1 on 2026-08-08, deliberately: the pre-release bumps to 2 and 3 recorded a history
// no installed copy of the app was ever on, since nothing has shipped and every existing
// install is a development one that gets wiped by `fallbackToDestructiveMigration` anyway.
// Version 1 is now the shape this app will *launch* with, and the number to write the first
// real `Migration` from. Bumping past it means the schema changed after that point — so from
// here on treat a bump as a decision, not routine.
@Database(
    entities = [Tag::class, TagInstance::class],
    version = 1,
    exportSchema = true,
)
abstract class TagDayDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun tagInstanceDao(): TagInstanceDao
}
