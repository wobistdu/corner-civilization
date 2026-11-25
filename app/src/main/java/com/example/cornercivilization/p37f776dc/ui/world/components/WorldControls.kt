package com.example.cornercivilization.p37f776dc.ui.world.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cornercivilization.p37f776dc.domain.model.InterventionKind
import com.example.cornercivilization.p37f776dc.domain.model.InvasionPhase
import com.example.cornercivilization.p37f776dc.ui.world.*
import com.example.cornercivilization.p37f776dc.ui.theme.CivilizationColors
import com.pico.spatial.ui.design.*

@Composable
internal fun WorldAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, size = ButtonDefaults.Regular) { Text(label) }
}

/** The one persistent attachment stays 64dp high; status lives in Details. */
@Composable
internal fun WorldToolbar(state: WorldUiState, onEvent: (WorldUiEvent) -> Unit) {
    val visible = state.presentation in setOf(
        WorldPresentation.OBSERVING, WorldPresentation.QUALIFIED, WorldPresentation.SELECTING,
        WorldPresentation.INTERVENTION_FEEDBACK, WorldPresentation.EVOLVING,
        WorldPresentation.WARNING, WorldPresentation.BATTLE, WorldPresentation.VICTORY,
        WorldPresentation.RESET,
    )
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (!visible) return@Row
        when (state.presentation) {
            WorldPresentation.OBSERVING, WorldPresentation.QUALIFIED -> {
                WorldAction("洒水", state.growthActive) { onEvent(WorldUiEvent.SelectTool(InterventionKind.WATER)) }
                WorldAction("点火", state.growthActive) { onEvent(WorldUiEvent.SelectTool(InterventionKind.FIRE)) }
                WorldAction("资源", state.growthActive) { onEvent(WorldUiEvent.SelectTool(InterventionKind.GRANT_RESOURCE)) }
            }
            else -> Unit
        }
        WorldAction(statusButtonLabel(state)) { onEvent(WorldUiEvent.Details) }
        when (state.presentation) {
            WorldPresentation.SELECTING -> WorldAction("收起") { onEvent(WorldUiEvent.CancelTool) }
            WorldPresentation.INTERVENTION_FEEDBACK -> WorldAction("继续观察") { onEvent(WorldUiEvent.Acknowledge) }
            WorldPresentation.EVOLVING -> WorldAction("看看新家") { onEvent(WorldUiEvent.Acknowledge) }
            WorldPresentation.VICTORY -> WorldAction("继续生活") { onEvent(WorldUiEvent.Acknowledge) }
            WorldPresentation.RESET -> WorldAction("重新扎营") { onEvent(WorldUiEvent.Acknowledge) }
            WorldPresentation.WARNING, WorldPresentation.BATTLE -> WorldAction("暂歇") { onEvent(WorldUiEvent.Pause) }
            else -> Unit
        }
    }
}

private fun statusButtonLabel(state: WorldUiState): String = when (state.presentation) {
    WorldPresentation.QUALIFIED -> "待明日"
    WorldPresentation.SELECTING -> "状态"
    WorldPresentation.INTERVENTION_FEEDBACK -> "反馈"
    WorldPresentation.EVOLVING -> "新家"
    WorldPresentation.WARNING -> "预警 ${((state.world?.invasion?.remainingMillis ?: 0) + 999) / 1000} 秒"
    WorldPresentation.BATTLE -> "命中 ${state.world?.invasion?.hits ?: 0}/10 · 剩 ${((state.world?.invasion?.remainingMillis ?: 0) + 999) / 1000} 秒"
    WorldPresentation.VICTORY -> "状态"
    WorldPresentation.RESET -> "状态"
    else -> "成长"
}

