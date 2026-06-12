package com.yangzhiguo.mushroom.ui.consultation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yangzhiguo.mushroom.ui.navigation.ConsultationFlowNav

@Composable
fun ConsultationFlowDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConsultationFlowNav(onExit = onDismiss)
        }
    }
}
