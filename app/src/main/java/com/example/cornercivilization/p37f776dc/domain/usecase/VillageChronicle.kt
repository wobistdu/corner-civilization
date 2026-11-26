package com.example.cornercivilization.p37f776dc.domain.usecase

/** One ordered page stream for the committed outcomes of the simulation and player gestures. */
internal class VillageChronicle {
    data class Line(val time: Int, val player: Boolean, val text: String)
    private val recentNews = ArrayDeque<String>(5)
    private val lines = ArrayDeque<Line>()
    var revision = 0L
        private set

    fun append(time: Float, text: String, player: Boolean = false) {
        if (!player) {
            // Old saves contain rendered sentences. Ignore resident names so a different
            // villager doing the same thing does not fill the page with the same story.
            val resident = residentNames.firstOrNull { text.startsWith(it) }
            val story = if (resident == null) text else text.removePrefix(resident)
            if (story in recentNews) return
            if (recentNews.size == 5) recentNews.removeFirst()
            recentNews.addLast(story)
        }
        if (lines.size == 96) lines.removeFirst()
        lines.addLast(Line(time.toInt().coerceAtLeast(0), player, text))
        revision++
    }

    fun snapshot() = lines.toList()

    fun villageEvent(event: VillageEvent, subject: String, time: Float) {
        val name = when {
            subject.startsWith("PrimitiveResident_") || subject.startsWith("Resident_") -> residentNames
                .getOrNull(subject.substringAfterLast('_').toIntOrNull() ?: -1).orEmpty()
            else -> ""
        }
        val scene = when (event) {
            VillageEvent.CROP_TRAMPLED -> "猛犸踏进田垄，一排青苗伏倒了。"
            VillageEvent.WATCH_TIGER_PATH -> "守在小路旁，望着剑齿虎来时的方向。"
            VillageEvent.BERRIES_REGROW -> "歇采的野果地又结了一茬果子。"
            VillageEvent.SPILL_WATER -> "躲闪时洒了水，只好再走一趟。"
            VillageEvent.RECOVER_CARGO -> "循原路找回了先前落下的粮束。"
            VillageEvent.REST_SHADE -> "在树影里坐下，歇了歇脚。"
            VillageEvent.COLLECT_BRANCH -> "拾起一根刚落下的树枝。"
            VillageEvent.RETURN_CARCASS_SOIL -> "猎手进食后，残骸归土，周围的草又长起来了。"
            VillageEvent.EAT_STOCK -> "从粮堆里取了一份口粮。"
            VillageEvent.RATION_MEAL -> "掰开口粮，今天只吃了一小份。"
            VillageEvent.KEEP_LAST_MEAL -> "把最后一份饭留给更饿的同伴。"
            VillageEvent.DRINK_RIVER -> "蹲在河岸，捧起水喝了几口。"
            VillageEvent.DRINK_JAR -> "从公用陶罐里舀水解了渴。"
            VillageEvent.SLEEP_HOME -> "在棚檐下坐了一会儿，歇足了才起身。"
            VillageEvent.SLEEP_LONGHOUSE -> "在长屋里找到了一处安睡的地方。"
            VillageEvent.DRY_CLOTHES -> "躲到屋檐下，晾起湿衣。"
            VillageEvent.WARM_AT_HEARTH -> "挨近火塘，暖了暖身子。"
            VillageEvent.STOP_EXHAUSTED -> "放下手里的活，停下来喘口气。"
            VillageEvent.RESUME_RECOVERED -> "歇足了，又回去接着干活。"
            VillageEvent.FORAGE_BERRIES -> "拨开叶子，摘下一捧野果。"
            VillageEvent.PICK_SEEDHEADS -> "沿着草丛，收了些饱满的草籽。"
            VillageEvent.CUT_RIPE_CROP -> "走进田里，割下一束熟粮。"
            VillageEvent.DRY_WET_GRAIN -> "把淋湿的粮摊在干地上。"
            VillageEvent.PRESERVE_GRAIN -> "守着炊烟，把粮食慢慢熏干。"
            VillageEvent.COMPOST_SPOILED -> "把坏粮埋回了田里。"
            VillageEvent.TURN_STOCK -> "翻了翻粮堆，让底下的粮也透透气。"
            VillageEvent.RESCUE_STOCK -> "抢在火舌前，把粮食移到了安全处。"
            VillageEvent.STORE_GRANARY -> "把一份粮搬进了粮棚。"
            VillageEvent.AGREE_RATIONS -> "和邻人商量，从今天起省着吃粮。"
            VillageEvent.COOK_TOGETHER -> "和同伴凑在一处，动手备饭。"
            VillageEvent.RESERVE_SEEDS -> "单独留出一份粮，等着来日播种。"
            VillageEvent.FILL_JAR -> "提水回来，添进村里的陶罐。"
            VillageEvent.SHARE_WATER -> "给干渴的邻人送去一口水。"
            VillageEvent.WATER_FIELD -> "把提来的水浇进了田垄。"
            VillageEvent.WASH_MUD -> "在河边洗掉了身上的泥。"
            VillageEvent.CATCH_RAIN -> "在雨幕边缘接了些清水。"
            VillageEvent.DAMP_ROOF -> "把水泼上棚顶，防着火星。"
            VillageEvent.COOL_ASH -> "将冷水浇在了尚热的余烬上。"
            VillageEvent.WATER_SEEDLINGS -> "俯身浇了浇刚冒头的幼苗。"
            VillageEvent.CLEAR_BANK -> "拾走落枝，清出了河边的取水处。"
            VillageEvent.ALTERNATE_BANK -> "绕过难走的河岸，换了一处取水。"
            VillageEvent.ESCORT_WATER -> "和同伴结伴走到了水边。"
            VillageEvent.SOW_SEEDS -> "取出留好的种粮，又播下一行。"
            VillageEvent.PULL_WEEDS -> "弯下腰，拔掉了作物旁的杂草。"
            VillageEvent.MULCH_FIELD -> "用一层草盖住正发干的泥土。"
            VillageEvent.LOOSEN_SOIL -> "把晒得发硬的田土松开了。"
            VillageEvent.DRAIN_FIELD -> "疏通田沟，积水顺着沟流走了。"
            VillageEvent.STAKE_CROP -> "把被踩倒的禾苗一棵棵扶起来。"
            VillageEvent.PICK_PESTS -> "在叶间捉去了啃苗的小虫。"
            VillageEvent.REPLANT_GAP -> "沿着稀疏的田行，补上了缺苗。"
            VillageEvent.CHECK_FENCE -> "绕田走了一圈，查看围栏的缺口。"
            VillageEvent.MEND_FENCE -> "搬来木料，补好了围栏。"
            VillageEvent.REST_SOIL -> "收割后留下这垄田，让地歇一歇。"
            VillageEvent.SAVE_OVERWATERED -> "拨开湿泥，救护泡水的苗根。"
            VillageEvent.PATCH_ROOF -> "塞紧了草棚上漏雨的缝。"
            VillageEvent.BRACE_HOUSE -> "顶住被象撞歪的棚柱，重新扎牢。"
            VillageEvent.CLEAR_TRAIL -> "将路上的断枝拖到一旁。"
            VillageEvent.FEED_HEARTH -> "抱来一束柴，给火塘续上火。"
            VillageEvent.COVER_WOOD -> "给材堆盖了一层避雨的草。"
            VillageEvent.DRY_WOOD -> "把湿柴翻了个面，摊开晾晒。"
            VillageEvent.SALVAGE_TIMBER -> "从倒塌的棚架中捡回可用的木头。"
            VillageEvent.MAKE_TOOL -> "削了块木料，做成一件工具。"
            VillageEvent.MEND_TOOL -> "坐下来修好了磨损的工具。"
            VillageEvent.BUILD_TOGETHER -> "和搭手的同伴一同去工地帮忙。"
            VillageEvent.INSPECT_HOME -> "绕着住处检查了一遍。"
            VillageEvent.RETURN_HOST_SPACE -> "搬回自己的棚屋，让出了借宿的地方。"
            VillageEvent.SHARE_MEAL -> "把一份饭送给了挨饿的邻人。"
            VillageEvent.COMFORT_FEAR -> "走近受惊的邻人，陪在身边安慰。"
            VillageEvent.THANK_HELPER -> "找到出过力的同伴，当面道了谢。"
            VillageEvent.REPAY_HELP -> "给帮过自己的人送去了一份饭。"
            VillageEvent.TEACH_FARM -> "在田边把种地的经验教给同伴。"
            VillageEvent.TEACH_BUILD -> "在工地上手把手地教人搭棚。"
            VillageEvent.RELAY_WARNING -> "赶到邻人身边，捎去了野兽的消息。"
            VillageEvent.VISIT_DISPLACED -> "去看望了暂住别处的邻人。"
            VillageEvent.INVITE_MEAL -> "邀了独处的邻人过来一起吃饭。"
            VillageEvent.HARVEST_TOGETHER -> "和同伴并肩，收起了熟粮。"
            VillageEvent.WELCOME_GUEST -> "腾出棚下的位置，接纳了无家可归的邻人。"
            VillageEvent.KEEP_COMPANY -> "留在疲惫的邻人身旁，陪了一阵。"
            VillageEvent.RECALL_WORKER -> "把还在危险处干活的人叫了回来。"
            VillageEvent.SHELTER_FROM_RAIN -> "从雨幕边缘快步走到棚下避雨。"
            VillageEvent.ESCORT_AFRAID -> "领着受惊的同伴走到了安全处。"
            VillageEvent.GUIDE_CARRIER -> "让开小路，好让搬运的人先过。"
            VillageEvent.WAIT_ROUTE -> "见前路危险，停下来等候时机。"
            VillageEvent.MOVE_GRAIN_DRY -> "把淋雨的粮束挪向了干地。"
            VillageEvent.RELIEVE_WATCH -> "走到岗哨旁，换下疲惫的看守。"
            VillageEvent.REUNITE_HOME -> "待四周平静，领着邻人回了家。"
            VillageEvent.MARK_SAFE_TRAIL -> "绕过险处，记下了一条走得通的小路。"
            VillageEvent.REOPEN_WORK -> "见危险已过，又回到工地开工。"
            VillageEvent.BAIT_ELEPHANT -> "拿割下的草引着猛犸离开了村子。"
            VillageEvent.LEAD_CALF_WATER -> "成象领着幼象去找水喝。"
            VillageEvent.SHARE_ELEPHANT_WATER -> "象群停在岸边，让幼象先喝。"
            VillageEvent.CALF_REJOIN -> "离群的幼象又跟上了成象。"
            VillageEvent.ELEPHANT_MUD -> "猛犸走进湿地，舒舒服服地洗了个泥浴。"
            VillageEvent.ELEPHANT_SHADE -> "吃饱的猛犸走到树荫下歇脚。"
            VillageEvent.TIGER_DRINK -> "剑齿虎停下追逐，到岸边低头喝水。"
            VillageEvent.TIGER_AVOID_FENCE -> "剑齿虎沿着完好的围栏绕了过去。"
            VillageEvent.TIGER_YIELD -> "歇息的剑齿虎退了退，让同类通过。"
            VillageEvent.ELEPHANT_AVOID_GUARD -> "猛犸望见结伴的守卫，从一旁绕开。"
            VillageEvent.CALF_REST_PROTECTED -> "幼象倚着成象安静地歇了下来。"
            VillageEvent.REST_FORAGE_PATCH -> "野果摘尽了，采集的人暂时离开了这片果地。"
            VillageEvent.CUT_REEDS -> "割下岸边的芦苇，拿去遮盖木堆。"
            VillageEvent.FLOOD_TRAIL -> "雨水冲来一层泥沙，小路被堵住了。"
            VillageEvent.CLEAN_MUD_TRAIL -> "走过泥泞的小路，顺手将它整平。"
            VillageEvent.MEADOW_RECOVER -> "被啃过的草场又泛起了新绿。"
            VillageEvent.COMPOST_SCAR -> "把腐粮撒在火后留下的焦土上。"
            VillageEvent.CLEAR_SILT -> "挖开了堵在田沟里的淤泥。"
            VillageEvent.BIRDS_EAT_SPILL -> "几只鸟落下来，啄走了地上散落的粮。"
            VillageEvent.KEEP_GRASS_REFUGE -> "在村外留下一片草地，让野兽分散觅食。"
            VillageEvent.GREET_NEIGHBOR -> "遇见熟人，扬手打了个招呼。"
            VillageEvent.CHAT_TOGETHER -> "趁歇工的空当，和邻人聊了一会儿。"
            VillageEvent.STRETCH_BREAK -> "停了停手里的活，长长地伸了个懒腰。"
            VillageEvent.MOULD_CLAY -> "把湿泥盘的缸坯捧回村；柴堆里有一捆干柴，就能开烧。"
            VillageEvent.FIRE_CISTERN -> "用一捆干柴烧好了陶缸，下回落雨就能存水了。"
            VillageEvent.WEAVE_FISH_TRAP -> "割了岸边的芦苇，编成鱼篓下在浅水边。"
            VillageEvent.LIFT_FISH_TRAP -> "提起沉甸甸的鱼篓，把鲜鱼留在岸边等人搬运。"
            VillageEvent.EAT_FORAGE -> "走到安全处，吃下了刚采的野食。"
        }
        append(time, name + scene)
    }

    companion object {
        private val residentNames = listOf("阿禾", "石生", "小满", "青叶", "阿岚", "松子", "陶伯", "阿桑", "榛子")
        fun nameOf(id:String)=residentNames.getOrNull(id.substringAfterLast('_').toIntOrNull() ?: -1) ?: "村民"
        fun place(p: LocalInterventionWorld.Point): String {
            val side = when { p.x < -.14f -> "左侧"; p.x > .14f -> "右侧"; else -> "中央" }
            val depth = when { p.z < -.10f -> "后方"; p.z > .10f -> "前方"; else -> "" }
            return side + depth
        }
        fun house(id: String) = when (id) {
            "RainShelter" -> "遮雨棚"
            "Granary" -> "粮棚"
            "FieldFence" -> "田边围栏"
            "Longhouse" -> "长屋"
            else -> "草棚"
        }
    }
}
