package com.example.cornercivilization.p37f776dc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.cornercivilization.p37f776dc.ui.fire.FireSliceScreen
import com.example.cornercivilization.p37f776dc.scene.SynchronizeVolumeSize
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter

fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultWindowContainer {
        PicoTheme {
            val converter = LocalPhysicalLengthConverter.current
            val density = LocalDensity.current
            val minDepth = converter.lengthToDp(.48f, LengthUnit.Meters)
            val maxDepth = 2700.dp
            var measuredDepth by remember { mutableStateOf(maxDepth) }
            var windowSize by remember { mutableStateOf(IntSize.Zero) }
            SynchronizeVolumeSize(windowSize, measuredDepth)
            Box(Modifier.windowConstraints(
                minWidth = converter.lengthToDp(.48f, LengthUnit.Meters), minHeight = converter.lengthToDp(.48f, LengthUnit.Meters),
                minDepth = measuredDepth,
                maxWidth = 2700.dp, maxHeight = 2700.dp,
                maxDepth = measuredDepth,
            ).fillMaxSize().onSizeChanged { size ->
                // Keep rendered depth in step with the uniformly resized cube.
                // A depth range alone collapses to its minimum on OS 6.2.1.
                // Ignore the compatibility synchronizer's one-pixel pulse.
                if (size.width > 0 && (kotlin.math.abs(size.width - windowSize.width) > 1 ||
                    kotlin.math.abs(size.height - windowSize.height) > 1)) {
                    windowSize = size
                    measuredDepth = with(density) { size.width.toDp() }
                        .coerceIn(minDepth, maxDepth)
                }
            }) {
                FireSliceScreen(windowSizePx = windowSize)
            }
        }
    }
}
