package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import org.json.JSONArray
import org.json.JSONObject

/** Versioned names, with all parsing and domain validation completed before applying a save. */
internal object VillageLifeCodec {
    private fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().apply{pairs.forEach{put(it.first,it.second?:JSONObject.NULL)}}
    private fun point(p:Point)=obj("x" to p.x,"z" to p.z)
    private fun point(j:JSONObject)=Point(j.getDouble("x").toFloat(),j.getDouble("z").toFloat())
    private fun <T> map(j:JSONObject,read:(String,Any)->T):Map<String,T> =
        j.keys().asSequence().associateWith{read(it,j.get(it))}
    private fun <T> array(j:JSONArray,read:(JSONObject)->T)= (0 until j.length()).map{read(j.getJSONObject(it))}
    private fun <T> objects(values:Map<String,T>,write:(T)->JSONObject)=JSONObject().apply{values.forEach{(k,v)->put(k,write(v))}}
    private fun <T> array(values:List<T>,write:(T)->JSONObject)=JSONArray().apply{values.forEach{put(write(it))}}
    private fun JSONObject.f(k:String)=getDouble(k).toFloat()
    private fun JSONObject.nullString(k:String)=if(isNull(k))null else getString(k)

    private fun person(p:VillageLife.Person)=obj("hunger" to p.hunger,"thirst" to p.thirst,"fatigue" to p.fatigue,
        "wet" to p.wet,"mud" to p.mud,"fear" to p.fear,"lonely" to p.lonely,"farmSkill" to p.farmSkill,
        "buildSkill" to p.buildSkill,"tool" to p.tool,"meals" to p.meals,"drinks" to p.drinks,
        "helped" to p.helped,"hostedBy" to p.hostedBy,"interrupted" to p.interrupted,"warned" to p.warned,
        "watch" to p.watch,"lastMeal" to p.lastMeal,"lastSocial" to p.lastSocial)
    private fun person(p:JSONObject)=VillageLife.Person(p.f("hunger"),p.f("thirst"),p.f("fatigue"),
        p.f("wet"),p.f("mud"),p.f("fear"),p.f("lonely"),p.f("farmSkill"),p.f("buildSkill"),p.f("tool"),
        p.getInt("meals"),p.getInt("drinks"),p.getInt("helped"),p.nullString("hostedBy"),
        p.getBoolean("interrupted"),p.getBoolean("warned"),p.f("watch"),p.f("lastMeal"),p.f("lastSocial"))
    private fun patch(p:VillageLife.Patch)=point(p.point).put("berries",p.berries).put("pressure",p.pressure)
        .put("resting",p.resting).put("regrowth",p.regrowth).put("branches",p.branches).put("branchGrowth",p.branchGrowth)
    private fun patch(p:JSONObject)=VillageLife.Patch(point(p),p.f("berries"),p.f("pressure"),p.getBoolean("resting"),
        p.f("regrowth"),p.getInt("branches"),p.f("branchGrowth"))
    private fun home(h:VillageLife.Home)=obj("leak" to h.leak,"brace" to h.brace,"inspected" to h.inspected,"salvage" to h.salvage,"visits" to h.visits,"damp" to h.damp)
    private fun home(h:JSONObject)=VillageLife.Home(h.f("leak"),h.f("brace"),h.f("inspected"),h.getInt("salvage"),h.getInt("visits"),h.f("damp"))
    private fun animal(s:VillageWildlifeLife.State)=obj("thirst" to s.thirst,"fatigue" to s.fatigue,"coat" to s.coat,
        "cooldown" to s.cooldown,"reunions" to s.reunions,"drinks" to s.drinks,"fenceMemory" to s.fenceMemory,"carcassReturned" to s.carcassReturned,"nextCalfAt" to s.nextCalfAt)
    private fun animal(s:JSONObject)=VillageWildlifeLife.State(thirst=s.f("thirst"),fatigue=s.f("fatigue"),
        coat=s.f("coat"),cooldown=s.f("cooldown"),reunions=s.getInt("reunions"),drinks=s.getInt("drinks"),
        fenceMemory=s.f("fenceMemory"),carcassReturned=s.getBoolean("carcassReturned"), nextCalfAt=s.optDouble("nextCalfAt",0.0).toFloat().takeIf { it.isFinite() && it>=0f } ?: 0f)

    fun encode(s:VillageLife.Snapshot)=obj("schema" to 1,"floats" to JSONObject(s.floats),"ints" to JSONObject(s.ints),
        "flags" to JSONObject(s.flags),"people" to objects(s.people,::person),"patches" to array(s.patches,::patch),
        "homes" to objects(s.homes,::home),"debts" to JSONObject(s.debts),"bonds" to JSONObject(s.bonds),
        "cargo" to objects(s.cargo,::point),"blocked" to JSONObject(s.blocked),"points" to objects(s.points,::point),
        "reservedFor" to s.reservedFor,"counts" to JSONObject(s.counts.mapKeys{it.key.name}),
        "recent" to array(s.recent){e->obj("event" to e.event.name,"subject" to e.subject,"at" to e.at,"result" to e.result)},
        "animals" to objects(s.animals,::animal),
        "plantings" to array(s.plantings){p->point(p.point).put("meadow",p.meadow).put("growth",p.growth)},
        "river" to obj("softBank" to s.river.softBank, "blank" to s.river.blank, "cistern" to s.river.cistern,
            "trapUses" to s.river.trapUses, "catchProgress" to s.river.catchProgress))

    fun decode(j:JSONObject):VillageLife.Snapshot {
        require(j.getInt("schema")==1)
        val river = if (j.has("river")) j.getJSONObject("river").let {
            RiverLivelihood.State(it.f("softBank"), it.getBoolean("blank"), it.getBoolean("cistern"),
                it.getInt("trapUses"), it.f("catchProgress"))
        } else RiverLivelihood.State()
        return VillageLife.Snapshot(
            map(j.getJSONObject("floats")){_,v->(v as Number).toFloat()},
            map(j.getJSONObject("ints")){_,v->(v as Number).toInt()},
            map(j.getJSONObject("flags")){_,v->v as Boolean},
            map(j.getJSONObject("people")){_,v->person(v as JSONObject)},
            array(j.getJSONArray("patches"),::patch),map(j.getJSONObject("homes")){_,v->home(v as JSONObject)},
            map(j.getJSONObject("debts")){_,v->(v as Number).toInt()},map(j.getJSONObject("bonds")){_,v->(v as Number).toFloat()},
            map(j.getJSONObject("cargo")){_,v->point(v as JSONObject)},map(j.getJSONObject("blocked")){_,v->v as String},
            map(j.getJSONObject("points")){_,v->point(v as JSONObject)},j.nullString("reservedFor"),
            map(j.getJSONObject("counts")){_,v->(v as Number).toInt()}.mapKeys{VillageEvent.valueOf(it.key)},
            array(j.getJSONArray("recent")){e->VillageJournal.Entry(VillageEvent.valueOf(e.getString("event")),e.getString("subject"),e.f("at"),e.getString("result"))},
            map(j.getJSONObject("animals")){_,v->animal(v as JSONObject)}, river, if(j.has("plantings"))array(j.getJSONArray("plantings")){p->VillageInfluence.Plot(point(p),p.getBoolean("meadow"),p.f("growth"))} else emptyList())
    }
}
