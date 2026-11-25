package com.example.cornercivilization.p37f776dc.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Revision 6: transparent content-sized strip; native buttons own their surfaces. */
@Composable
internal fun SceneFooterPanel(content: @Composable () -> Unit, onMeasured: (IntSize) -> Unit = {}) {
    Box(Modifier.width(640.dp).onSizeChanged(onMeasured)) { content() }
}
