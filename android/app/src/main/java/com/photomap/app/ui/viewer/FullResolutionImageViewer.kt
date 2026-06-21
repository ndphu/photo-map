package com.photomap.app.ui.viewer

import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File

@Composable
fun FullResolutionImageViewer(
    filePath: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SubsamplingScaleImageView(context).apply {
                setBackgroundColor(Color.BLACK)
                setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
            }
        },
        update = { viewer ->
            if (viewer.tag != filePath) {
                viewer.tag = filePath
                viewer.setImage(ImageSource.uri(Uri.fromFile(File(filePath))))
            }
        },
        onRelease = SubsamplingScaleImageView::recycle,
    )
}
