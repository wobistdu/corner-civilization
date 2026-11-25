package com.example.cornercivilization.p37f776dc.scene
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Three tapered rough logs with separate bark/end-grain UVs; source layout and portable length remain owned by callers. */
internal class PrimitiveTimberGeometry(world:Entity) {
    private val source=requireNotNull(world.requireNode("Resources_0").requireNode("NativeModel").components.get<ModelComponent>()).mesh
    private val raw=source.getPositions()
    private val bounds=boundsOf(raw.toList().chunked(3).map{Vector3(it[0],it[1],it[2])})
    private val length=maxOf(bounds.size.x,bounds.size.y,bounds.size.z)
    private val positions=mutableListOf<Vector3>();private val indices=mutableListOf<Int>();private val uv=mutableListOf<Vector2>();private val colors=mutableListOf<Color4>()
    private fun add(p:Vector3,t:Vector2,shade:Float):Int {positions+=p;uv+=t;colors+=Color4(shade,shade,shade,1f);return positions.lastIndex}
    private val mesh:MeshResource
    init {
        val radius=length*.095f
        val centers=listOf(Vector3(0f,radius,-radius*1.02f),Vector3(-length*.060f,radius,radius*1.02f),Vector3(length*.042f,radius*2.72f,0f))
        centers.forEachIndexed {log,center->
            val rings=List(5){ring->List(17){side->val a=side*2f*PI.toFloat()/16f;val rr=radius*(1f+.08f*sin(side*2.1f+log))*(1f-ring*.028f)
                val p=Vector3((ring/4f-.5f)*length*(1f-log*.035f),sin(a)*rr,cos(a)*rr)+center+Vector3(bounds.center.x,bounds.min.y,bounds.center.z)
                add(p,Vector2(side/16f*.61f,ring/4f),.68f+.32f*(sin(a)*.5f+.5f))}}
            for(j in 0..3)for(i in 0..15){val a=rings[j][i];val b=rings[j][i+1];val c=rings[j+1][i];val d=rings[j+1][i+1];indices+=listOf(a,c,b,b,c,d)}
            for(end in listOf(0,4)){
                val c=Vector3((end/4f-.5f)*length*(1f-log*.035f),0f,0f)+center+Vector3(bounds.center.x,bounds.min.y,bounds.center.z)
                val ci=add(c,Vector2(.855f,.5f),.94f)
                val rim=List(17){i->val p=positions[rings[end][i]];val angle=i*2f*PI.toFloat()/16f;add(p,Vector2(.855f+cos(angle)*.125f,.5f+sin(angle)*.44f),.88f)}
                for(i in 0..15)indices+=if(end==0)listOf(ci,rim[i],rim[i+1])else listOf(ci,rim[i+1],rim[i])
            }
        }
        // A taut band follows the convex outline of the three logs. Rotating the
        // support normal rounds each corner; switching logs spans the straight edges.
        val origin = Vector3(bounds.center.x, bounds.min.y, bounds.center.z)
        for (along in listOf(-.27f, .26f)) {
            val steps = 32
            val band = List(steps + 1) { i ->
                val angle = (i % steps) * 2f * PI.toFloat() / steps
                val ny = sin(angle)
                val nz = cos(angle)
                val log = centers.maxBy { it.y * ny + it.z * nz }
                val reach = radius * (1.12f - (along + .5f) * .11f)
                val y = log.y + ny * reach
                val z = log.z + nz * reach
                val x = length * along + y * .095f
                val shade = .80f + .20f * (ny + 1f) / 2f
                List(3) { edge ->
                    val crown = if (edge == 1) radius * .12f else 0f
                    add(origin + Vector3(x + (edge - 1) * length * .038f, y + ny*crown, z + nz*crown),
                        Vector2(.64f + edge * .03f, i.toFloat() / steps), shade)
                }
            }
            for (i in 0 until steps) for (edge in 0..1) {
                val a = band[i][edge]; val b = band[i+1][edge]
                val c = band[i][edge+1]; val d = band[i+1][edge+1]
                indices += listOf(a,c,b,b,c,d)
            }
        }
        mesh=MeshResource.createWithMeshModel(MeshModel(positions,indices,uv0=uv,colors=colors),bounds=boundsOf(positions),name="RoughTimberBundle").apply{toGlobal()}
        for(name in listOf("Resources_0","Resources_1"))world.requireNode(name).requireNode("NativeModel").components.get<ModelComponent>()?.mesh=mesh
    }
    fun close(){mesh.close()}
}
