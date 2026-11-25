package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bounded one-shot dry-ground detail; geometry batches, not thousands of Entities. */
internal class PrimitiveGroundDetail(
    private val world: Entity,
) {
    private val meshes = mutableListOf<MeshResource>()
    private val materials = mutableListOf<UnlitMaterial>()
    private data class Batch(val positions: MutableList<Vector3> = mutableListOf(), val indices: MutableList<Int> = mutableListOf(), val colors: MutableList<Color4> = mutableListOf())
    private val root = Entity().apply { setName("GroundMicroDetail"); world.addChild(this) }

    suspend fun load(
        ground: PrimitiveTerrain, routes: List<PrimitiveResidentRoute>,
        awaitActive: suspend (String) -> Unit,
    ) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        try {
            // Capture SDK bounds on main. The worker below only reads immutable
            // terrain samples, points and bounds; it never touches an Entity.
            val reserved = listOf("Shelter_0", "Shelter_1", "Shelter_2", "Campfire", "Resources_0", "Resources_1", "FirstField", "RainShelter").map { name ->
                world.requireNode(name).getVisualBounds(world, recursive=true, enabledOnly=false).also { check(!it.isEmpty()) { "Missing detail exclusion bounds: $name" } }
            }
            val segments = routes.flatMap { route -> route.stops.indices.map { i -> route.stops[i].point to route.stops[(i+1)%route.stops.size].point } }.toMutableList()
            for (lane in 0..1) {
                val z = if (lane==0) .1861f else .2239f
                val stops = listOf(PrimitiveGroundPoint(-.18f,z),PrimitiveGroundPoint(-.13f,z),PrimitiveGroundPoint(-.088f,.227f + if (lane==0) -.0042f else .0042f))
                stops.zipWithNext().forEach { segments += it }
                PrimitiveFireWalk(lane).points.zipWithNext().forEach { segments += it }
            }
            // Additional farmer path is independently reserved from the measured field edge.
            val field = world.requireNode("FirstField").getVisualBounds(world,recursive=true,enabledOnly=false)
            val farmer = listOf(PrimitiveGroundPoint(-.011f,.062f),PrimitiveGroundPoint(.005f,.13f),PrimitiveGroundPoint(.005f,.23f),
                PrimitiveGroundPoint(field.center.x-field.halfExtent.x*.45f,field.max.z+.004f),
                PrimitiveGroundPoint(field.center.x+field.halfExtent.x*.45f,field.max.z+.004f))
            val home = world.requireNode("Shelter_0").getVisualBounds(world, recursive=true, enabledOnly=false)
            val rainShelter = world.requireNode("RainShelter").getVisualBounds(world, recursive=true, enabledOnly=false)
            for ((slot,index) in listOf(1,4,7).withIndex()) {
                val first=routes[index].sample(0f)
                val visitor=PrimitiveCampWalk(slot,PrimitiveGroundPoint(first.x,first.z),
                    PrimitiveGroundPoint(home.max.x+.015f+slot*.010f,
                        rainShelter.max.z+.022f+if(slot==1) .004f else 0f))
                visitor.anchors.zipWithNext().forEach { segments += it }
            }
            farmer.zipWithNext().forEach { segments += it }
            val generated = withContext(Dispatchers.Default) {
            val started = android.os.SystemClock.elapsedRealtime()
            val random = Random(94231)
            var grass = Batch()
            val grassBatches = mutableListOf(grass)
            val stone = Batch()
            fun distance(x: Float,z: Float,a: PrimitiveGroundPoint,b: PrimitiveGroundPoint): Float {
                val dx=b.x-a.x;val dz=b.z-a.z
                val lengthSquared=dx*dx+dz*dz
                if (lengthSquared <= .0000000001f) return hypot(x-a.x,z-a.z)
                val t=(((x-a.x)*dx+(z-a.z)*dz)/lengthSquared).coerceIn(0f,1f)
                return hypot(x-a.x-dx*t,z-a.z-dz*t)
            }
            fun clear(x: Float,z: Float,radius: Float): Boolean =
                reserved.none { x>it.min.x-radius && x<it.max.x+radius && z>it.min.z-radius && z<it.max.z+radius } &&
                    segments.none { distance(x,z,it.first,it.second)<radius+.006f } &&
                    !(x in -.275f..-.07f && z in .16f.. .32f)
            fun support(x: Float,z: Float): Float? = ground.heightOrNull(x,z)
            fun vertex(batch: Batch,p: Vector3,c: Color4) { batch.positions += p;batch.colors += c }
            var tufts=0;var pebbles=0;var bankTufts=0
            // Finite, deterministic islands: dense interiors with gaps rather than uniform glitter.
            val islands = List(38) {
                Triple(random.nextFloat()*.82f-.41f, random.nextFloat()*.59f-.295f,
                    .018f+random.nextFloat()*.022f)
            }
            repeat(18_000) {
                if (it % 128 == 0) ensureActive()
                if (tufts >= PrimitiveVegetationPatch.MAX_TUFTS) return@repeat
                val scatter = it % 5 == 0
                val island = islands[it % islands.size]
                val angle = random.nextFloat()*2f*PI.toFloat()
                val radius = PrimitiveVegetationPatch.offset(random.nextFloat(), island.third)
                val x = if (scatter) random.nextFloat()*.87f-.435f else island.first+cos(angle)*radius
                val z = if (scatter) random.nextFloat()*.64f-.32f else island.second+sin(angle)*radius
                if (!clear(x,z,.005f)) return@repeat
                val centerY=support(x,z) ?: return@repeat
                if (centerY < .0295f) return@repeat
                val bankEdge = listOf(-.014f to 0f, .014f to 0f, 0f to -.014f, 0f to .014f)
                    .any { (dx,dz) -> support(x+dx,z+dz) == null }
                val h = PrimitiveVegetationPatch.bladeHeight(random.nextFloat(), bankEdge)
                // Entire leaf footprint must remain supported and outside the reserved corridors.
                val feet = mutableListOf<Pair<Vector3,Vector3>>()
                val yaws = mutableListOf<Float>()
                repeat(PrimitiveVegetationPatch.BLADES_PER_TUFT) blade@{
                    val yaw=random.nextFloat()*2f*PI.toFloat()
                    val width=.00030f+random.nextFloat()*.00022f
                    val dx=cos(yaw)*width;val dz=sin(yaw)*width
                    val sx=x+(random.nextFloat()-.5f)*.003f;val sz=z+(random.nextFloat()-.5f)*.003f
                    val a=support(sx-dx,sz-dz) ?: return@blade
                    val b=support(sx+dx,sz+dz) ?: return@blade
                    if (!clear(sx,sz,.0025f)) return@blade
                    feet += Vector3(sx-dx,a-.0001f,sz-dz) to Vector3(sx+dx,b-.0001f,sz+dz)
                    yaws += yaw
                }
                if (feet.size != PrimitiveVegetationPatch.BLADES_PER_TUFT) return@repeat
                // Bound each native upload without removing or moving a blade.
                if (tufts > 0 && tufts % 400 == 0) {
                    grass = Batch()
                    grassBatches += grass
                }
                feet.forEachIndexed { blade,(left,right) ->
                    val i=grass.positions.size
                    val yaw=yaws[blade]
                    val shade=random.nextFloat()
                    val bottom=Color4(.15f+shade*.04f,.23f+shade*.045f,.052f+shade*.025f,1f)
                    val middle=Color4(.065f+shade*.055f,.10f+shade*.075f,.022f+shade*.027f,1f)
                    val tip=Color4(.14f+shade*.065f,.17f+shade*.055f,.040f+shade*.045f,1f)
                    val center=(left+right)*.5f
                    val half=(right-left)*.5f
                    val bend=Vector3(cos(yaw+1.4f)*h*.27f,0f,sin(yaw+1.4f)*h*.27f)
                    vertex(grass,left,bottom)
                    vertex(grass,right,bottom)
                    vertex(grass,center+half*.55f+bend*.45f+Vector3(0f,h*.62f,0f),middle)
                    vertex(grass,center-half*.55f+bend*.45f+Vector3(0f,h*.62f,0f),middle)
                    vertex(grass,center+half*.08f+bend+Vector3(0f,h,0f),tip)
                    vertex(grass,center-half*.08f+bend+Vector3(0f,h*.98f,0f),tip)
                    grass.indices += listOf(i,i+1,i+2,i,i+2,i+3,i+3,i+2,i+4,i+3,i+4,i+5)
                }
                tufts++
                if (bankEdge) bankTufts++
            }
            check(grassBatches.sumOf { it.indices.size / 3 } <= PrimitiveVegetationPatch.MAX_GRASS_TRIANGLES)
            repeat(1800) {
                if (it % 128 == 0) ensureActive()
                if (pebbles>=360) return@repeat
                val x=random.nextFloat()*.86f-.43f;val z=random.nextFloat()*.62f-.31f
                if (!clear(x,z,.004f)) return@repeat
                val y=support(x,z) ?: return@repeat
                if (y < .029f) return@repeat
                val r=.0006f+random.nextFloat()*.0018f;val h=r*.65f
                val i=stone.positions.size
                val tint=random.nextFloat()*.035f
                val bottom=Color4(.09f+tint,.070f+tint,.04f+tint,1f)
                val top=Color4(.26f+tint,.21f+tint,.145f+tint,1f)
                val feet = listOf(-r to -r*.7f,r to -r*.6f,r*.9f to r*.7f,-r*.8f to r*.65f).map { (dx,dz) ->
                    val foot=support(x+dx,z+dz) ?: return@repeat
                    Vector3(x+dx,foot-.0001f,z+dz)
                }
                feet.forEach { vertex(stone,it,bottom) }
                vertex(stone,Vector3(x-r*.1f,y+h,z),top)
                stone.indices += listOf(i,i+1,i+4,i+1,i+2,i+4,i+2,i+3,i+4,i+3,i,i+4)
                pebbles++
            }
            val batches = grassBatches.mapIndexed { index, batch -> "FineGrass_$index" to batch } + ("SmallPebbles" to stone)
            for ((_,batch) in batches) {
                check(batch.positions.isNotEmpty() && batch.colors.size==batch.positions.size)
                check(batch.indices.size%3==0 && batch.indices.all { it in batch.positions.indices })
                check(batch.positions.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            }
            Log.i("GroundDetail","generated tufts=$tufts bankTufts=$bankTufts islands=38 pebbles=$pebbles batches=${batches.size} cpuMillis=${android.os.SystemClock.elapsedRealtime()-started} thread=${Thread.currentThread().name}")
            batches
            }
            for ((name,batch) in generated) {
                // Yield for Android input and pause uploads while minimized.
                awaitActive("upload-$name")
                check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                val started = android.os.SystemClock.elapsedRealtime()
                val mesh=MeshResource.createWithMeshModel(MeshModel(batch.positions,batch.indices,colors=batch.colors),bounds=boundsOf(batch.positions),name=name).apply { toGlobal();meshes += this }
                val mat=UnlitMaterial.create().apply { toGlobal();materials += this;setBaseColor(Color4.WHITE);setCullingMode(MaterialCullingMode.NONE);setApplyToneMapping(false) }
                ModelEntity(mesh,mat).apply { setName(name);root.addChild(this) }
                check(mesh.hasColors())
                Log.i("GroundDetail","uploaded name=$name triangles=${mesh.getTriangleCount()} mainMillis=${android.os.SystemClock.elapsedRealtime()-started}")
            }
            Log.i("GroundDetail","ready batches=${meshes.size} triangles=${meshes.sumOf { it.getTriangleCount() }} ground=actualSamples pathClearance=.006 realtimeLighting=false")
        } catch (error: Exception) {
            root.destroy(true)
            close()
            throw error
        }
    }

    /** Scene destroys Entities first; this releases retained definitions exactly once. */
    fun close() { meshes.forEach { it.close() };meshes.clear();materials.forEach { it.close() };materials.clear() }
}
