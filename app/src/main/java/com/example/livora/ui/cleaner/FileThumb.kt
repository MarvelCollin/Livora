package com.example.livora.ui.cleaner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.cleaner.CleanerFile
import com.example.livora.data.people.media.ThumbnailLoader
import com.example.livora.ui.components.SkeletonBox

@Composable
fun FileThumb(
    file: CleanerFile,
    sizePx: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    showDuration: Boolean = true
) {
    val context = LocalContext.current
    val bitmap by produceState(ThumbnailLoader.peekKey(file.key, sizePx), file.key, sizePx) {
        if (value == null) value = ThumbnailLoader.loadFile(context, file.uri, file.key, sizePx, file.video)
    }
    val loaded = bitmap
    Box(modifier = modifier.clip(shape)) {
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            SkeletonBox(modifier = Modifier.matchParentSize(), shape = shape)
        }
        if (file.video && showDuration) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = duration(file.durationMs),
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

fun duration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
