package dev.krfu.tagday.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import dev.krfu.tagday.ui.theme.TagDayTheme

@Composable
fun TagDayApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    TagDayNavHost(navController = navController, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun TagDayAppPreview() {
    TagDayTheme {
        TagDayApp()
    }
}
