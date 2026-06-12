package com.yangzhiguo.mushroom.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

/**
 * 我的收藏(子页)。
 *
 * 设计说明:
 *  - 当前没有真正的收藏数据层(`SpeciesEntity` 不含 is_favorite 字段,
 *    也没有 favorites 表/DAO),因此本屏先做空态占位。
 *  - 等真正的收藏存储落地后,只需在此处挂上一个 ViewModel
 *    从 Room/DataStore 拉取 species id 集合,渲染 SpeciesRow 列表即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFavoritesScreen(
    onBack: () -> Unit = {},
    onSpeciesClick: (Int) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_favorites_title)) },
                navigationIcon = {
                    Text(
                        text = stringResource(R.string.species_back),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.my_favorites_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.my_favorites_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
