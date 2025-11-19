package com.example.wppsticker.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.wppsticker.ui.editor.EditorScreen
import com.example.wppsticker.ui.home.HomeScreen
import com.example.wppsticker.ui.stickerpack.PackageScreen
import com.example.wppsticker.ui.stickerpack.SaveStickerScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.name) {
        composable(Screen.Home.name) {
            HomeScreen(navController = navController)
        }
        composable(
            route = "${Screen.StickerPack.name}/{packageId}",
            arguments = listOf(navArgument("packageId") { type = NavType.IntType })
        ) {
            PackageScreen(navController = navController)
        }
        composable(
            route = "${Screen.Editor.name}/{imageUri}",
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) {
            EditorScreen(navController = navController)
        }
        composable(
            route = "${Screen.SaveSticker.name}/{stickerUri}",
            arguments = listOf(navArgument("stickerUri") { type = NavType.StringType })
        ) {
            SaveStickerScreen(navController = navController)
        }
    }
}
