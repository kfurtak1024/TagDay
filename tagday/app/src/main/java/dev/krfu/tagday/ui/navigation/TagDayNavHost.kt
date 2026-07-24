package dev.krfu.tagday.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.krfu.tagday.ui.calendar.CalendarPlaceholderScreen
import dev.krfu.tagday.ui.tags.TagsPlaceholderScreen

@Composable
fun TagDayNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TagDayDestination.CALENDAR.route,
        modifier = modifier,
    ) {
        composable(TagDayDestination.CALENDAR.route) { CalendarPlaceholderScreen() }
        composable(TagDayDestination.TAGS.route) { TagsPlaceholderScreen() }
    }
}
