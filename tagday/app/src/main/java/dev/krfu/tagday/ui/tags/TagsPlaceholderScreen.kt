package dev.krfu.tagday.ui.tags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.krfu.tagday.R
import dev.krfu.tagday.ui.theme.TagDayTheme

@Composable
fun TagsPlaceholderScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.tags_placeholder_message))
    }
}

@Preview(showBackground = true)
@Composable
fun TagsPlaceholderScreenPreview() {
    TagDayTheme {
        TagsPlaceholderScreen()
    }
}
