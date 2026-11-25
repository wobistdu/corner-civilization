package com.example.cornercivilization.p37f776dc.scene

import com.pico.spatial.core.ecs.Entity

/** Verified SGS originals. Not instantiated by the live scene: rigging and visual
 * acceptance must produce separate runtime derivatives before integration. */
internal object ResidentVarietySources {
    const val ELDER = "sgs/eeadb48f47819ef99ac50876aef579ee5d4cfd69b0ae6d7221138e519015b043.glb"
    const val CURLY = "sgs/6c879e15b199385c73ad12137f5faf68a09ee6078f41c0db116929233bc78c30.glb"

    suspend fun loadElder(): Entity = Entity.loadSuspend("asset://$ELDER").apply {
        setName("ElderResident")
    }

    suspend fun loadCurly(): Entity = Entity.loadSuspend("asset://$CURLY").apply {
        setName("CurlyResident")
    }
}
