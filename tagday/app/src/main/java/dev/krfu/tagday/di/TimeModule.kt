package dev.krfu.tagday.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The device clock, injected rather than reached for statically so "what is today?" can be
 * driven in a test. `CalendarViewModel` re-reads it at every midnight to keep the today
 * highlights current (BACKLOG F6); without an injectable clock that rollover is only
 * observable by waiting until midnight, which is no kind of test.
 *
 * Deliberately *not* a fixed zone: `systemDefaultZone` follows the device, so changing
 * timezone changes what the app calls today, which is what a diary should do.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