@Composable
internal fun WorldDetails(state: WorldUiState) {
    val world = state.world
    val era = world?.era?.ordinal ?: 0
    val effectivePresentation = when (world?.invasion?.phase) {
        InvasionPhase.WARNING -> WorldPresentation.WARNING
        InvasionPhase.FIGHTING -> WorldPresentation.BATTLE
        else -> state.detailsPresentation
    }
    val display = statusText(state.copy(presentation = effectivePresentation))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${eraNames[era]}时代 · ${eraSubtitles[era]}", color = CivilizationColors.Ink, style = PicoTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(display.first, color = CivilizationColors.Ink, style = PicoTheme.typography.titleMedium)
            Text(display.second, color = CivilizationColors.Ink, style = PicoTheme.typography.bodyMedium)
            val invasion = world?.invasion
            val progress = when (effectivePresentation) {
                WorldPresentation.BATTLE -> (invasion?.hits ?: 0) / 10f
                WorldPresentation.WARNING -> 1f - (invasion?.remainingMillis ?: 5000) / 5000f
                else -> (world?.day?.growthMillis ?: 0) / 601000f
            }.coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), colors = LinearProgressDefaults.linearProgressColors(indicatorColor = CivilizationColors.Ink, backgroundColor = CivilizationColors.GrassDark))
        }
    }
}

private val eraNames get() = listOf(WorldCopy.Era0Name, WorldCopy.Era1Name, WorldCopy.Era2Name, WorldCopy.Era3Name, WorldCopy.Era4Name)
private val eraSubtitles get() = listOf(WorldCopy.Era0Subtitle, WorldCopy.Era1Subtitle, WorldCopy.Era2Subtitle, WorldCopy.Era3Subtitle, WorldCopy.Era4Subtitle)
private val activities get() = listOf(WorldCopy.Era0Activity, WorldCopy.Era1Activity, WorldCopy.Era2Activity, WorldCopy.Era3Activity, WorldCopy.Era4Activity)
private fun toolLabel(kind: InterventionKind?) = when (kind) { InterventionKind.WATER -> "洒水"; InterventionKind.FIRE -> "点火"; else -> "赐予资源" }

internal fun statusText(s: WorldUiState): Pair<String, String> {
    val w = s.world
    val seconds = (w?.day?.growthMillis ?: 0) / 1000
    val remaining = ((w?.invasion?.remainingMillis ?: 0) + 999) / 1000
    val hiddenBattle = if (!s.isSighted && w?.invasion != null) " · 飞船倒计时已停住；回到视野后继续。" else ""
    return when (s.presentation) {
        WorldPresentation.FIRST_OPEN -> "小岛还在等第一位居民" to "从一簇火种开始。"
        WorldPresentation.SELECTING -> "${toolLabel(s.selectedTool)} · 指向一块空地" to "点亮的地块是作用范围；收起即可取消。"
        WorldPresentation.INTERVENTION_FEEDBACK -> when(s.lastEffect) {
            InterventionKind.WATER -> "大家开始排队接水" to "潮湿持续，火焰熄灭，农田开始恢复。"
            InterventionKind.FIRE -> "锅盖救援队出动了" to "居民已避难；建筑受损，接下来会重建。"
            else -> "工地今天加餐" to "居民搬运物资，建设进度持续增加。"
        }
        WorldPresentation.QUALIFIED -> "今天的成长已备好" to "升级资格已保存；次日开启时再进入下个时代。"
        WorldPresentation.EVOLVING -> "熟悉的火种，新的时代" to "地标留在原处，居民正在把老家改建成新家。"
        WorldPresentation.WARNING -> "有个不速之客正在靠近" to "预警 $remaining 秒 · 随后点中飞船10次。$hiddenBattle"
        WorldPresentation.BATTLE -> "守住这座小小文明" to "命中 ${w?.invasion?.hits ?: 0} / 10 · 还剩 $remaining 秒$hiddenBattle"
        WorldPresentation.VICTORY -> "守住了！零件也归居民了" to "今日不再入侵，居民从避难处出来庆祝。"
        WorldPresentation.RESET -> "大家已经安全撤离" to "城市归零，原始时代重新扎营；今日可以重新积累成长。"
        WorldPresentation.PAUSED -> "生活停在这一刻" to "恢复后从保存的状态继续。"
        WorldPresentation.RECOVERY_ERROR -> "家园暂时无法恢复" to "原始文件保留，避免覆盖尚可恢复的进度。"
        WorldPresentation.RESTORE_CONFIRM -> "重新开始前，再想一下" to "确认后清除当前文明发展。"
        WorldPresentation.HELP -> "看着它们，就会发生故事" to "日常无需持续操作。"
        WorldPresentation.LOADING -> "正在找回家园" to "请稍候。"
        else -> "安静观察，也可以帮一点小忙" to "${activities[w?.era?.ordinal ?: 0]} · 今日运行 ${seconds / 60}分${seconds % 60}秒 / 超过10分钟"
    }
}
