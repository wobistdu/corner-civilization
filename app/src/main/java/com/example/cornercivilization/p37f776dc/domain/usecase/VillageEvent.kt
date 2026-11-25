package com.example.cornercivilization.p37f776dc.domain.usecase

/** Stable save identifiers. Each entry is emitted by a rule that changes the world. */
internal enum class VillageEvent(val title: String) {
    EAT_STOCK("取粮吃饭"), RATION_MEAL("缺粮时小份用餐"), KEEP_LAST_MEAL("为更饿的人留饭"),
    DRINK_RIVER("到河岸饮水"), DRINK_JAR("饮用公共储水"), REST_SHADE("树荫小憩"),
    SLEEP_HOME("回家补眠"), SLEEP_LONGHOUSE("长屋收留疲惫居民"), DRY_CLOTHES("屋下晾干"),
    WARM_AT_HEARTH("火旁取暖"), STOP_EXHAUSTED("疲惫时放下工作"), RESUME_RECOVERED("恢复后续做"),

    FORAGE_BERRIES("采有限野果"), PICK_SEEDHEADS("收集草籽"), CUT_RIPE_CROP("割下熟粮"),
    DRY_WET_GRAIN("摊晒湿粮"), PRESERVE_GRAIN("熏干粮食"), COMPOST_SPOILED("腐粮还田"),
    TURN_STOCK("翻动粮堆防霉"), RESCUE_STOCK("从火边转移粮食"), STORE_GRANARY("搬粮入粮棚"),
    AGREE_RATIONS("商议节粮"), COOK_TOGETHER("共同备餐"), RESERVE_SEEDS("留下播种粮"),

    FILL_JAR("取水补满公共罐"), SHARE_WATER("给缺水邻居送水"), WATER_FIELD("提水浇田"),
    WASH_MUD("河边洗去泥污"), CATCH_RAIN("在安全雨沿接水"), SPILL_WATER("摔洒水后重取"),
    DAMP_ROOF("润湿棚顶防火"), COOL_ASH("浇冷余烬"), WATER_SEEDLINGS("照料新苗"),
    CLEAR_BANK("清理取水落枝"), ALTERNATE_BANK("绕到另一处取水点"), ESCORT_WATER("结伴取水"),

    SOW_SEEDS("用留种补播"), PULL_WEEDS("拔除争水杂草"), MULCH_FIELD("用草覆盖旱土"),
    LOOSEN_SOIL("松开板结田土"), DRAIN_FIELD("疏通积水田沟"), STAKE_CROP("扶起踩倒作物"),
    PICK_PESTS("捉除田间虫害"), REPLANT_GAP("补种空缺田行"), CHECK_FENCE("巡查围栏缺口"),
    MEND_FENCE("搬材修补围栏"), REST_SOIL("收割后轮休田土"), SAVE_OVERWATERED("救护过湿根部"),

    PATCH_ROOF("补漏棚顶"), BRACE_HOUSE("加固受象撞的棚柱"), CLEAR_TRAIL("清走路上落枝"),
    FEED_HEARTH("搬柴续公共火塘"), COVER_WOOD("为木堆盖草"), DRY_WOOD("翻晒湿木"),
    SALVAGE_TIMBER("从残棚回收可用木"), MAKE_TOOL("用木料制劳动工具"), MEND_TOOL("修复磨损工具"),
    BUILD_TOGETHER("两人配合施工"), INSPECT_HOME("检查住家隐患"), RETURN_HOST_SPACE("搬回家让出借宿处"),

    SHARE_MEAL("给饥饿邻居送饭"), COMFORT_FEAR("走近安慰受惊邻居"), THANK_HELPER("当面感谢帮助"),
    REPAY_HELP("给帮助过自己的人送饭"), TEACH_FARM("田边传授照料经验"), TEACH_BUILD("工地传授搭建经验"),
    RELAY_WARNING("面对面传递兽情"), VISIT_DISPLACED("探望暂住邻居"), INVITE_MEAL("邀请孤独邻居同食"),
    HARVEST_TOGETHER("搭伴收割"), WELCOME_GUEST("屋下接纳无家邻居"), KEEP_COMPANY("陪伴疲惫邻居"),

