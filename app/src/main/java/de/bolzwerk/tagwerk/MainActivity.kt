package de.bolzwerk.tagwerk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.bolzwerk.tagwerk.ui.navigation.TagwerkNavHost
import de.bolzwerk.tagwerk.ui.theme.TagwerkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagwerkTheme {
                TagwerkNavHost()
            }
        }
    }
}
