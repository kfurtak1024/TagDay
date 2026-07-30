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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListSheet(
    group: TagDisplayGroup,
    onDismiss: () -> Unit,
    onUpdateInstance: (TagInstance) -> Unit,
    onRemoveInstance: (TagInstance) -> Unit,
    onAddValue: (tagId: Long, value: String) -> Unit,
    onReorderValues: (List<TagInstance>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // skipPartiallyExpanded + confirmValueChange rejecting Hidden means the only
    // reachable SheetValue is Expanded — dragging (or back/scrim) can never dismiss it,
    // since hide() (whatever triggers it) can't complete. Closing only happens through
    // the explicit close button below, which just calls onDismiss directly.
    //
    // confirmValueChange alone only vetoes the final *settle* target — the sheet's own
    // `.draggable()` still followed the finger down (and snapped back on release), so
    // the panel was still visibly slidable. `sheetGesturesEnabled = false` disables that
    // `.draggable()` modifier outright (see ModalBottomSheet.kt's ModalBottomSheetContent
    // — it also skips the swipe-dismiss nested scroll connection entirely when false), so
    // there's no drag motion on the sheet surface to begin with.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    val panelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
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
            if (group.type == TagType.VALUED) {
                ValuedInstanceList(
                    instances = group.instances,
                    onUpdateInstance = onUpdateInstance,
                    onRemoveInstance = onRemoveInstance,
                    onReorder = onReorderValues,
                    modifier = Modifier.weight(1f),
                )
                AddValueRow(onAdd = { value -> onAddValue(group.tagId, value) })
            } else {
                TimeOrderedInstanceList(
                    instances = group.instances,
                    type = group.type,
                    onUpdateInstance = onUpdateInstance,
                    onRemoveInstance = onRemoveInstance,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The list for types that stay in creation order — Rated in practice (Simple can't open
 * this sheet at all, ADR-018, but degrades to a plain timestamp row if it ever does).
 * Valued is manually ordered instead, see [ValuedInstanceList].
 */
@Composable
private fun TimeOrderedInstanceList(
    instances: List<TagInstance>,
    type: TagType,
    onUpdateInstance: (TagInstance) -> Unit,
    onRemoveInstance: (TagInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableInstanceList(modifier = modifier) {
        instances.forEach { instance ->
            key(instance.id) {
                ListItem(
                    headlineContent = {
                        when (type) {
                            TagType.RATED -> StarInput(
                                rating = instance.rating ?: 0,
                                onRatingSelected = { rating ->
                                    onUpdateInstance(instance.copy(rating = rating))
                                },
                            )
                            // Nothing to edit — the timestamp is the whole row.
                            else -> Text(text = formatTime(instance.createdAt))
                        }
                    },
                    // Only where the headline is an editor, or it would just repeat itself.
                    supportingContent = if (type == TagType.RATED) {
                        { Text(text = formatTime(instance.createdAt)) }
                    } else {
                        null
                    },
                    trailingContent = { RemoveInstanceButton(onClick = { onRemoveInstance(instance) }) },
                )
            }
        }
    }
}

@Composable
private fun ValuedInstanceList(
    instances: List<TagInstance>,
    onUpdateInstance: (TagInstance) -> Unit,
    onRemoveInstance: (TagInstance) -> Unit,
    onReorder: (List<TagInstance>) -> Unit,
    modifier: Modifier = Modifier,
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
                ListItem(
                    modifier = Modifier
                        .onSizeChanged { rowHeights[instance.id] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset {
                            IntOffset(x = 0, y = if (isDragged) dragOffset.roundToInt() else 0)
                        },
                    colors = if (isDragged) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    leadingContent = {
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
                    },
                    headlineContent = {
                        ValueField(instance = instance, onUpdateInstance = onUpdateInstance)
                    },
                    trailingContent = {
                        RemoveInstanceButton(onClick = { onRemoveInstance(instance) })
                    },
                )
            }
        }
    }
}

/**
 * The scrollable region both instance lists live in: a `Column` rather than a `LazyColumn`
 * because these lists hold one tag's instances for a single day (a handful), and because
 * [ValuedInstanceList]'s drag needs every row measured, including off-screen ones.
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
                .fillMaxSize()
                .verticalScroll(scrollState),
            content = content,
        )
        ScrollbarTrack(state = scrollState, modifier = Modifier.align(Alignment.CenterEnd))
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

// A dedicated overlay sibling, not a modifier chained onto the scrollable Column itself.
// The previous attempt chained `.verticalScroll(state).verticalScrollbar(state)` on the
// *same* node — but that puts the scrollbar's own draw inside the part of the modifier
// chain that verticalScroll offsets to implement scrolling, so the thumb scrolled away
// with the content instead of staying fixed as a viewport overlay: as you scrolled down,
// more of the (fixed, document-relative) thumb rectangle ended up shifted above the
// visible top edge and got clipped there, which is exactly "gets smaller and smaller,
// doesn't move". A separate sibling Box, drawn in the *parent* Box's coordinate space,
// is never touched by the Column's internal scroll offset — only `ScrollIndicatorState`
// (read reactively, so it still redraws every scroll frame) determines where the thumb
// sits within it.
@Composable
private fun ScrollbarTrack(state: ScrollState, modifier: Modifier = Modifier) {
    val thumbColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .drawBehind {
                val indicator = state.scrollIndicatorState ?: return@drawBehind
                val viewportSize = indicator.viewportSize
                val contentSize = indicator.contentSize
                val scrollOffset = indicator.scrollOffset
                if (
                    viewportSize <= 0 ||
                    contentSize == Int.MAX_VALUE ||
                    viewportSize == Int.MAX_VALUE ||
                    scrollOffset == Int.MAX_VALUE ||
                    contentSize <= viewportSize
                ) {
                    return@drawBehind
                }
                val trackHeight = size.height
                val thumbHeight = (trackHeight * viewportSize / contentSize.toFloat())
                    .coerceAtLeast(size.width * 4)
                val maxScroll = (contentSize - viewportSize).toFloat()
                val startY = (trackHeight - thumbHeight) * (scrollOffset / maxScroll)
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(size.width / 2),
                )
            },
    )
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
    // onUpdateInstance only fires once the trimmed text is non-empty again, mirroring
    // AddValueRow's blank guard.
    var text by remember(instance.id) { mutableStateOf(instance.value ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            text = newValue
            if (newValue.trim().isNotEmpty()) {
                onUpdateInstance(instance.copy(value = newValue))
            }
        },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.day_instances_sheet_value_placeholder)) },
        modifier = modifier,
    )
}

// Immutable and thread-safe, so a top-level val rather than a remembered/passed-around
// instance — same as DayContent.kt's formatters.
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
