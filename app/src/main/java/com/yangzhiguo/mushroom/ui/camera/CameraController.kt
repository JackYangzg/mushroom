package com.yangzhiguo.mushroom.ui.camera

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

/**
 * Bundle of ActivityResult launchers and the camera-permission state used by S4.
 */
@OptIn(ExperimentalPermissionsApi::class)
class CameraController internal constructor(
    val takePicture: androidx.activity.result.ActivityResultLauncher<Uri>,
    val pickMultipleFromGallery: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val cameraPermission: PermissionState,
    val pendingFile: () -> File,
) {
    fun newPhotoUri(context: Context): Uri {
        val file = pendingFile().also { it.parentFile?.mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberCameraController(
    context: Context,
    onPhotoReady: (Uri) -> Unit,
    onPhotosReady: (List<Uri>) -> Unit = { uris -> uris.forEach(onPhotoReady) },
): CameraController {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val pendingFileHolder = remember { arrayOfNulls<File>(1) }
    val pendingFile = remember {
        {
            pendingFileHolder[0] ?: File(
                context.cacheDir,
                "photos/${System.currentTimeMillis()}.jpg",
            ).also { pendingFileHolder[0] = it }
        }
    }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            val file = pendingFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            onPhotoReady(uri)
        }
        pendingFileHolder[0] = null
    }
    val pickMultipleFromGallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6),
    ) { uris ->
        if (uris.isNotEmpty()) onPhotosReady(uris)
    }
    return remember {
        CameraController(takePicture, pickMultipleFromGallery, cameraPermission, pendingFile)
    }
}
