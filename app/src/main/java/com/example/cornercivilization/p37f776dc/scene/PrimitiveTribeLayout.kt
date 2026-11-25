package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** V5 presentation layout. The immutable source bundle and gameplay fire coordinates stay intact. */
internal fun arrangePrimitiveTribe(world: Entity, ground: PrimitiveGroundSurface) {
    val homes = listOf(
        Triple("Shelter_0", -.240f, .230f),
        Triple("Shelter_1", -.190f, .277f),
        Triple("Shelter_2", -.127f, .270f),
    )
    homes.forEach { (name, x, z) -> placeOnGround(world.requireNode(name), world, ground, x, z) }
    placeOnGround(world.requireNode("Resources_1"), world, ground, -.250f, .265f)
    for (name in listOf("Campfire", "Resources_0")) {
        val e = world.requireNode(name)
        val b = e.getVisualBounds(world, recursive = true, enabledOnly = true)
        placeOnGround(e, world, ground, b.center.x, b.center.z)
    }
    val trees = listOf(
        Triple(0, -.37f, .25f), Triple(2, -.37f, -.03f),
        Triple(5, -.39f, -.25f), Triple(7, -.18f, -.27f),
        Triple(9, .17f, .27f), Triple(11, .38f, .22f),
        Triple(13, .36f, -.04f), Triple(15, .36f, -.27f),
    )
    trees.forEach { (index, x, z) -> placeOnGround(world.requireNode("Tree_$index"), world, ground, x, z) }
}

internal fun replacePrimitiveTrees(world: Entity, prototype: Entity, ground: PrimitiveGroundSurface) {
    val visible = listOf(0, 2, 5, 7, 9, 11, 13, 15)
    world.addChild(prototype)
    prototype.setName("NaturalTreePrototype")
    // Preserve the imported GLB's reviewed upright orientation.
    prototype.enableTree()
    for (index in visible) {
        val old = world.requireNode("Tree_$index")
        val prior = old.getVisualBounds(world, recursive = true, enabledOnly = true)
        check(!prior.isEmpty())
        val tree = requireNotNull(prototype.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true)))
        world.addChild(tree)
        tree.setName("NaturalTree_$index")
        tree.enableTree()
        val initial = tree.getVisualBounds(world, recursive = true, enabledOnly = true)
        check(!initial.isEmpty() && initial.size.y > 0f)
        tree.transform().scaleVector = tree.transform().scaleVector * (prior.size.y / initial.size.y)
        placeOnGround(tree, world, ground, prior.center.x, prior.center.z)
        val final = tree.getVisualBounds(world, recursive = true, enabledOnly = true)
        check(kotlin.math.abs(final.size.y-prior.size.y) < .00002f)
        old.enabled = false
        Log.i("PrimitiveTribe", "treeReplacement=$index source=SGS-image triangles=14732 preservedHeight=${prior.size.y} final=$final owner=PrimitiveWorld numericStatus=PASS visualStatus=UNOBSERVED")
    }
    prototype.enabled = false
}

/** Static wildlife tableau: two adults with a calf, and two separated predators. */
internal fun populatePrimitiveFauna(world: Entity, ground: PrimitiveGroundSurface) {
    val library = world.requireNode("FaunaLibrary")
    data class Placement(val prototype: String, val name: String, val x: Float, val z: Float, val height: Float, val yaw: Float)
    val placements = listOf(
        Placement("MammothPrototype", "MammothAdult_0", .260f, -.130f, .030f, 155f),
        Placement("MammothPrototype", "MammothAdult_1", .300f, -.170f, .0285f, 170f),
        Placement("MammothPrototype", "MammothCalf", .240f, -.190f, .021f, 145f),
        Placement("SabertoothPrototype", "Sabertooth_0", -.320f, -.160f, .0105f, -25f),
        Placement("SabertoothPrototype", "Sabertooth_1", -.280f, -.220f, .00945f, 15f),
    )
    placements.forEach { p ->
        val animal = requireNotNull(library.requireNode(p.prototype).clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true)))
        world.addChild(animal)
        animal.setName(p.name)
        animal.enableTree()
        animal.transform().eulerAngles = EulerAngles(0f, p.yaw, 0f)
        val size = animal.getVisualBounds(world, recursive = true, enabledOnly = true).size
        check(size.y > 0f)
        animal.transform().scaleVector = animal.transform().scaleVector * (p.height / size.y)
        placeOnGround(animal, world, ground, p.x, p.z)
    }
    // Keep the shared mesh/material resources alive; only the five explicit instances render.
    library.enabled = false
    Log.i("PrimitiveTribe", "fauna mammoths=3 sabertooths=2 animation=static source=SGS fixedIBL=pending-bind")
}

private fun placeOnGround(entity: Entity, world: Entity, ground: PrimitiveGroundSurface, x: Float, z: Float) {
    val parent = requireNotNull(entity.getParent())
    val before = entity.getVisualBounds(world, recursive = true, enabledOnly = true)
    check(!before.isEmpty()) { "Cannot ground missing native bounds: ${entity.getName()} bounds=$before" }
    val y = ground.heightAt(x, z)
    val displacement = Vector3(x-before.center.x, y-before.min.y, z-before.center.z)
    entity.transform().position += world.convertPositionTo(displacement, parent) - world.convertPositionTo(Vector3.ZERO, parent)
    val after = entity.getVisualBounds(world, recursive = true, enabledOnly = true)
    check(kotlin.math.abs(after.min.y-y) < .00002f) { "V5 ground placement failed: ${entity.getName()}" }
    Log.i("PrimitiveTribe", "grounded ${entity.getName()} center=($x,$z) support=$y bounds=$after")
}
