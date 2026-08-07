package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagName

private const val MAX_VISIBLE_SUGGESTIONS = 4

@Composable
fun TagQuickEntryBar(
    allTags: List<Tag>,
    onAddExistingTag: (tagId: Long) -> Unit,
    onAddRatingToExistingTag: (tagId: Long, rating: Int) -> Unit,
    onAddValuesToExistingTag: (tagId: Long, values: List<String>) -> Unit,
    onEditExistingTag: (tagId: Long) -> Unit,
    onCreateTag: (name: String, type: TagType, rating: Int?, values: List<String>) -> Unit,
    onCreateTagForEditing: (name: String, type: TagType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The type is now an explicit selection rather than something a chip tap decides on the
    // spot, so it needs to survive recomposition (and rotation) between typing and "+".
    var selectedType by rememberSaveable { mutableStateOf(TagType.SIMPLE) }
    val parsed = ParsedTagInput.parse(query)
    val trimmedName = parsed?.name.orEmpty()
    // Only *creating* a tag has to satisfy the naming rules; matching an existing one
    // doesn't, so tags named before the rules existed stay reachable by typing their name.
    val isCreatableName = TagName.isValid(trimmedName)
    val exactMatch = if (trimmedName.isEmpty()) {
        null
    } else {
        allTags.find { it.name.equals(trimmedName, ignoreCase = true) }
    }
    // Capped by count, not height, and forced single-line below: a partial, clipped row
    // (e.g. a height cap that lands mid-item) reads as the input field overlapping the
    // list rather than as "scroll for more".
    val suggestions = if (trimmedName.isEmpty()) {
        emptyList()
    } else {
        allTags.filter { it.name.contains(trimmedName, ignoreCase = true) }
            .take(MAX_VISIBLE_SUGGESTIONS)
    }

    // The `name:***` / `name:value` shorthands (ADR-009) still work, and now move the
    // selection rather than bypassing it — so what's selected is always what "+" will
    // create, and the shorthand stays a shortcut instead of a hidden second mechanism.
    val typeFromSyntax = when (parsed) {
        is ParsedTagInput.Rated -> TagType.RATED
        is ParsedTagInput.Valued -> TagType.VALUED
        is ParsedTagInput.Ambiguous, null -> null
    }
    LaunchedEffect(typeFromSyntax) {
        if (typeFromSyntax != null) selectedType = typeFromSyntax
    }

    val isCreating = exactMatch == null && isCreatableName

    // Which of those an existing tag gets is decided by QuickEntryAction, a pure function so
    // ADR-034's table can be unit-tested; this only routes the result to the callbacks.
    fun addToExisting(tag: Tag) = when (val action = QuickEntryAction.forExistingTag(tag, parsed)) {
        is QuickEntryAction.AddInstance -> onAddExistingTag(action.tagId)
        is QuickEntryAction.AddRating -> onAddRatingToExistingTag(action.tagId, action.rating)
        is QuickEntryAction.AddValues -> onAddValuesToExistingTag(action.tagId, action.values)
        is QuickEntryAction.OpenSheet -> onEditExistingTag(action.tagId)
    }

    fun submit() {
        when {
            trimmedName.isEmpty() -> return
            exactMatch != null -> addToExisting(exactMatch)
            !isCreatableName -> return

            // A seed only applies to the type it belongs to: pick Simple after typing
            // `film:dune` and you get a Simple `film`, matching what the selector shows.
            //
            // Rated and Valued behave the same when nothing was seeded: create the tag with
            // no instance and open the sheet to set the first rating/value there, rather than
            // adding an instance with nothing in it (ADR-031).
            selectedType == TagType.RATED -> {
                val rating = (parsed as? ParsedTagInput.Rated)?.rating
                if (rating == null) {
                    onCreateTagForEditing(trimmedName, TagType.RATED)
                } else {
                    onCreateTag(trimmedName, TagType.RATED, rating, emptyList())
                }
            }

            selectedType == TagType.VALUED -> {
                val values = (parsed as? ParsedTagInput.Valued)?.values.orEmpty()
                if (values.isEmpty()) {
                    onCreateTagForEditing(trimmedName, TagType.VALUED)
                } else {
                    onCreateTag(trimmedName, TagType.VALUED, null, values)
                }
            }

            // Simple has nothing to configure, so it's created with its first instance.
            else -> onCreateTag(trimmedName, TagType.SIMPLE, null, emptyList())
        }
        query = ""
        selectedType = TagType.SIMPLE
    }

    Surface(modifier = modifier.fillMaxWidth().imePadding(), tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            suggestions.forEach { tag ->
                ListItem(
                    headlineContent = {
                        Text(text = tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { ColorDot(color = tag.color) },
                    // Same dispatch as typing the name exactly: a suggestion carries no seed,
                    // so Rated/Valued open the sheet rather than gaining a blank instance.
                    modifier = Modifier.clickable {
                        addToExisting(tag)
                        query = ""
                    },
                )
            }

            // Shown whenever the typed name would create a new tag — regardless of whether
            // suggestions are also showing, since a partial match doesn't mean the typed name
            // shouldn't also be creatable as its own new tag.
            if (isCreating) {
                TagTypePicker(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    // Only the name half is sanitized as it's typed — values are free text
                    // (`film:blade runner`, `set:1,2,3`) and must stay untouched.
                    onValueChange = { raw -> query = sanitizeNameHalf(raw) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.day_quick_entry_placeholder)) },
                    // Submitting from the keyboard, in an app whose main loop is
                    // type-a-tag-and-add — "+" stays, it's just no longer the only way
                    // (BACKLOG F18). `submit()` already no-ops on input it can't act on.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { submit() }, enabled = exactMatch != null || isCreating) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.day_quick_entry_submit_content_description),
                    )
                }
            }
        }
    }
}

/**
 * Applies [TagName.sanitize] to everything before the first `:`, leaving the value/rating
 * suffix alone. Keeps the field's text and the name that would be created identical, so
 * there's never a hidden difference between what's typed and what gets saved.
 */
private fun sanitizeNameHalf(raw: String): String {
    val separatorIndex = raw.indexOf(':')
    return if (separatorIndex < 0) {
        TagName.sanitize(raw)
    } else {
        TagName.sanitize(raw.substring(0, separatorIndex)) + raw.substring(separatorIndex)
    }
}

/**
 * Type picker for the tag about to be created. A single-select segmented button row, which is
 * Material's component for a small set of mutually exclusive options that should all stay
 * visible — and, unlike a plain `Row` of chips, it carries `selectableGroup()` semantics so
 * screen readers announce it as one "N of 3" choice. Simple is preselected, so the common
 * case is type-a-name-and-press-+ with nothing else to touch. See ADR-029.
 */
@Composable
private fun TagTypePicker(
    selectedType: TagType,
    onTypeSelected: (TagType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not fillMaxWidth: the row applies `width(IntrinsicSize.Min)` internally, so it sizes to
    // its labels whatever the caller asks for — content-sized is the intended look.
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        TagType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = TagType.entries.size,
                ),
                label = { Text(type.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ColorDot(color: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}
