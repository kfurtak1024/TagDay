package dev.krfu.tagday.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.krfu.tagday.data.repository.TagInstanceRepository
import dev.krfu.tagday.data.repository.TagInstanceRepositoryImpl
import dev.krfu.tagday.data.repository.TagRepository
import dev.krfu.tagday.data.repository.TagRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindTagInstanceRepository(impl: TagInstanceRepositoryImpl): TagInstanceRepository
}
