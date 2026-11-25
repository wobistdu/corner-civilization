package com.example.cornercivilization.p37f776dc.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.platform.resize.SpatialContainerUtils

/** Reconcile OS 6.2.1's asynchronous depth bounds without a private 3D setter. */
@Composable
internal fun SynchronizeVolumeSize(size: IntSize, depth: Dp) {
    val context = LocalContext.current
    val maximumPx = with(LocalDensity.current) { 2700.dp.roundToPx() }
    LaunchedEffect(size, depth) {
        if (size.width <= 1 || size.height <= 1) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        // A same-X/Y request can be ignored even after new Z bounds arrive.
        // Re-measure within the existing limit, then restore the target next frame.
        // Main ignores this <=1px pulse so it never changes the depth contract.
        val offset = if (size.width >= maximumPx || size.height >= maximumPx) -1 else 1
        SpatialContainerUtils.resizeCurrentWindowContainer(context, size.width + offset, size.height + offset)
        withFrameNanos { }
        SpatialContainerUtils.resizeCurrentWindowContainer(context, size.width, size.height)
    }
}
