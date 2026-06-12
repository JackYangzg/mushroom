package com.yangzhiguo.mushroom.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

/**
 * S4 Camera. Uses the system camera intent.
 */
@Composable
fun CameraScreen(
    onPhotoReady: (android.net.Uri) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val controller = rememberCameraController(context, onPhotoReady = onPhotoReady)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.camera_close), tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💡", modifier = Modifier.padding(8.dp))
                    Text("🔄", modifier = Modifier.padding(8.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .background(Color.White.copy(alpha = 0.15f), shape = MaterialTheme.shapes.large),
            ) {
                Text(
                    text = stringResource(R.string.camera_hint),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (controller.cameraPermission.status.isGranted) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                val uri = controller.newPhotoUri(context)
                                controller.takePicture.launch(uri)
                            },
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.camera_shutter), tint = Color.Black)
                        }
                    }
                } else {
                    PermissionPrompt(
                        onRequest = { controller.cameraPermission.launchPermissionRequest() },
                    )
                }
                IconButton(
                    onClick = {
                        controller.pickFromGallery.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.camera_gallery), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.camera_permission_body),
            color = Color.White,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.camera_permission_open_settings))
        }
    }
}

private val com.google.accompanist.permissions.PermissionStatus.isGranted: Boolean
    get() = this == com.google.accompanist.permissions.PermissionStatus.Granted
