package dev.krfu.tagday.ui.calendar.day

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagType

@Composable
fun TagType.label(): String = when (this) {
    TagType.SIMPLE -> stringResource(R.string.tag_type_simple)
    TagType.RATED -> stringResource(R.string.tag_type_rated)
    TagType.VALUED -> stringResource(R.string.tag_type_valued)
}
