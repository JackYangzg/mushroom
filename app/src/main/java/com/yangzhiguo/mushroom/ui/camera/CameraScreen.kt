package com.yangzhiguo.mushroom.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R

@Composable
fun CameraScreen(
    onPhotoReady: (android.net.Uri) -> Unit,
    onPhotosReady: (List<android.net.Uri>) -> Unit = { uris -> uris.forEach(onPhotoReady) },
    onClose: () -> Unit,
    openGalleryOnLaunch: Boolean = false,
) {
    val context = LocalContext.current
    val controller = rememberCameraController(
        context,
        onPhotoReady = onPhotoReady,
        onPhotosReady = onPhotosReady,
    )
    LaunchedEffect(openGalleryOnLaunch) {
        if (openGalleryOnLaunch) {
            controller.pickMultipleFromGallery.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(270.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(20.dp)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "让整株蘑菇进入画面，尽量拍清菌褶和菌柄",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (controller.cameraPermission.status.isGranted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(
                            onClick = {
                                controller.pickMultipleFromGallery.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.camera_gallery), tint = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    val uri = controller.newPhotoUri(context)
                                    controller.takePicture.launch(uri)
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(
                                    Modifier
                                        .size(58.dp)
                                        .border(2.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
                                )
                            }
                        }
                        Box(Modifier.size(56.dp))
                    }
                } else {
                    PermissionPrompt(
                        onRequest = { controller.cameraPermission.launchPermissionRequest() },
                        onGallery = {
                            controller.pickMultipleFromGallery.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, onGallery: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.camera_permission_body), color = Color.White)
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_open_settings)) }
        IconButton(onClick = onGallery) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.camera_gallery), tint = Color.White)
        }
    }
}

private val com.google.accompanist.permissions.PermissionStatus.isGranted: Boolean
    get() = this == com.google.accompanist.permissions.PermissionStatus.Granted
