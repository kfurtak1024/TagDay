package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.components.VerticalScrollbar
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * How long typing has to pause before an edited value is written. Long enough that ordinary
 * typing produces one write rather than one per character, short enough that closing the sheet
 * straight after typing still saves — see BACKLOG F11.
 */
private const val VALUE_WRITE_DEBOUNCE_MS = 400L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListSheet(
    group: TagDisplayGroup,
    onDismiss: () -> Unit,
    onUpdateInstance: (TagInstance) -> Unit,
    onRemoveInstance: (TagInstance) -> Unit,
    onAddValue: (tagId: Long, value: String) -> Unit,
    onAddRating: (tagId: Long, rating: Int?) -> Unit,
    onIncrementCount: (tagId: Long) -> Unit,
    onDecrementCount: (TagInstance) -> Unit,
    onReorderInstances: (List<TagInstance>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drag and scrim dismissal stay blocked (ADR-021 point 6); **back is deliberately
    // allowed** (ADR-021 Amendment 1). A back press isn't the accidental dismissal that
    // decision was protecting against, and blocking it on Android 14+ means a predictive-back
    // peek followed by nothing, which reads as a broken screen.
    //
    // Each path is now blocked by the mechanism actually meant for it:
    //  - scrim: `shouldDismissOnClickOutside = false`, which gates the `Scrim`'s own
    //    clickability. This used to be done with a `confirmValueChange` veto on `Hidden` —
    //    the only tool available when ADR-021 was written, and one that no longer expresses
    //    the intent, since material3 gained this property.
    //  - drag: `sheetGesturesEnabled = false`, which disables the sheet's `.draggable()`
    //    outright; `confirmValueChange` alone only vetoed the final settle, leaving the panel
    //    visibly following the finger before snapping back.
    //  - back: left to the library, which routes it through `sheetState.hide()` and so
    //    animates out properly instead of vanishing.
    //
    // Dropping the veto is what makes back *designed* rather than incidental: it worked before
    // only because material3's back path calls `onDismissRequest` from an `invokeOnCompletion`
    // that, unlike the scrim's, carries no `isVisible` guard — an inconsistency that could be
    // tightened in any release. See BACKLOG F15.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Both panels size to their content and use half the screen only as a ceiling, so a
    // one-row group opens a short sheet and a long one stops growing and scrolls instead
    // (ADR-028, superseding ADR-021's fixed height and generalising ADR-025).
    // `LocalWindowInfo.containerSize`, not `LocalConfiguration.screenHeightDp`: the latter is
    // deprecated, rounds to whole dp, and applies insets differently depending on target SDK —
    // all of which matter when the value *is* a layout ceiling (BACKLOG F21).
    val panelHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.5f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnClickOutside = false),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = panelHeight)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = group.tagName, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.day_instances_sheet_close_content_description,
                        ),
                    )
                }
            }
            // weight(fill = false) is what makes the panel responsive: the add-row below is
            // measured first, then the list takes *at most* the space left under the ceiling
            // and wraps to its content if that's shorter. weight(fill = true) would stretch
            // it to fill, pinning the sheet open at the full ceiling every time.
            val listModifier = Modifier.weight(1f, fill = false)
            when (group.type) {
                TagType.VALUED -> {
                    ReorderableInstanceList(
                        instances = group.instances,
                        onReorder = onReorderInstances,
                        modifier = listModifier,
                    ) { instance, dragHandle ->
                        InstanceRow(
                            dragHandle = dragHandle,
                            onRemove = { onRemoveInstance(instance) },
                        ) {
                            ValueField(instance = instance, onUpdateInstance = onUpdateInstance)
                        }
                    }
                    AddValueRow(onAdd = { value -> onAddValue(group.tagId, value) })
                }

                TagType.RATED -> {
                    ReorderableInstanceList(
                        instances = group.instances,
                        onReorder = onReorderInstances,
                        modifier = listModifier,
                    ) { instance, dragHandle ->
                        InstanceRow(
                            dragHandle = dragHandle,
                            onRemove = { onRemoveInstance(instance) },
                        ) {
                            StarInput(
                                rating = instance.rating ?: 0,
                                onRatingSelected = { rating ->
                                    onUpdateInstance(instance.copy(rating = rating))
                                },
                            )
                        }
                    }
                    AddRatingRow(onAdd = { rating -> onAddRating(group.tagId, rating) })
                }

                // Simple has no per-instance state at all, so its instances are
                // interchangeable and the only thing worth editing is how many there are
                // (ADR-031, superseding ADR-018's "nothing to edit").
                TagType.SIMPLE -> SimpleCountEditor(
                    count = group.instances.size,
                    onIncrement = { onIncrementCount(group.tagId) },
                    // Newest first out, so repeated -/+ is a no-op rather than a reshuffle.
                    onDecrement = {
                        group.instances.maxByOrNull { it.sortOrder }?.let(onDecrementCount)
                    },
                )
            }
        }
    }
}

