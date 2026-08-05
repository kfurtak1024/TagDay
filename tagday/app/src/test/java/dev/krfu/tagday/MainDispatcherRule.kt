package dev.krfu.tagday

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher, which `viewModelScope` needs to exist at
 * all off-device. Defaults to [UnconfinedTestDispatcher] so a `viewModelScope.launch` runs
 * eagerly to its first real suspension point — the ViewModels here launch work that only
 * touches in-memory fakes, so tests can assert straight after calling a method instead of
 * scheduling `advanceUntilIdle()` around every one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Keeps [flow] subscribed for the rest of the test. Every `uiState` here is built with
 * `SharingStarted.WhileSubscribed`, so with no collector the upstream never runs and
 * `.value` stays stuck on the initial state — assertions would all read the default.
 *
 * Explicitly on an [UnconfinedTestDispatcher] (sharing this test's scheduler): with
 * `backgroundScope`'s inherited `StandardTestDispatcher` the collector would only be
 * *queued*, so nothing would run until the test suspended, and every assertion made
 * straight after calling a ViewModel method would still see the initial state.
 * `backgroundScope` is cancelled by `runTest` once the test body finishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepSubscribed(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

/**
 * Like [keepSubscribed], but records every emission into [into] rather than discarding them.
 * For assertions about the emissions a flow makes *along the way* — a `StateFlow`'s `.value`
 * only ever shows where it settled, which is exactly what hides an inconsistent intermediate
 * state (see `CalendarViewModelTest.everyEmission_pairsTheDataWithItsOwnQuery`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.collectInto(flow: Flow<T>, into: MutableList<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { into += it } }
}
