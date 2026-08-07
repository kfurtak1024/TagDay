package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R

@Composable
fun StarInput(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not `Icons.Outlined.Star`: in material-icons-core that's the *same solid star* as
    // Icons.Filled.Star (identical path data — verified against the artifact's sources), so
    // every star rendered filled whatever the rating was. The genuine hollow star is a
    // hand-added drawable, per ADR-010's no-material-icons-extended rule.
    val emptyStar = ImageVector.vectorResource(R.drawable.ic_star_border)

    // The row states the rating; each star states what tapping it *does*. Previously every
    // star was labelled "%d stars" with its own index, so a screen reader read out
    // "1 stars, 2 stars… 5 stars" and never said what the rating actually was (BACKLOG F16).
    val currentRating = if (rating > 0) {
        pluralStringResource(R.plurals.day_rating_current_content_description, rating, rating)
    } else {
        stringResource(R.string.day_rating_unrated_content_description)
    }

    Row(
        modifier = modifier.semantics { contentDescription = currentRating },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..5).forEach { star ->
            Icon(
                imageVector = if (star <= rating) Icons.Filled.Star else emptyStar,
                contentDescription = pluralStringResource(
                    R.plurals.day_rating_star_content_description,
                    star,
                    star,
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onRatingSelected(star) },
            )
        }
    }
}
