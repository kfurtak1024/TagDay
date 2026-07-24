package dev.krfu.tagday.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.krfu.tagday.data.local.TagDao
import dev.krfu.tagday.data.local.TagDayDatabase
import dev.krfu.tagday.data.local.TagInstanceDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideTagDayDatabase(@ApplicationContext context: Context): TagDayDatabase =
        Room.databaseBuilder(context, TagDayDatabase::class.java, "tagday.db").build()

    @Provides
    fun provideTagDao(database: TagDayDatabase): TagDao = database.tagDao()

    @Provides
    fun provideTagInstanceDao(database: TagDayDatabase): TagInstanceDao = database.tagInstanceDao()
}
