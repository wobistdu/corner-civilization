package com.example.cornercivilization.p37f776dc.scene

import com.pico.spatial.core.ecs.Entity

/** Official SGS originals. Runtime derivatives require separate visual/rig validation. */
internal object PrehistoricSourcesV17 {
    const val ELDER = "sgs/9f93be68e4ed2f3f93582e19df3aef0ad0fdbfac1d8cf32cdc1454505874e67a.glb"
    const val GATHERER = "sgs/5a834f3c95ec5952c7e5a8149ce5a4958db0855f3c420e00e8f53b3268f9f221.glb"
    const val FOOD_BASKET = "sgs/ab113b7c9b859202e0549a39d05d7d8e1e811ed735791c38203f282625ce9f77.glb"
    const val WATER_VESSEL = "sgs/37027e1bff4d6f4a5cf7865e4921e87dfffb739895e95b06d5b28740b3c1d9d5.glb"

    suspend fun loadElder(): Entity = Entity.loadSuspend("asset://$ELDER").apply { setName("HideElder") }
    suspend fun loadGatherer(): Entity = Entity.loadSuspend("asset://$GATHERER").apply { setName("HideGatherer") }
    suspend fun loadFoodBasket(): Entity = Entity.loadSuspend("asset://$FOOD_BASKET").apply { setName("ReedFoodBasket") }
    suspend fun loadWaterVessel(): Entity = Entity.loadSuspend("asset://$WATER_VESSEL").apply { setName("GourdWaterVessel") }
}
