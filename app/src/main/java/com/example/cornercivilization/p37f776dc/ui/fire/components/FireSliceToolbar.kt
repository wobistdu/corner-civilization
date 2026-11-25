package com.example.cornercivilization.p37f776dc.ui.fire.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import com.example.cornercivilization.p37f776dc.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.ui.fire.FireSliceUiState
import com.pico.spatial.ui.design.*

@Composable
fun FireSliceToolbar(state: FireSliceUiState, enabled: Boolean, scrollOpen: Boolean, toggleScroll: () -> Unit, castAtHearth: () -> Unit = {}, select: (FireTool) -> Unit) {
    // spatial-ui: intentional-in-page-overlay existing world-anchored transparent front-edge attachment; preserve container and fit.
    Column(Modifier.fillMaxWidth().height(64.dp).semantics {
        // Nonvisual accessibility equivalent of the enlarged hearth target; same domain event.
        customActions = listOf(CustomAccessibilityAction("向火塘施放已选工具") {
            if (enabled && state.tool != FireTool.OBSERVE) { castAtHearth(); true } else false
        })
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        FireToolbarPresentation.tools.forEach { (tool, label) ->
            val icon = when(tool) {
                FireTool.OBSERVE -> R.drawable.tool_scroll
                FireTool.WATER -> R.drawable.tool_rain
                FireTool.FIRE -> R.drawable.tool_fire
                FireTool.WOOD -> R.drawable.tool_wood
            }
            val isScroll = tool == FireTool.OBSERVE
            val checked = if (isScroll) scrollOpen else state.tool == tool
            ToggleIconButton(checked = checked, onCheckedChange = { if (isScroll) toggleScroll() else select(tool) },
                size = IconButtonDefaults.Regular, enabled = enabled,
                modifier = Modifier.semantics { stateDescription = if (isScroll) { if (checked) "已展开" else "已收起" } else if (checked) "已选" else "未选" }) {
                Image(painterResource(icon), contentDescription = label, modifier = Modifier.size(36.dp))
            }
        }
        }
        state.firstToolHint?.let { Text(FireToolbarPresentation.firstHint(it), fontSize=12.sp, maxLines=1) }
    }
}
