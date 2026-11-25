package com.example.cornercivilization.p37f776dc.scene

import com.pico.spatial.core.ecs.Entity

/** Frozen names from the actual 20-archetype manifest; source Model children remain intact. */
private val managedOwnerNames = listOf(
    "World", "IslandOwner", "Resident_0", "Resident_1", "Resident_2", "Resident_3", "Resident_4",
    "Resident_5", "Tent_0", "Tent_1", "Tent_2", "Windmill_0", "Windmill_1", "SpaceBuilding_0",
    "SpaceBuilding_1", "SpaceBuilding_2", "InvaderOwner", "Primitive", "Agricultural", "Industrial", "Modern",
    "Space", "Traffic_0", "Traffic_1", "Traffic_2", "Traffic_3", "Traffic_4", "Traffic_5",
    "Traffic_6", "Traffic_7", "Plot_0", "Plot_1", "Plot_2", "Plot_3", "Plot_4",
    "Plot_5", "Plot_6", "Plot_7", "Plot_8", "StatusPanel", "ToolPanel", "ModalPanel",
    "House_0", "House_1", "House_2", "Field_0", "Field_1", "Field_2", "Factory_0",
    "Factory_1", "Factory_2", "Train_0", "Tower_0", "Tower_1", "Tower_2", "Park_0",
    "Road_0", "Road_1", "Road_2", "Road_3", "Road_4", "Road_5", "Car_0",
    "Car_1", "Car_2", "EnergyTower_0", "LandingPad_0", "CampfirePrototype", "ResourcePrototype", "WaterdropPrototype",
    "Campfire_0",
)

internal fun verifyManagedOwners(world: Entity) {
    val counts = mutableMapOf<String, Int>()
    fun visit(entity: Entity) {
        val name = entity.getName()
        counts[name] = (counts[name] ?: 0) + 1
        entity.getChildren().forEach(::visit)
    }
    visit(world)
    managedOwnerNames.forEach { check(counts[it] == 1) { "Managed runtime owner must exist uniquely: $it count=${counts[it]}" } }
}
