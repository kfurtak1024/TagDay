package dev.krfu.tagday.ui.calendar

import dev.krfu.tagday.data.local.entity.TagInstance

/**
 * A Day-zoom removal (capsule "x" or the instance-list sheet's per-instance delete) that
 * the UI has already hidden but hasn't yet been sent to the repository — delay-delete
 * undo, see ADR-019. `instances` is what actually gets deleted once this commits;
 * `tagName` is only for the snackbar message.
 */
data class PendingRemoval(
    val instances: List<TagInstance>,
    val tagName: String,
)
