package com.example.cornercivilization.p37f776dc.ui.fire.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var basketOpen by rememberSaveable { mutableStateOf(false) }
    val basketTools=listOf(FireTool.WOOD,FireTool.DRUM,FireTool.SEED)
    val activeBasketTool=state.tool.takeIf{it in basketTools}
    // spatial-ui: intentional-in-page-overlay existing world-anchored transparent front-edge attachment; preserve container and fit.
    Column(Modifier.fillMaxWidth().height(82.dp).semantics {
        // Nonvisual accessibility equivalent of the enlarged hearth target; same domain event.
        customActions = listOf(CustomAccessibilityAction("向火塘施放已选工具") {
            if (enabled && state.tool != FireTool.OBSERVE) { castAtHearth(); true } else false
        })
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(start=196.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FireToolbarPresentation.tools.forEach { (tool, label) ->
            if (tool==FireTool.WOOD) {
                ToggleIconButton(checked=basketOpen, onCheckedChange={basketOpen=it}, enabled=enabled,
                    size=IconButtonDefaults.Regular, modifier=Modifier.size(56.dp).semantics {
                        stateDescription=if(basketOpen) "已展开" else "已收起"
                    }) {
                    Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){
                        Image(painterResource(R.drawable.tool_basket),contentDescription="工具篮",modifier=Modifier.size(36.dp))
                        if(!basketOpen && activeBasketTool!=null){
                            val badge=when(activeBasketTool){FireTool.DRUM->R.drawable.tool_drum;FireTool.SEED->R.drawable.tool_seed;else->R.drawable.tool_wood}
                            Image(painterResource(badge),contentDescription=null,modifier=Modifier.size(18.dp).align(Alignment.BottomEnd))
                        }
                    }
                }
            }
            if (tool in basketTools && !basketOpen) return@forEach
            val icon = when(tool) {
                FireTool.OBSERVE -> R.drawable.tool_scroll
                FireTool.WATER -> R.drawable.tool_rain
                FireTool.FIRE -> R.drawable.tool_fire
                FireTool.DRUM -> R.drawable.tool_drum
                FireTool.SEED -> R.drawable.tool_seed
                FireTool.WOOD -> R.drawable.tool_wood
            }
            val isScroll = tool == FireTool.OBSERVE
            val checked = if (isScroll) scrollOpen else state.tool == tool
            ToggleIconButton(checked = checked, onCheckedChange = { if (isScroll) toggleScroll() else select(tool) },
                size = IconButtonDefaults.Regular, enabled = enabled,
                modifier = Modifier.size(56.dp).semantics { stateDescription = if (isScroll) { if (checked) "已展开" else "已收起" } else if (checked) "已选" else "未选" }) {
                Image(painterResource(icon), contentDescription = label, modifier = Modifier.size(36.dp))
            }
        }
        }
        Box(Modifier.fillMaxWidth().height(24.dp),contentAlignment=Alignment.Center) {
            state.firstToolHint?.let { Text(FireToolbarPresentation.firstHint(it), fontSize=14.sp, maxLines=1) }
        }
    }
}
