
package com.nilaass.assistant

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun NilaAvatarPage() {
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> avatarUri = uri }

    val animation = rememberInfiniteTransition(label = "nila")
    val scale by animation.animateFloat(
        initialValue = 1f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071426))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Nila • Avatar",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF65DFFF)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "வணக்கம்! நான் நிலா ❤️",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10243B)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri == null) {
                Text(
                    "Choose Nila's full-body HD image",
                    color = Color.White
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { view ->
                        view.setImageURI(avatarUri)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose Nila image")
        }

        Text(
            "Next: Tamil voice • Offline AI • Lip-sync",
            color = Color(0xFF65DFFF),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