/**
 * A drag-reorderable instance list. Owns the ordering state, the drag gesture, the swap
 * thresholds and the edge auto-scroll; [row] supplies what each instance actually looks
 * like, and is handed the `dragHandle` to place wherever it wants (both current callers put
 * it in a `ListItem`'s leading slot). Reordering commits once per drop via [onReorder].
 *
 * Shared by Valued and Rated — see ADR-022 for how the gesture works and ADR-028 for why
 * Rated reuses it.
 */
@Composable
private fun ReorderableInstanceList(
    instances: List<TagInstance>,
    onReorder: (List<TagInstance>) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (instance: TagInstance, dragHandle: @Composable () -> Unit) -> Unit,
) {
    // `instances` already arrives in sortOrder — see TagInstanceRepository.observeDayGroups.
    val persistedIds = instances.map { it.id }
    val byId = instances.associateBy { it.id }
    val scrollState = rememberScrollState()

    // The rendered order is local state, not `instances` directly: a drag has to reorder
    // rows immediately, frame by frame, rather than wait for each swap to round-trip
    // through Room.
    var order by remember { mutableStateOf(persistedIds) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    // Where the dragged row sits relative to the slot it currently occupies, in px.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Net *finger* travel since this drag started — auto-scroll only, see below. Can't use
    // dragOffset for that: it's a residual that flips sign at every swap (crossing half a
    // row's height triggers the swap, then a whole row's height comes off the offset).
    var dragTravel by remember { mutableFloatStateOf(0f) }
    // Measured per row (rows aren't uniform-height by construction) — the swap thresholds
    // and the auto-scroll below both need real heights. A plain map, not a snapshot one:
    // nothing reads it during composition, only the gesture callbacks and the effect below.
    val rowHeights = remember { mutableMapOf<Long, Int>() }

    // Resync only when the *set* of instances changes (a value added or removed), never on a
    // pure order change: this UI is the only thing that ever writes `sortOrder`, so a
    // reordered emission is just our own commit coming back, and adopting it could revert a
    // newer local order that a second, quick reorder had already produced (the emission for
    // reorder #1 can land after reorder #2 is done, and `order` is the fresher of the two).
    LaunchedEffect(persistedIds) {
        if (persistedIds.toSet() != order.toSet()) order = persistedIds
    }
    // Anything persisted but not yet in `order` — a value just added through AddValueRow —
    // renders at the end straight away, instead of waiting a frame for the resync above.
    val rows = order.mapNotNull { byId[it] } + instances.filter { it.id !in order }

    fun commit(newOrder: List<Long>) {
        if (newOrder == persistedIds) return
        onReorder(
            newOrder.mapIndexedNotNull { index, id -> byId[id]?.copy(sortOrder = index.toLong()) },
        )
    }

    /** Discrete move, for the handle's accessibility actions — see [DragHandle]. */
    fun move(id: Long, by: Int) {
        val from = order.indexOf(id)
        val to = from + by
        if (from < 0 || to !in order.indices) return
        val moved = order.toMutableList().apply { add(to, removeAt(from)) }
        order = moved
        commit(moved)
    }

    // Applies as many neighbour swaps as the accumulated offset covers: once the dragged
    // row has travelled past half of the neighbour it's heading into, the two trade places
    // and that neighbour's height comes back off the offset — so the row stays under the
    // finger while the rows around it shuffle.
    fun applySwaps() {
        val id = draggedId ?: return
        while (true) {
            val from = order.indexOf(id)
            if (from < 0) return
            val to = when {
                dragOffset < 0f && from > 0 -> from - 1
                dragOffset > 0f && from < order.lastIndex -> from + 1
                else -> return
            }
            val neighbourHeight = rowHeights[order[to]] ?: return
            if (abs(dragOffset) <= neighbourHeight / 2f) return
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            dragOffset -= sign(dragOffset) * neighbourHeight
        }
    }

    fun dragBy(delta: Float) {
        dragOffset += delta
        applySwaps()
    }

    // Edge auto-scroll. The handle consumes every move event while a drag is in flight
    // (that's the point — see [DragHandle]), so the list can't scroll itself then, and
    // dragging a row to a position that's currently off-screen has to be driven from here.
    // Scrolling the content under a stationary finger has to advance the row by the same
    // amount for it to stay under that finger, hence feeding the scroll actually consumed
    // back through dragBy — which also lets swaps keep happening as the list moves past.
    //
    // Gated on `dragTravel` in both magnitude and direction, so it can only ever continue a
    // drag the user is really making: a press on the handle that never moves (drags start at
    // touch-down, so a plain tap is a zero-length drag) stays put, and a row dragged *down*
    // out of the top edge zone isn't yanked upward on its way out of it.
    val autoScrollZone = with(LocalDensity.current) { 24.dp.toPx() }
    val autoScrollStep = with(LocalDensity.current) { 8.dp.toPx() }
    val autoScrollArm = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(draggedId) {
        val id = draggedId ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val index = order.indexOf(id)
            val height = rowHeights[id]
            if (index < 0 || height == null) return@LaunchedEffect
            val viewportHeight = scrollState.viewportSize
            val slotTop = order.take(index).sumOf { rowHeights[it] ?: 0 }
            val top = slotTop + dragOffset - scrollState.value
            val bottom = top + height
            val step = when {
                abs(dragTravel) < autoScrollArm -> 0f
                dragTravel < 0f && top < autoScrollZone -> -autoScrollStep
                dragTravel > 0f && viewportHeight > 0 &&
                    bottom > viewportHeight - autoScrollZone -> autoScrollStep
                else -> 0f
            }
            if (step != 0f) dragBy(scrollState.scrollBy(step))
        }
    }

    ScrollableInstanceList(modifier = modifier, scrollState = scrollState) {
        // key() is load-bearing: without it Compose tracks these rows by position, so
        // the first in-drag swap would tear down and recreate the composable (and the
        // handle's live gesture with it) at the dragged row's position.
        rows.forEachIndexed { index, instance ->
            key(instance.id) {
                val isDragged = draggedId == instance.id
                Box(
                    modifier = Modifier
                        .onSizeChanged { rowHeights[instance.id] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset {
                            IntOffset(x = 0, y = if (isDragged) dragOffset.roundToInt() else 0)
                        }
                        .background(
                            if (isDragged) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                    row(instance) {
                        DragHandle(
                            // Safe to reset from here: the node launches this UNDISPATCHED
                            // and its event loop delivers DragStarted before it starts
                            // pumping deltas, so no real movement can predate it. Belt and
                            // braces against a drag that ended while this row was detached
                            // (`onDragStopped` is skipped entirely in that case, leaving
                            // both values behind).
                            onDragStarted = {
                                draggedId = instance.id
                                dragOffset = 0f
                                dragTravel = 0f
                            },
                            // dragTravel accumulates here rather than in dragBy, so the
                            // auto-scroll's own feed into dragBy can't sustain itself.
                            onDrag = { delta ->
                                dragTravel += delta
                                dragBy(delta)
                            },
                            onDragStopped = {
                                commit(order)
                                draggedId = null
                                dragOffset = 0f
                            },
                            onMoveUp = { move(instance.id, -1) }.takeIf { index > 0 },
                            onMoveDown = { move(instance.id, 1) }.takeIf { index < rows.lastIndex },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The scrollable region both instance lists live in: a `Column` rather than a `LazyColumn`
 * because these lists hold one tag's instances for a single day (a handful), and because
 * [ValuedInstanceList]'s drag needs every row measured, including off-screen ones.
 *
 * Wraps its content vertically, so a caller that wants a short region (Rated) just doesn't
 * pass `weight(1f)`; scrolling kicks in when the content outgrows whatever height the
 * caller's constraints allow.
 */
@Composable
private fun ScrollableInstanceList(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            content = content,
        )
        VerticalScrollbar(state = scrollState, modifier = Modifier.matchParentSize())
    }
}

/**
 * The shell every instance row shares: drag handle, the type's own editor, delete button.
 * `ListItem` for its slot padding/alignment, with a transparent container so the dragged
 * row's highlight (owned by [ReorderableInstanceList]) shows through.
 */
@Composable
private fun InstanceRow(
    dragHandle: @Composable () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    editor: @Composable () -> Unit,
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { dragHandle() },
        headlineContent = { editor() },
        trailingContent = { RemoveInstanceButton(onClick = onRemove) },
    )
}

/**
 * The whole Simple panel: a stepper over the number of instances on the day, which is all a
 * Simple tag has (presence, and how many times). "+" adds an instance, "−" deletes the newest
 * outright — no undo snackbar, since "+" restores an equivalent one exactly (see
 * `CalendarViewModel.removeInstanceImmediately`).
 *
 * Floored at 1: removing the tag from the day entirely is the capsule's "x", which keeps one
 * removal path rather than two that behave differently (the "x" is undoable, ADR-019).
 * See ADR-031.
 */
@Composable
private fun SimpleCountEditor(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        IconButton(onClick = onDecrement, enabled = count > 1) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_minus),
                contentDescription = stringResource(
                    R.string.day_instances_sheet_decrease_count_content_description,
                ),
            )
        }
        Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = onIncrement) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    R.string.day_instances_sheet_increase_count_content_description,
                ),
            )
        }
    }
}

