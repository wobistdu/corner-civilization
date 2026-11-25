package com.example.cornercivilization.p37f776dc.ui.fire.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cornercivilization.p37f776dc.domain.usecase.VillageChronicle
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text

// ARGB pigments of the illustrated paper and wooden rollers.
private object ScrollPigment {
    const val INK = 0xFF36281E
    const val SEAL = 0xFF903C2A
    const val MOSS = 0xFF495838
    const val PAPER_LEFT = 0xFFCBA471
    const val PAPER_WARM = 0xFFF3E5C6
    const val PAPER_LIGHT = 0xFFF5EAD1
    const val PAPER_RIGHT = 0xFFD2B185
    const val ROLLER_LIGHT = 0xFFB88D50
    const val ROLLER_DARK = 0xFF78502D
}

private const val LINES_PER_PAGE = 5

@Composable
internal fun VillageScroll(lines: List<VillageChronicle.Line>, open: Boolean) {
    Box(Modifier.size(790.dp, 292.dp)) {
        if (!open) return@Box
        // Pigments of the illustrated scroll, independent of the native control theme.
        val ink = Color(ScrollPigment.INK)
        val seal = Color(ScrollPigment.SEAL)
        val moss = Color(ScrollPigment.MOSS)
        val parchment = listOf(Color(ScrollPigment.PAPER_LEFT), Color(ScrollPigment.PAPER_WARM),
            Color(ScrollPigment.PAPER_LIGHT), Color(ScrollPigment.PAPER_RIGHT))
        val roller = listOf(ink, Color(ScrollPigment.ROLLER_LIGHT), Color(ScrollPigment.ROLLER_DARK), ink)
        var archive by remember { mutableStateOf<List<VillageChronicle.Line>?>(null) }
        var page by remember { mutableIntStateOf(0) }
        val entries = archive ?: lines
        val pages = ((entries.size + LINES_PER_PAGE - 1) / LINES_PER_PAGE).coerceAtLeast(1)
        Canvas(Modifier.fillMaxSize()) {
            val rail = 18.dp.toPx()
            val rim = 8.dp.toPx()
            drawRect(Brush.horizontalGradient(parchment), Offset(rail, rim), Size(size.width - 2*rail, size.height - 2*rim))
            for (i in 0..75) {
                val x = rail + (size.width - 2*rail)*i/75f
                drawLine(ink.copy(alpha=.035f), Offset(x, rim), Offset(x+1f, size.height-rim), 1f)
            }
            for (x in listOf(0f, size.width-rail*1.6f)) {
                drawRect(Brush.horizontalGradient(roller, x, x+rail*1.6f),
                    Offset(x,0f),Size(rail*1.6f,size.height))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal=48.dp, vertical=14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("村落见闻", color=ink, fontFamily=FontFamily.Serif, fontSize=28.sp)
                Spacer(Modifier.weight(1f))
                Text(if (archive == null) "新近所记" else "往日所记", color=moss, fontSize=18.sp)
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha=.4f)))
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.Center) {
                if (entries.isEmpty()) Text("墨尚未干，且候村中第一桩事。", color=ink, fontSize=18.sp)
                entries.asReversed().drop(page * LINES_PER_PAGE).take(LINES_PER_PAGE).forEach { line ->
                    Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text(if (line.player) "天意" else "村闻", modifier=Modifier.width(58.dp),
                            color=if(line.player) seal else moss, fontFamily=FontFamily.Serif, fontSize=17.sp)
                        Text(line.text, color=ink, fontSize=18.sp, maxLines=1, modifier=Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("第 ${page + 1} / ${pages} 页", color=ink, fontSize=18.sp, modifier=Modifier.weight(1f))
                Button(enabled=page>0, onClick={ page--; if(page==0) archive=null }) { Text("向新") }
                Button(enabled=page+1<pages, onClick={ if(archive==null) archive=entries; page++ }) { Text("向旧") }
            }
        }
    }
}
