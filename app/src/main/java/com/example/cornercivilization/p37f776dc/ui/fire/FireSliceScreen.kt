package com.example.cornercivilization.p37f776dc.ui.fire

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cornercivilization.p37f776dc.scene.PrimitivePreviewScene

@Composable
fun FireSliceScreen(windowSizePx: IntSize) {
    val context = LocalContext.current
    val vm: FireSliceViewModel = viewModel(factory = remember { FireSliceViewModelFactory(context.filesDir) })
    val state by vm.state.collectAsStateWithLifecycle()
    FireSliceContent(windowSizePx, state, vm::onEvent)
}

@Composable
internal fun FireSliceContent(windowSizePx: IntSize, state: FireSliceUiState, onEvent: (FireSliceEvent) -> Unit) {
    PrimitivePreviewScene(windowSizePx, state, onEvent)
}
