package com.yangzhiguo.mushroom.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.home.HomeScreen
import com.yangzhiguo.mushroom.ui.misc.AboutScreen
import com.yangzhiguo.mushroom.ui.misc.DisclaimerScreen
import com.yangzhiguo.mushroom.ui.profile.MyFavoritesScreen
import com.yangzhiguo.mushroom.ui.profile.ProfileScreen
import com.yangzhiguo.mushroom.ui.settings.SettingsScreen
import com.yangzhiguo.mushroom.ui.species.SpeciesDetailScreen
import com.yangzhiguo.mushroom.ui.species.SpeciesListScreen

/**
 * MainShell — 底部 3 个 tab（用户要求）:
 *  - 主页:最常见蘑菇 + 拍照识别
 *  - 常见:100 种蘑菇(可食/可药用/有毒/需谨慎)
 *  - 我的:我的收藏 / 设置 / 免责声明 / 关于
 *
 *  SpeciesDetail / AiRecognitionFlow / 3D 仍保留路由(在拍照链路中跳转),
 *  不再作为独立底部 tab。
 */
@Composable
fun MainShell() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                BottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(padding),
        ) {
            // ---- 底部 3 个 tab ----
            composable(Route.Home.path) {
                HomeScreen(
                    onStartAiRecognition = {
                        navController.navigate(Route.AiRecognitionFlow.path)
                    },
                )
            }
            composable(Route.SpeciesList.path) {
                SpeciesListScreen(
                    onSpeciesClick = { id ->
                        navController.navigate(Route.SpeciesDetail.build(id))
                    },
                )
            }
            composable(Route.Profile.path) {
                ProfileScreen(
                    onMyFavoritesClick = { navController.navigate(Route.MyFavorites.path) },
                    onSettingsClick = { navController.navigate(Route.Settings.path) },
                    onDisclaimerClick = { navController.navigate(Route.Disclaimer.path) },
                    onAboutClick = { navController.navigate(Route.About.path) },
                )
            }

            // ---- "我的" tab 内子页(非底部 tab,带返回)----
            composable(Route.MyFavorites.path) {
                MyFavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onSpeciesClick = { id ->
                        navController.navigate(Route.SpeciesDetail.build(id))
                    },
                )
            }
            composable(Route.Settings.path) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Route.Disclaimer.path) {
                DisclaimerScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Route.About.path) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // ---- 拍照 → 识别 → 详情/3D 流程（不在 tab 中显示）----
            composable(Route.AiRecognitionFlow.path) {
                com.yangzhiguo.mushroom.ui.navigation.ConsultationFlowNav(
                    useAi = true,
                    onExit = { navController.popBackStack(Route.Home.path, inclusive = false) },
                    onOpen3D = { name ->
                        navController.navigate(Route.ThreeD.build(name))
                    },
                )
            }
            composable(Route.SpeciesDetail.PATTERN) { entry ->
                val id = entry.arguments?.getString("speciesId")?.toIntOrNull() ?: 0
                SpeciesDetailScreen(
                    speciesId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val TAB_ROUTES = setOf(
    Route.Home.path,
    Route.SpeciesList.path,
    Route.Profile.path,
)

private val Tabs = listOf(
    TabItem(Route.Home.path, R.string.tab_home, Icons.Rounded.Home),
    TabItem(Route.SpeciesList.path, R.string.tab_species, Icons.Rounded.MenuBook),
    TabItem(Route.Profile.path, R.string.tab_profile, Icons.Rounded.Person),
)

@Composable
private fun BottomBar(
    currentRoute: String?,
    onTabSelected: (TabItem) -> Unit,
) {
    NavigationBar {
        Tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
