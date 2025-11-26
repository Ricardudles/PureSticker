package com.example.wppsticker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.wppsticker.nav.NavGraph
import com.example.wppsticker.ui.theme.WppStickerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WppStickerTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
