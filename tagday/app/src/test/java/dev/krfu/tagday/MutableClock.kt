package dev.krfu.tagday

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A [Clock] whose time a test can move. Pairs with `runTest`'s virtual time: advancing the
 * scheduler resumes whatever was waiting on a `delay`, and moving [instant] decides what it
 * sees when it wakes. Both are needed to cross midnight in a test — the scheduler alone would
 * resume the coroutine while the real date stayed put.
 */
class MutableClock(
    var instant: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant
}
