package com.yangzhiguo.mushroom.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

/**
 * 我的 tab(用户要求) — 4 个入口:我的收藏 / 设置 / 免责声明 / 关于。
 *
 * 旧版包含微信昵称 / 订单 / 客服 / 法务 / 系统 等电商假页,本次重构全部移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onMyFavoritesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDisclaimerClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProfileEntry(
                label = stringResource(R.string.profile_my_favorites),
                icon = Icons.Rounded.Favorite,
                onClick = onMyFavoritesClick,
            )
            Divider()
            ProfileEntry(
                label = stringResource(R.string.profile_entry_settings),
                icon = Icons.Rounded.Settings,
                onClick = onSettingsClick,
            )
            Divider()
            ProfileEntry(
                label = stringResource(R.string.profile_entry_disclaimer),
                icon = Icons.Rounded.Warning,
                onClick = onDisclaimerClick,
            )
            Divider()
            ProfileEntry(
                label = stringResource(R.string.profile_entry_about),
                icon = Icons.Rounded.Info,
                onClick = onAboutClick,
            )
        }
    }
}

@Composable
private fun ProfileEntry(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        onClick = onClick,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