    RECALL_WORKER("叫回危险边缘劳动者"), SHELTER_FROM_RAIN("雨沿走回完整棚"),
    ESCORT_AFRAID("引导受惊邻居到安全处"), GUIDE_CARRIER("为搬运者让出通道"),
    WAIT_ROUTE("路被危险阻断时就地等待"), RECOVER_CARGO("记得并取回中断粮束"),
    MOVE_GRAIN_DRY("将雨淋粮束搬到干地"), WATCH_TIGER_PATH("站岗留意虎的来路"),
    RELIEVE_WATCH("接替疲惫看守"), REUNITE_HOME("安全后带邻居返回住家"),
    MARK_SAFE_TRAIL("走通并记住绕行路"), REOPEN_WORK("危险解除后恢复工地"),

    BAIT_ELEPHANT("用割草引象离村"), LEAD_CALF_WATER("成象带幼象找水"),
    SHARE_ELEPHANT_WATER("象群让幼象先饮水"), CALF_REJOIN("离散幼象回到成象身边"),
    ELEPHANT_MUD("成象在湿地泥浴"), ELEPHANT_SHADE("成象饱食后到树荫休息"),
    TIGER_DRINK("饥虎暂缓追逐去饮水"), TIGER_AVOID_FENCE("虎绕开完整围栏"),
    TIGER_YIELD("休息虎给觅食虎让路"), RETURN_CARCASS_SOIL("进食残留滋养附近草地"),
    ELEPHANT_AVOID_GUARD("象绕开结伴看守"), CALF_REST_PROTECTED("幼象在成象身旁恢复"),

    COLLECT_BRANCH("拾起新落枝"), REST_FORAGE_PATCH("采尽后让野果地恢复"),
    CUT_REEDS("采河边芦苇盖木堆"), BERRIES_REGROW("休采野果重新结实"),
    FLOOD_TRAIL("雨水冲积阻塞小路"), CLEAN_MUD_TRAIL("踩过泥路后整平"),
    CROP_TRAMPLED("真实象足踩倒田苗"), MEADOW_RECOVER("放牧后草地恢复"),
    COMPOST_SCAR("把腐粮撒到烧痕处"), CLEAR_SILT("清除田沟淤积"),
    BIRDS_EAT_SPILL("鸟落地吃遗落粮食"), KEEP_GRASS_REFUGE("留下村外草地分散兽压"),

    GREET_NEIGHBOR("遇见邻居挥手招呼"), CHAT_TOGETHER("歇工时面对面聊一会儿"),
    STRETCH_BREAK("劳动间隙伸展腰背"), EAT_FORAGE("在安全处吃采下的野食");

    val group: Int get() = if(this==EAT_FORAGE)0 else ordinal / 12
}

internal class VillageJournal {
    data class Entry(val event: VillageEvent, val subject: String, val at: Float, val result: String)
    val counts = linkedMapOf<VillageEvent, Int>()
    val recent = ArrayDeque<Entry>()
    private val last = mutableMapOf<String, Float>()
    fun record(event: VillageEvent, subject: String, now: Float, result: String, cooldown: Float = 3f): Boolean {
        val key = "${event.name}:$subject"
        if (now - (last[key] ?: -10000f) < cooldown) return false
        last[key] = now
        counts[event] = (counts[event] ?: 0) + 1
        recent.addLast(Entry(event, subject, now, result))
        while (recent.size > 96) recent.removeFirst()
        if (last.size > 384) last.entries.removeAll { now - it.value > 120f }
        return true
    }
    fun restore(savedCounts:Map<VillageEvent,Int>,savedRecent:List<Entry>) {
        counts.clear();counts.putAll(savedCounts);recent.clear();last.clear()
        savedRecent.takeLast(96).forEach { e->recent.addLast(e);last["${e.event.name}:${e.subject}"]=e.at }
    }
}
