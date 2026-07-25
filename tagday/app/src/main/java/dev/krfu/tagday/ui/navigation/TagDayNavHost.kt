package dev.krfu.tagday.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.krfu.tagday.ui.calendar.day.DayScreen
import dev.krfu.tagday.ui.tags.TagsScreen

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
        composable(TagDayDestination.CALENDAR.route) {
            DayScreen(onNavigateToTags = { navController.navigate(TagDayDestination.TAGS.route) })
        }
        composable(TagDayDestination.TAGS.route) {
            TagsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
