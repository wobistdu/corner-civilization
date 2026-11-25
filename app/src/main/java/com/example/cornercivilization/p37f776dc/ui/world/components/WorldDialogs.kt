package com.example.cornercivilization.p37f776dc.ui.world.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.cornercivilization.p37f776dc.ui.world.*
import com.example.cornercivilization.p37f776dc.ui.theme.CivilizationColors
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog

@Composable
internal fun WorldDialogs(state: WorldUiState, onEvent: (WorldUiEvent) -> Unit) {
    val copy = when (state.presentation) {
        WorldPresentation.DETAILS -> "这座小世界" to ""
        WorldPresentation.FIRST_OPEN -> WorldCopy.FirstDialogTitle to WorldCopy.FirstDialogMessage
        WorldPresentation.PAUSED -> WorldCopy.PauseDialogTitle to WorldCopy.PauseDialogMessage
        WorldPresentation.HELP -> WorldCopy.HelpDialogTitle to WorldCopy.HelpDialogMessage
        WorldPresentation.RECOVERY_ERROR -> if (state.saveFailed) "家园暂时没能保存" to "先保留上一次有效存档。重试保存后从原处继续，未完成入侵不会清除。" else WorldCopy.ErrorDialogTitle to WorldCopy.ErrorDialogMessage
        WorldPresentation.RESTORE_CONFIRM -> WorldCopy.ConfirmDialogTitle to WorldCopy.ConfirmDialogMessage
        else -> return
    }
    AlertDialog(onDismissRequest = {}, title = { Text(copy.first, color = CivilizationColors.Ink) }, content = {
        if (state.presentation == WorldPresentation.DETAILS) WorldDetails(state)
        else Text(copy.second, color = CivilizationColors.Ink)
    }, buttons = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.presentation) {
                WorldPresentation.DETAILS -> {
                    WorldAction("玩法") { onEvent(WorldUiEvent.Help) }
                    WorldAction("暂歇") { onEvent(WorldUiEvent.Pause) }
                    WorldAction("回到沙盘") { onEvent(WorldUiEvent.CloseDetails) }
                }
                WorldPresentation.FIRST_OPEN -> WorldAction("让文明开始") { onEvent(WorldUiEvent.Start) }
                WorldPresentation.PAUSED -> WorldAction("继续") { onEvent(WorldUiEvent.Resume) }
                WorldPresentation.HELP -> WorldAction("知道了") { onEvent(WorldUiEvent.CloseHelp) }
                WorldPresentation.RECOVERY_ERROR -> {
                    WorldAction(if (state.saveFailed) "重试保存" else "重试读取") { onEvent(WorldUiEvent.Retry) }
                    WorldAction("重新开始", state.canRebuild && state.world?.invasion == null) { onEvent(WorldUiEvent.AskReset) }
                }
                WorldPresentation.RESTORE_CONFIRM -> {
                    WorldAction("保留家园") { onEvent(WorldUiEvent.CancelReset) }
                    WorldAction("确认重建", state.canRebuild && state.world?.invasion == null) { onEvent(WorldUiEvent.ConfirmReset) }
                }
                else -> Unit
            }
        }
    })
}
