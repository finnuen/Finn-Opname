package com.example.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Drag-to-reorder for a LazyColumn, driven from a drag handle.
 *
 * While a drag is in progress the dragged row follows the finger via [draggingOffset] and the
 * list is reordered live through [onMove]; [onDrop] fires once when the finger lifts.
 */
@Stable
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val onStart: () -> Unit,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDrop: () -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    val isDragging: Boolean get() = draggingKey != null

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    // After a move the lazy layout needs one more pass before its item indices reflect it.
    // Until then we must not trigger another move from stale positions.
    private var awaitingIndex: Int? = null

    private val draggingInfo: LazyListItemInfo?
        get() = draggingKey?.let { key ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    /** Pixels the dragged row must be shifted from its current slot to stay under the finger. */
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + draggedDistance - it.offset } ?: 0f

    fun isDragging(key: Any): Boolean = draggingKey == key

    internal fun onDragStart(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        onStart()
        draggingKey = key
        initialOffset = info.offset
        draggedDistance = 0f
        awaitingIndex = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun onDrag(deltaY: Float) {
        if (draggingKey == null) return
        draggedDistance += deltaY
        maybeMove()
    }

    internal fun onDragEnd() {
        if (draggingKey == null) return
        draggingKey = null
        draggedDistance = 0f
        initialOffset = 0
        awaitingIndex = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onDrop()
    }

    private fun maybeMove() {
        val dragging = draggingInfo ?: return
        awaitingIndex?.let { expected ->
            if (dragging.index != expected) return
            awaitingIndex = null
        }
        val top = initialOffset + draggedDistance
        val middle = top + dragging.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != dragging.index && middle >= it.offset && middle <= it.offset + it.size
        } ?: return
        awaitingIndex = target.index
        onMove(dragging.index, target.index)
    }

    /** Keeps the list scrolling while the dragged row is held near the top or bottom edge. */
    internal suspend fun autoScrollWhileDragging() {
        while (coroutineContext.isActive && isDragging) {
            val dragging = draggingInfo
            if (dragging != null) {
                val layout = listState.layoutInfo
                val top = initialOffset + draggedDistance
                val bottom = top + dragging.size
                val edge = dragging.size * 0.75f
                val topGap = top - layout.viewportStartOffset
                val bottomGap = layout.viewportEndOffset - bottom
                val delta = when {
                    bottomGap < edge -> ((edge - bottomGap) * 0.2f).coerceAtMost(36f)
                    topGap < edge -> -((edge - topGap) * 0.2f).coerceAtMost(36f)
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    maybeMove()
                }
            }
            withFrameNanos { }
        }
    }
}

@Composable
private fun rememberDragDropState(
    listState: LazyListState,
    onStart: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit
): DragDropState {
    val haptics = LocalHapticFeedback.current
    val state = remember(listState) {
        DragDropState(listState, haptics, onStart, onMove, onDrop)
    }
    LaunchedEffect(state.isDragging) {
        if (state.isDragging) state.autoScrollWhileDragging()
    }
    return state
}

/**
 * Holds the order a list is displayed in. Normally that is just [source] (the database);
 * during a drag, and until the database has caught up with the drop, it is a local copy so
 * the rows never snap back to the old order.
 */
@Stable
class ReorderableList<T> internal constructor(
    private val source: State<List<T>>,
    private val idOf: (T) -> Long,
    private val onCommit: (List<Long>) -> Unit
) {
    private val local = mutableStateListOf<T>()
    private var useLocal by mutableStateOf(false)

    lateinit var drag: DragDropState
        internal set

    val items: List<T> get() = if (useLocal) local else source.value

    internal fun start() {
        local.clear()
        local.addAll(source.value)
        useLocal = true
    }

    internal fun move(from: Int, to: Int) {
        if (from in local.indices && to in local.indices) {
            local.add(to, local.removeAt(from))
        }
    }

    internal fun drop() {
        val ids = local.map(idOf)
        if (ids == source.value.map(idOf)) {
            useLocal = false // dropped where it started, nothing to save
        } else {
            onCommit(ids)    // stay on the local copy until the database emits the new order
        }
    }

    internal fun sourceChanged() {
        if (!drag.isDragging) useLocal = false
    }
}

@Composable
fun <T> rememberReorderableList(
    source: List<T>,
    idOf: (T) -> Long,
    listState: LazyListState,
    onCommit: (List<Long>) -> Unit
): ReorderableList<T> {
    val sourceState = rememberUpdatedState(source)
    val commit = rememberUpdatedState(onCommit)
    val list = remember(listState) {
        ReorderableList(sourceState, idOf) { ids -> commit.value(ids) }
    }
    list.drag = rememberDragDropState(
        listState = listState,
        onStart = list::start,
        onMove = list::move,
        onDrop = list::drop
    )
    LaunchedEffect(source) { list.sourceChanged() }
    return list
}

/** Attach to the drag handle only; the rest of the row stays free for taps. */
fun Modifier.dragHandle(state: DragDropState, key: Any): Modifier =
    pointerInput(state, key) {
        detectVerticalDragGestures(
            onDragStart = { state.onDragStart(key) },
            onVerticalDrag = { change, amount ->
                change.consume()
                state.onDrag(amount)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() }
        )
    }

/**
 * Row modifier for items inside `items(..., key = ...)`. The dragged row lifts and follows the
 * finger; every other row animates into its new slot.
 */
fun LazyItemScope.reorderableRow(state: DragDropState, key: Any): Modifier =
    if (state.isDragging(key)) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.draggingOffset
                shadowElevation = 14.dp.toPx()
            }
    } else {
        Modifier.animateItem()
    }
