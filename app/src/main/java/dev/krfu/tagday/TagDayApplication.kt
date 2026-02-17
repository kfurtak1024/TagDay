package dev.krfu.tagday

import android.app.Application
import dev.krfu.tagday.data.TagDayRepository
import dev.krfu.tagday.data.room.RoomTagDayRepository
import dev.krfu.tagday.data.room.TagDayDatabase

class TagDayApplication : Application() {
    val repository: TagDayRepository by lazy {
        RoomTagDayRepository(TagDayDatabase.getInstance(this))
    }
}
