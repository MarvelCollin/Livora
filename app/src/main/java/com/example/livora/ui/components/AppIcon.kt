package com.example.livora.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.example.livora.data.apps.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface IconState {
    data object Loading : IconState
    data object Missing : IconState
    class Ready(val image: ImageBitmap) : IconState
}

@Composable
fun AppIcon(packageName: String, label: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val state by produceState<IconState>(IconState.Loading, packageName, sizePx) {
        val image = withContext(Dispatchers.IO) { InstalledApps.icon(context, packageName, sizePx) }
        value = if (image == null) IconState.Missing else IconState.Ready(image)
    }
    val base = modifier.size(size).semantics { contentDescription = "$label icon" }
    when (val current = state) {
        is IconState.Ready -> Image(
            bitmap = current.image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = base
        )
        IconState.Loading -> SkeletonBox(modifier = base, shape = RoundedCornerShape(size / 4))
        IconState.Missing -> Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = base
        )
    }
}
