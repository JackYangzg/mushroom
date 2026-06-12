package com.yangzhiguo.mushroom.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yangzhiguo.mushroom.recognition.MushroomRepository
import com.yangzhiguo.mushroom.ui.onboarding.OnboardingScreen
import com.yangzhiguo.mushroom.ui.recognition.RecognitionScreen
import com.yangzhiguo.mushroom.ui.recognition.ThreeDViewerScreen
import com.yangzhiguo.mushroom.ui.splash.SplashScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun MushroomNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = Route.Splash.path,
    ) {
        composable(Route.Splash.path) {
            SplashScreen(
                onReady = { firstLaunch ->
                    navController.navigate(
                        if (firstLaunch) Route.Onboarding.path else Route.MainShell.path,
                    ) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Onboarding.path) {
            OnboardingScreen(
                onDone = {
                    markOnboarded(context)
                    navController.navigate(Route.MainShell.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.MainShell.path) {
            MainShell()
        }

        // ---- 拍照 → 识别 → 详情/3D（设计文档 §2）----

        composable(
            route = Route.Recognition.PATTERN,
            arguments = listOf(navArgument(Route.Recognition.ARG_PHOTO) { type = NavType.StringType }),
        ) { entry ->
            val rawUri = entry.arguments?.getString(Route.Recognition.ARG_PHOTO).orEmpty()
            val photoUri = URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name())
            RecognitionScreen(
                photoUris = listOf(photoUri),
                onOpen3D = { name ->
                    navController.navigate(Route.ThreeD.build(name))
                },
                onRetake = {
                    navController.popBackStack(Route.MainShell.path, inclusive = false)
                },
            )
        }

        composable(
            route = Route.ThreeD.PATTERN,
            arguments = listOf(navArgument(Route.ThreeD.ARG_NAME) { type = NavType.StringType }),
        ) { entry ->
            val rawName = entry.arguments?.getString(Route.ThreeD.ARG_NAME).orEmpty()
            val name = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
            val holder: MushroomRepositoryHolder = hiltViewModel()
            ThreeDViewerScreen(
                scientificName = name,
                onClose = { navController.popBackStack() },
                repository = holder.repository,
            )
        }
    }
}

private fun markOnboarded(context: Context) {
    context.getSharedPreferences("mushroom_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarded", true)
        .apply()
}

/**
 * 小桥：让 MushroomRepository 通过 hiltViewModel 注入到 ThreeDViewerScreen。
 * 简单 ViewModel 透传，避免在 ThreeDViewerScreen 内做 Hilt EntryPoint 反射。
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class MushroomRepositoryHolder @javax.inject.Inject constructor(
    val repository: MushroomRepository,
) : androidx.lifecycle.ViewModel()
