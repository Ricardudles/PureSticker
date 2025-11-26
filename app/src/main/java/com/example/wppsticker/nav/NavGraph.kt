package com.example.wppsticker.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.wppsticker.ui.editor.EditorScreen
import com.example.wppsticker.ui.home.HomeScreen
import com.example.wppsticker.ui.home.StickerTypeSelectionScreen
import com.example.wppsticker.ui.stickerpack.PackageSelectionScreen
import com.example.wppsticker.ui.settings.RestorePreviewScreen
import com.example.wppsticker.ui.settings.SettingsScreen
import com.example.wppsticker.ui.stickerpack.PackageScreen
import com.example.wppsticker.ui.stickerpack.SaveStickerScreen
import com.example.wppsticker.ui.videoeditor.VideoEditorScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.name
    ) {
        composable(Screen.Home.name) { HomeScreen(navController) }

        composable(Screen.StickerPack.name + "/{packageId}", arguments = listOf(
            navArgument("packageId") { type = NavType.IntType }
        )) { backStackEntry ->
            PackageScreen(
                navController = navController
            )
        }

        composable(Screen.StickerTypeSelection.name) { StickerTypeSelectionScreen(navController) }

        composable(
            route = Screen.PackageSelection.name + "?isAnimated={isAnimated}&stickerUri={stickerUri}&emojis={emojis}&preSelectedPackageId={preSelectedPackageId}",
            arguments = listOf(
                navArgument("isAnimated") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("stickerUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("emojis") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("preSelectedPackageId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) {
            PackageSelectionScreen(navController)
        }

        composable(
            route = Screen.Editor.name + "/{stickerUri}?packageId={packageId}",
            arguments = listOf(
                navArgument("stickerUri") { type = NavType.StringType },
                navArgument("packageId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) {
            EditorScreen(navController)
        }

        composable(
            route = Screen.VideoEditor.name + "/{stickerUri}?packageId={packageId}",
            arguments = listOf(
                navArgument("stickerUri") { type = NavType.StringType },
                navArgument("packageId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) {
            VideoEditorScreen(navController)
        }

        composable(
            route = Screen.SaveSticker.name + "/{stickerUri}?packageId={packageId}&isAnimated={isAnimated}",
            arguments = listOf(
                navArgument("stickerUri") { type = NavType.StringType },
                navArgument("packageId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("isAnimated") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            SaveStickerScreen(navController = navController)
        }
        
        composable(Screen.Settings.name) {
            SettingsScreen(navController)
        }

        composable(
            route = Screen.RestorePreview.name + "/{backupUri}",
            arguments = listOf(
                navArgument("backupUri") { type = NavType.StringType }
            )
        ) {
            RestorePreviewScreen(navController)
        }
    }
}
