package com.yangzhiguo.mushroom.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

@Composable
fun ProfileScreen(
    onMyFavoritesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDisclaimerClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
            ProfileEntry(stringResource(R.string.profile_my_favorites), Icons.Rounded.FavoriteBorder, onMyFavoritesClick)
            Divider(modifier = Modifier.padding(start = 60.dp))
            ProfileEntry(stringResource(R.string.profile_entry_settings), Icons.Rounded.Settings, onSettingsClick)
            Divider(modifier = Modifier.padding(start = 60.dp))
            ProfileEntry(stringResource(R.string.profile_entry_disclaimer), Icons.Rounded.Shield, onDisclaimerClick)
            Divider(modifier = Modifier.padding(start = 60.dp))
            ProfileEntry(stringResource(R.string.profile_entry_about), Icons.Rounded.Info, onAboutClick)
        }
    }
}

@Composable
private fun ProfileEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
