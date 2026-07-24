package dev.krfu.tagday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.krfu.tagday.ui.navigation.TagDayApp
import dev.krfu.tagday.ui.theme.TagDayTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagDayTheme {
                TagDayApp()
            }
        }
    }
}
