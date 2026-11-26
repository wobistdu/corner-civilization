package com.example.cornercivilization.p37f776dc.scene
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.audio.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Vector3
import kotlin.math.hypot
import kotlin.random.Random

/** Scene-owned spatial mono sources; bounded loops, species cooldowns and quiet occasional birds. */
internal class PrimitiveWorldAudio(private val world:Entity,private val events:LocalInterventionWorld) {
    private val root=Entity().apply{setName("WorldSoundscape");world.addChild(this)}
    private val resources=listOf("drum","rain","fire","ignite","human","mammoth","tiger","birds","steam","build","forage","finish").associateWith {name->AudioResource.load(name,"asset://audio/$name.wav").apply {check(valid){"Missing audio $name"};toGlobal()}}
    private data class Voice(val owner:Entity,val player:AudioPlayerController,val sound:String,var age:Float=0f,var gain:Float=0f)
    private val loops=mutableMapOf<Int,Voice>();private val shots=mutableListOf<Voice>()
    private val tails=mutableListOf<Voice>()
    private val cooled=mutableSetOf<Int>()
    private var rebuiltCount=-1
    private val threatened=mutableSetOf<Long>();private val cooldown=mutableMapOf<String,Float>()
    private val random=Random(1304);private var birdIn=5f;private var enabled=false
    internal var starts=0;private set
    private fun play(sound:String,point:Vector3,volume:Float,loop:Boolean=false):Voice? {
        val e=Entity().apply {setName("Sound_$sound");root.addChild(this);transform().position=point;components.set(ObjectAudioComponent(volume=1f,reverbVolume=.08f,soundRadiusLevel=2f))}
        val c=e.prepareAudio(requireNotNull(resources[sound]))
        if(!c.valid){e.destroy(true);Log.e("WorldAudio","invalid sound=$sound");return null}
        c.setLoop(loop);c.setVolume(volume);c.setPlaybackSpeed(if(loop)1f else random.nextFloat()*.12f+.94f)
        if(!c.play()){c.close();e.destroy(true);Log.e("WorldAudio","play failed sound=$sound");return null}
        starts++;Log.i("WorldAudio","play sound=$sound loop=$loop native=true gain=$volume position=$point starts=$starts")
        return Voice(e,c,sound,gain=volume)
    }
    private fun release(v:Voice){v.player.stop();v.player.close();v.owner.destroy(true)}
    private fun shot(sound:String,p:Vector3,gain:Float){if(shots.size<6)play(sound,p,gain)?.let{shots+=it}}
    private var lastBeat=-1f
    fun update(dt:Float,active:Boolean) {
        if(!active){if(enabled){loops.values.forEach(::release);loops.clear();shots.forEach(::release);shots.clear();tails.forEach(::release);tails.clear();Log.i("WorldAudio","suspended voices=0")};enabled=false;return}
        enabled=true
        events.village?.influence?.beat?.takeIf{it.at!=lastBeat}?.let{
            lastBeat=it.at
            if(shots.size>=6)release(shots.removeAt(0))
            shot("drum",Vector3(it.point.x,.04f,it.point.z),.85f)
        }
        shots.toList().forEach{it.age+=dt;if(it.player.isCompleted() || it.age>5f){release(it);shots.remove(it)}}
        cooldown.keys.toList().forEach{cooldown[it]=requireNotNull(cooldown[it])-dt}
        tails.toList().forEach{it.age+=dt;if(it.age>=.4f){release(it);tails.remove(it)}}
        val ids=events.hazards.map{it.id}.toSet()
        loops.keys.filter{it !in ids}.toList().forEach{loops.remove(it)?.let{v->v.player.fade(0f,350);v.age=0f;if(tails.size>=6)release(tails.removeAt(0));tails+=v}}
        events.hazards.forEach {h->
            val p=Vector3(h.point.x,h.y+.018f,h.point.z)
            val sound=if(h.kind==LocalInterventionWorld.Kind.RAIN)"rain" else "fire"
            val v=loops[h.id]?:play(sound,p,0f,true)?.also{loops[h.id]=it;if(sound=="fire" && (cooldown["ignite"]?:0f)<=0f){shot("ignite",p,.23f);cooldown["ignite"]=.8f}}
            if(v!=null){v.age+=dt;v.owner.transform().position=p;val target=(if(sound=="rain").25f else .28f)*h.strength/(events.hazards.count{it.kind==h.kind}.coerceAtLeast(1));v.gain+=(target-v.gain)*(dt*5f).coerceIn(0f,1f);v.player.setVolume(v.gain)}
        }
        cooled.retainAll(ids)
        events.hazards.filter{it.kind==LocalInterventionWorld.Kind.FIRE&&it.heat<.8f&&events.rainAt(it.point)>.2f&&it.id !in cooled}.forEach { h->
            cooled+=h.id;if((cooldown["steam"]?:0f)<=0f){shot("steam",Vector3(h.point.x,h.y,h.point.z),.18f);cooldown["steam"]=2f}
        }
        events.village?.let { v ->
            val alarm=v.ecology.tiger
            if(alarm!=null && v.residents.values.any { it.distance(alarm)<.09f } && (cooldown["campAlarm"]?:0f)<=0f) {
                shot("human",Vector3(alarm.x,.045f,alarm.z),.13f)
                shot("tiger",Vector3(alarm.x,.04f,alarm.z),.16f)
                cooldown["campAlarm"]=42f
            }
            val total=v.houses.values.sumOf{it.rebuilds}
            if(rebuiltCount>=0&&total>rebuiltCount){val h=v.houses.values.maxBy{it.rebuilds};shot("finish",Vector3(h.center.x,.04f,h.center.z),.12f)}
            rebuiltCount=total
            v.jobs.entries.firstOrNull{it.value.path.isEmpty()&&it.value.kind in listOf("clear","build","forage","harvest") }?.let { (id,job)->
                val sound=if(job.kind in listOf("clear","build"))"build" else "forage"
                if((cooldown["work"]?:0f)<=0f){v.residents[id]?.let{shot(sound,Vector3(it.x,.04f,it.z),.095f)};cooldown["work"]=1.7f}
            }
        }
        val people=world.requireNode("PrimitiveRuntimeResidents").getChildren()
        val animals=world.requireNode("LivingFauna").getChildren()
        (people+animals).forEach {actor->val p=actor.transform().position;val danger=events.hazards.any{it.dangerous && hypot(p.x-it.point.x,p.z-it.point.z)<it.radius+.018f}
            if(danger && actor.id !in threatened){
                val sound=when{actor.getName().startsWith("Mammoth")->"mammoth";actor.getName().startsWith("Sabertooth")->"tiger";else->"human"}
                if((cooldown[sound]?:0f)<=0f){shot(sound,p,if(sound=="human").19f else .28f);cooldown[sound]=if(sound=="human")2f else 3f}
            }
            if(danger)threatened+=actor.id else threatened-=actor.id
        }
        birdIn-=dt
        if(birdIn<=0f){if(events.hazards.isEmpty())shot("birds",Vector3(random.nextFloat()*.8f-.4f,.15f,random.nextFloat()*.5f-.25f),.13f);birdIn=12f+random.nextFloat()*14f}
    }
    fun close(){loops.values.forEach(::release);loops.clear();shots.forEach(::release);shots.clear();tails.forEach(::release);tails.clear();resources.values.forEach{it.close()}}
}
