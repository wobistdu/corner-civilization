package com.example.cornercivilization.p37f776dc.ui.fire.components

import com.example.cornercivilization.p37f776dc.domain.model.*

/** Presentation only: no clock, resource allocation or task dispatch here. */
internal object FireToolbarPresentation {
    val tools = listOf(FireTool.OBSERVE to "村落见闻", FireTool.WATER to "下雨", FireTool.FIRE to "点火", FireTool.WOOD to "木头")

    fun firstHint(tool: FireTool): String = when (tool) {
        FireTool.OBSERVE -> "翻开画卷，读村中近事"
        FireTool.WATER -> "捏住拖动雨云，松手让附近下雨"
        FireTool.FIRE -> "点地面或房子，让附近起火"
        FireTool.WOOD -> "点地面放木料，他们会自己搬回"
    }

    fun hint(world: FireSliceState?, tool: FireTool, enabled: Boolean): String = when {
        !enabled || world == null -> "营地正在准备"
        tool != FireTool.OBSERVE -> firstHint(tool)
        world.phase == FirePhase.WET -> "火熄了 · 他们会保留知识并重燃"
        world.shelter == ShelterPhase.COMPLETE && world.resourcePhase == ResourcePhase.CARRYING -> "两人正在搬木料 · 到库才算交付"
        world.shelter == ShelterPhase.COMPLETE && world.resourcePhase == ResourcePhase.FETCHING -> "他们正在取木料"
        world.agricultural -> "第一块田留下了 · 他们继续照料"
        world.shelter == ShelterPhase.COMPLETE -> when {
            world.activeMillis - world.lastWaterAt in 0..4_000 -> "棚挡住了雨 · 火还在烧"
            world.storedWood > 0 -> "木料搬回来了 · 营地继续生活"
            else -> "棚搭好了 · 点地面影响他们的生活"
        }
        world.shelter == ShelterPhase.BUILDING -> "正在绑扎棚顶"
        world.shelter == ShelterPhase.CARRYING -> "他们把木料搬向火塘"
        world.shelter == ShelterPhase.FETCHING -> "他们决定搭棚，去取木料了"
        world.shelter == ShelterPhase.NEEDED -> "雨让他们想到了遮雨棚"
        world.phase == FirePhase.QUIET -> "他们正在寻找火种"
        world.phase == FirePhase.APPROACH -> "有人走向火塘"
        world.phase == FirePhase.KINDLING -> "他们在尝试生火"
        else -> "他们继续生活 · 选工具，再点地面"
    }
}
