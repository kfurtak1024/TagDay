package dev.krfu.tagday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.krfu.tagday.ui.MainViewModel
import dev.krfu.tagday.ui.TagDayApp
import dev.krfu.tagday.ui.theme.TagDayTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as TagDayApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagDayTheme {
                TagDayApp(viewModel)
            }
        }
    }
}
