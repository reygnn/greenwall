package com.github.reygnn.greenwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.reygnn.greenwall.ui.screens.EditorScreen
import com.github.reygnn.greenwall.ui.theme.GreenwallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenwallTheme {
                EditorScreen()
            }
        }
    }
}