/**
 * Adds a rating, sitting where the Valued panel's add-value field does: pick the stars, then
 * press "+". Submitting with nothing picked adds an *unrated* instance, which is a real state
 * for this type (ADR-008) and matches what quick-entry's Rated chip already creates — so
 * unlike [AddValueRow] there's nothing to guard against here. See ADR-028.
 */
@Composable
private fun AddRatingRow(onAdd: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var rating by rememberSaveable { mutableIntStateOf(0) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        StarInput(
            rating = rating,
            onRatingSelected = { rating = it },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                onAdd(rating.takeIf { it > 0 })
                rating = 0
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    R.string.day_instances_sheet_add_rating_content_description,
                ),
            )
        }
    }
}

@Composable
private fun RemoveInstanceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(
                R.string.day_instances_sheet_remove_content_description,
            ),
        )
    }
}

// Reorder affordance for one Valued row. `Modifier.draggable` rather than a hand-rolled
// `pointerInput`/`drag()` loop, which is what every earlier attempt at this used and what
// made them lose to the list's own `verticalScroll` (see ADR-021, then ADR-022): the low-level
// `drag()` helper treats losing a single move event to another consumer as permanent failure,
// whereas `draggable` is a `DragGestureNode` — the same class `verticalScroll` is built on —
// and so plays the arbitration by the same rules the scroll does, including recovering from a
// lost event instead of dying on it. Two mechanics from foundation 1.11.4's `Draggable.kt`
// make the handle win outright:
//   - Pointer events reach the innermost node first in the Main pass (`HitPathTracker`
//     recurses into children before invoking the node's own Main handler), so this node
//     acts before the ancestor scrollable ever sees the event.
//   - `startDragImmediately = true` skips touch-slop detection and consumes the *down*
//     event in the Initial pass, then consumes every subsequent move. The ancestor
//     scrollable therefore never gets an unconsumed move to scroll with, and the claim
//     happens at touch-down rather than depending on which node crosses slop first.
// Dragging anywhere other than this handle is untouched, so the list still scrolls normally.
// Touch-drag is inherently unavailable to screen readers, so the discrete move-up/move-down
// actions survive here as accessibility actions on the handle.
@Composable
private fun DragHandle(
    onDragStarted: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val dragState = rememberDraggableState(onDelta = onDrag)
    val reorderLabel = stringResource(R.string.day_instances_sheet_reorder_content_description)
    val moveUpLabel = stringResource(R.string.day_instances_sheet_move_up_content_description)
    val moveDownLabel = stringResource(R.string.day_instances_sheet_move_down_content_description)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                startDragImmediately = true,
                onDragStarted = { onDragStarted() },
                onDragStopped = { onDragStopped() },
            )
            .semantics {
                contentDescription = reorderLabel
                customActions = buildList {
                    onMoveUp?.let { move ->
                        add(CustomAccessibilityAction(moveUpLabel) { move(); true })
                    }
                    onMoveDown?.let { move ->
                        add(CustomAccessibilityAction(moveDownLabel) { move(); true })
                    }
                }
            },
    ) {
        // material-icons-core has no DragHandle glyph (it's an extended-set icon, and
        // ADR-010/ADR-011's no-new-dependencies line applies) — Menu is the same stack of
        // horizontal bars, so it reads as a handle here.
        Icon(imageVector = Icons.Filled.Menu, contentDescription = null)
    }
}

