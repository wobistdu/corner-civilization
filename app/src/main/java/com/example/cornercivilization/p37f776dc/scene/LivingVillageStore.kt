package com.example.cornercivilization.p37f776dc.scene

import android.util.AtomicFile
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.pico.spatial.core.ecs.Entity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Independent additive save: never rewrites the user's existing fire/era ledger. */
internal class LivingVillageStore(directory:File,private val world:Entity,private val events:LocalInterventionWorld) {
    private val file=AtomicFile(File(directory,"living-village-v11.json"))
    private val writer=Executors.newSingleThreadExecutor()
    private var elapsed=0f;private var last="";private var writable=true
    init {
        if(file.baseFile.exists())try {
            val data=JSONObject(String(file.readFully()));require(data.getInt("version") in 1..4)
            val village=requireNotNull(events.village)
            val houses=data.getJSONArray("houses")
            require(houses.length()<=7)
            val restoredHouses=(0 until houses.length()).map {i->
                val h=houses.getJSONObject(i);val old=requireNotNull(village.houses[h.getString("id")])
                require(h.getDouble("health") in 0.0..100.0&&h.getInt("materials") in 0..3&&h.getDouble("progress") in 0.0..52.0)
                require(h.getDouble("cleared") in 0.0..6.0&&h.optInt("rebuilds")>=0&&h.optInt("animalHits")>=0)
                old.copy(health=h.getDouble("health").toFloat(),ruined=h.getBoolean("ruined"),cleared=h.getDouble("cleared").toFloat(),
                    materials=h.getInt("materials"),progress=h.getDouble("progress").toFloat(),rebuilds=h.optInt("rebuilds"),
                    available=if(old.id in village.ecology.sites)h.optBoolean("available",false)else true,
                    damageCause=h.optString("damageCause",if(h.getDouble("health")<99.99)"fire"else"none"),animalHits=h.optInt("animalHits"),safeSeconds=0f)
                    .also{require(it.damageCause in setOf("none","fire","animal"))}
            }
            require(restoredHouses.map{it.id}.distinct().size==restoredHouses.size)
            require(data.optInt("food") in 0..16)
            require(data.optDouble("cultivation",0.0) in 0.0..12.0)
            val foodBundles=data.optJSONArray("foodBundles")?:JSONArray()
            require(foodBundles.length()<=16&&foodBundles.length()+data.optInt("food")<=16)
            val restoredFood=(0 until foodBundles.length()).map { i ->
                val p=foodBundles.getJSONObject(i)
                val point=LocalInterventionWorld.Point(p.getDouble("x").toFloat(),p.getDouble("z").toFloat())
                require(point.x.isFinite()&&point.z.isFinite()&&events.navigation.open(point))
                val wet=p.optDouble("wet",0.0).toFloat();val age=p.optDouble("age",0.0).toFloat()
                require(wet.isFinite()&&wet in 0f..1f&&age.isFinite()&&age>=0f)
                com.example.cornercivilization.p37f776dc.domain.usecase.LivingVillage.FoodBundle(point,wet=wet,age=age,origin=p.optString("origin","forage"))
            }
            val animals=data.optJSONArray("animals")?:JSONArray()
            val restoredAnimals=(0 until animals.length()).map { i->
                val a=animals.getJSONObject(i)
                com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.SavedAnimal(
                    a.getString("id"),a.getBoolean("alive"),
                    LocalInterventionWorld.Point(a.getDouble("x").toFloat(),a.getDouble("z").toFloat()),
                    a.getDouble("health").toFloat(),a.getDouble("hunger").toFloat(),a.getDouble("rest").toFloat(),
                    a.getDouble("feeding").toFloat(),a.getInt("kills"),a.getInt("impacts"))
            }
            val life=data.optJSONObject("life")?.let{VillageLifeCodec.decode(it)}
            if(data.getInt("version")==4)require(life!=null)
            life?.let{village.life.validate(it)}
            val residents=data.optJSONArray("residents")?:JSONArray()
            require(residents.length()<=9)
            val restoredResidents=(0 until residents.length()).map { i->
                val r=residents.getJSONObject(i)
                val id=r.getString("id");val at=LocalInterventionWorld.Point(r.getDouble("x").toFloat(),r.getDouble("z").toFloat())
                require(events.wildlife.bodies[id]?.species==com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.Species.RESIDENT)
                require(at.x.isFinite()&&at.z.isFinite()&&events.navigation.open(at))
                id to at
            }
            require(restoredResidents.map{it.first}.distinct().size==restoredResidents.size)
            val piles=data.optJSONArray("wood")?:JSONArray();require(piles.length()<=6)
            val restoredWood=(0 until piles.length()).map {i->
                val p=piles.getJSONObject(i);val at=LocalInterventionWorld.Point(p.getDouble("x").toFloat(),p.getDouble("z").toFloat())
                require(at.x.isFinite()&&at.z.isFinite()&&events.navigation.open(at)&&events.navigation.path(at,events.depot)!=null)
                val age=p.optDouble("age",0.0).toFloat();require(age.isFinite()&&age in 0f..30f)
                Triple(at,p.optString("phase","WAITING"),age)
            }
            val patches=data.optJSONArray("growth")?:JSONArray();require(patches.length()<=24)
            val restoredGrowth=(0 until patches.length()).map{i->
                val p=patches.getJSONObject(i);val at=LocalInterventionWorld.Point(p.getDouble("x").toFloat(),p.getDouble("z").toFloat())
                val g=p.getDouble("growth").toFloat();val wet=p.optDouble("wet",0.0).toFloat()
                require(at.x.isFinite()&&at.z.isFinite()&&g.isFinite()&&g in 0f..1f&&wet.isFinite()&&wet in 0f..1f)
                LocalInterventionWorld.WetSpot(at,wet,g)
            }
            val scar=if(data.has("scarX"))LocalInterventionWorld.Point(data.getDouble("scarX").toFloat(),data.getDouble("scarZ").toFloat())else null
            require(scar==null||scar.x.isFinite()&&scar.z.isFinite())
            val scarAge=data.optDouble("scarAge",0.0).toFloat();require(scarAge.isFinite()&&scarAge>=0f)
            val crop=data.optDouble("crop",.64).toFloat();require(crop.isFinite()&&crop in .05f..1f)
            val established=data.optBoolean("fieldEstablished",false)
            require(listOf("raids","delivered","collected","harvests").all{data.optInt(it)>=0})
            // Wildlife validates its whole batch before applying; everything else is now parsed.
            events.wildlife.restore(restoredAnimals){events.navigation.open(it)}
            // Saved resident positions are continuous metre coordinates, not grid centres.
            val preferred=restoredResidents.associate{(id,p)->id to (events.navigation.points.minByOrNull{it.distance(p)}?:p)}
            events.wildlife.restoreResidents(preferred,events.navigation.points).forEach{(id,p)->
                village.residents[id]=p
                world.requireNode(id).transform().position=com.pico.spatial.core.math.Vector3(p.x,requireNotNull(events.navigation.height(p.x,p.z)),p.z)
            }
            restoredHouses.forEach{village.houses[it.id]=it}
            village.ecology.apply {
                this.crop=crop
                raids=data.optInt("raids"); food=data.optInt("food")
                cultivation=data.optDouble("cultivation",0.0).toFloat()
                scorch=scar;this.scarAge=scarAge
            }
            village.delivered=data.optInt("delivered");village.collected=data.optInt("collected");village.harvests=data.optInt("harvests")
            village.agriculture=established
            village.foodBundles+=restoredFood
            restoredWood.forEach{(at,phaseName,age)->
                if(events.dropWood(at)){events.woods.last().apply {phase=if(phaseName=="STACKED")LocalInterventionWorld.WoodPhase.STACKED else LocalInterventionWorld.WoodPhase.WAITING;stackedAge=age.coerceAtMost(29.9f)}}
            }
            events.wetSpots+=restoredGrowth
            if(life!=null)village.life.restore(life)else village.life.foodBaseline=village.ecology.food+village.foodBundles.size
            data.optJSONObject("absentResidents")?.let { timers ->
                for (id in timers.keys()) {
                    val body=events.wildlife.bodies[id] ?: continue
                    if(body.species!=com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.Species.RESIDENT) continue
                    val left=timers.optDouble(id)
                    if(left.isFinite() && left in 0.0..20.0) {
                        body.alive=false;village.residents.remove(id)
                        events.returnAt[id]=events.seconds+left.toFloat()
                    }
                }
            }
            val scroll = data.optJSONArray("chronicle")
            if (scroll == null) {
                village.life.journal.recent.forEach { e -> events.chronicle.villageEvent(e.event, e.subject, e.at) }
            } else {
                for (i in (scroll.length() - 96).coerceAtLeast(0) until scroll.length()) {
                    val row = scroll.optJSONObject(i) ?: continue
                    val line = row.optString("text").take(140)
                    if (line.isNotBlank()) events.chronicle.append(row.optInt("time").toFloat(), line, row.optBoolean("player"))
                }
            }
            village.ecology.foodCapacity=if(village.houses["Granary"]?.let{it.available&&!it.ruined&&it.health>=99f}==true)16 else 8
            Log.i("LivingSave","restored version=${data.getInt("version")} houses=${houses.length()} piles=${events.woods.size} life=${life!=null}; transient routes not resumed")
        }catch(error:Exception){writable=false;Log.e("LivingSave","Preserving unreadable/newer village save; will not overwrite",error)}
    }
    fun tick(dt:Float,force:Boolean=false) {
        elapsed+=dt;if(!force&&elapsed<2f)return;elapsed=0f;if(!writable)return
        val v=requireNotNull(events.village)
        val houses=JSONArray();v.houses.values.forEach { h->houses.put(JSONObject().put("id",h.id).put("available",h.available).put("health",h.health).put("ruined",h.ruined).put("cleared",h.cleared).put("materials",h.materials).put("progress",h.progress.coerceAtMost(52f)).put("rebuilds",h.rebuilds).put("damageCause",h.damageCause).put("animalHits",h.animalHits))}
        val animals=JSONArray()
        events.wildlife.save().forEach { a->
            animals.put(JSONObject().put("id",a.id).put("alive",a.alive).put("x",a.point.x).put("z",a.point.z)
                .put("health",a.health).put("hunger",a.hunger).put("rest",a.rest).put("feeding",a.feeding)
                .put("kills",a.kills).put("impacts",a.impacts))
        }
        val residents=JSONArray()
        events.wildlife.bodies.values.filter{it.species==com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.Species.RESIDENT}.forEach{b->
            residents.put(JSONObject().put("id",b.id).put("x",b.point.x).put("z",b.point.z))
        }
        val piles=JSONArray();events.woods.forEach { w->
            val carrier=w.carrier?.let{world.findEntity(it)}?.transform()?.position
            val p=if(w.phase==LocalInterventionWorld.WoodPhase.CARRIED&&carrier!=null)LocalInterventionWorld.Point(carrier.x,carrier.z)else if(w.phase==LocalInterventionWorld.WoodPhase.STACKED)events.depot else w.point
            piles.put(JSONObject().put("x",p.x).put("z",p.z).put("phase",if(w.phase==LocalInterventionWorld.WoodPhase.CARRIED)"WAITING"else w.phase.name).put("age",w.stackedAge))
        }
        val growth=JSONArray();events.wetSpots.forEach{growth.put(JSONObject().put("x",it.point.x).put("z",it.point.z).put("wet",it.wet).put("growth",it.growth))}
        val foodBundles=JSONArray();v.foodBundles.forEach { bundle ->
            foodBundles.put(JSONObject().put("x",bundle.point.x).put("z",bundle.point.z).put("wet",bundle.wet).put("age",bundle.age).put("origin",bundle.origin))
        }
        val json=JSONObject().apply {
            put("absentResidents",JSONObject().apply {
                events.returnAt.forEach { (id,deadline) -> put(id,(deadline-events.seconds).coerceIn(0f,20f).toDouble()) }
            })
            put("chronicle", JSONArray().apply {
                events.chronicle.snapshot().forEach { row ->
                    put(JSONObject().put("time", row.time).put("player", row.player).put("text", row.text))
                }
            })
            put("crop",v.ecology.crop);put("food",v.ecology.food);put("raids",v.ecology.raids)
            v.ecology.scorch?.let { put("scarX",it.x);put("scarZ",it.z);put("scarAge",v.ecology.scarAge.coerceAtMost(180f)) }
        }.put("version",4).put("houses",houses).put("animals",animals).put("residents",residents).put("wood",piles).put("growth",growth).put("delivered",v.delivered).put("collected",v.collected).put("harvests",v.harvests)
            .put("fieldEstablished",v.agriculture).put("cultivation",v.ecology.cultivation).put("foodBundles",foodBundles).put("life",VillageLifeCodec.encode(v.life.snapshot())).toString()
        if(json==last)return;last=json
        writer.submit {var stream:java.io.FileOutputStream?=null;try{stream=file.startWrite();stream.write(json.toByteArray());file.finishWrite(stream)}catch(error:Exception){stream?.let{file.failWrite(it)};Log.e("LivingSave","Atomic save failed",error)}}
    }
    fun close(){tick(0f,true);writer.shutdown()}
}