@Composable
private fun AddValueRow(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        onAdd(trimmed)
        value = ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.day_instances_sheet_value_placeholder)) },
            // The keyboard's own action submits, so adding several values in a row never needs
            // a reach for "+" between them (BACKLOG F18).
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { submit() }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    R.string.day_instances_sheet_add_value_content_description,
                ),
            )
        }
    }
}

@Composable
private fun ValueField(
    instance: TagInstance,
    onUpdateInstance: (TagInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A local buffer so the field can freely show whatever's being typed (including a
    // transient blank while replacing the whole value) without ever persisting a blank —
    // the write below only fires once the trimmed text is non-empty again, mirroring
    // AddValueRow's blank guard.
    var text by remember(instance.id) { mutableStateOf(instance.value ?: "") }
    // Debounced, and trimmed. This used to write on *every keystroke* — one Room UPDATE per
    // character, each round-tripping back through the day's flow to recompose this sheet — and
    // it persisted the untrimmed text while testing the trimmed version, so `"dune "` is what
    // landed in the database (BACKLOG F11). Cancelling on each new keystroke means only the
    // pause at the end of typing writes.
    LaunchedEffect(text, instance.id) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == instance.value) return@LaunchedEffect
        delay(VALUE_WRITE_DEBOUNCE_MS)
        onUpdateInstance(instance.copy(value = trimmed))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newValue -> text = newValue },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.day_instances_sheet_value_placeholder)) },
        modifier = modifier,
    )
}
