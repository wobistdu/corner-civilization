package com.example.cornercivilization.p37f776dc
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Test
import org.junit.Assert.*

class LivingVillageTest {
 private fun world()=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
 private fun village(w:LocalInterventionWorld):LivingVillage=LivingVillage(w).also {v->w.village=v;v.addHouse("Shelter_0",Point(.08f,.1f),.02f,Point(.045f,.1f));v.sources+=Point(-.06f,.13f)}
 private fun step(w:LocalInterventionWorld, seconds:Float){repeat((seconds*10).toInt()){w.advance(.1f)}}
 private fun drive(w:LocalInterventionWorld,positions:MutableMap<String,Point>,ticks:Int) {
  repeat(ticks) {w.advance(.1f);for(id in positions.keys) w.respond(id,positions.getValue(id),.1f,false)?.let { positions[id]=it.point }}
 }
 @Test fun repairSuppliesCanReachFreeEdgeOfOccupiedDepot() {
  val w=world();val v=village(w)
  val worker=w.wildlife.add("worker",WildlifeWorld.Species.RESIDENT,Point(-.14f,.22f),.004f)
  w.wildlife.add("eater",WildlifeWorld.Species.RESIDENT,w.depot,.004f)
  assertFalse(w.wildlife.open(worker.id,w.depot))
  val path=requireNotNull(v.supplyPath(worker.id,worker.point,w.depot))
  for(step in path)assertEquals(step,w.wildlife.move(worker.id,step))
  assertTrue(worker.point.distance(w.depot)<.012f)
 }
 @Test fun settledResidentsGatherDeliverAndCultivateWithoutIntervention() {
  val w=world();val v=village(w);v.settled=true
  val positions=listOf(0,1,4,7).associate { "PrimitiveResident_$it" to w.depot }.toMutableMap()
  drive(w,positions,1800)
  assertTrue(v.agriculture);assertEquals(12f,v.ecology.cultivation,.001f)
  assertEquals(v.ecology.food,v.harvests);assertEquals(8,v.ecology.food)
  assertTrue(v.foodBundles.isEmpty());assertTrue(w.hazards.isEmpty())
 }
 @Test fun missingShelterOrFoodCannotBeReplacedByRuntime() {
  for(settled in listOf(false,true)) {
   val w=world();val v=village(w);v.sources.clear();v.settled=settled
   if(!settled)v.ecology.food=3
   val positions=mutableMapOf("PrimitiveResident_0" to w.depot)
   drive(w,positions,6200)
   assertFalse(v.agriculture);assertEquals(0f,v.ecology.cultivation,0f)
  }
 }
 @Test fun foodCountsOnlyAfterArrivalAndInterruptedCargoIsRecoveredOnce() {
  val w=world();val v=village(w);val id="PrimitiveResident_0"
  val positions=mutableMapOf(id to w.depot)
  var carrying=false
  repeat(800) { if(!carrying){drive(w,positions,1);carrying=v.jobs[id]?.kind=="foodDelivery"} }
  assertTrue(carrying);assertEquals(0,v.ecology.food);assertEquals(1,v.foodBundles.size)
  val droppedAt=positions.getValue(id);v.interrupt(id,droppedAt);v.sources.clear()
  assertNull(v.foodBundles.single().carrier);assertEquals(droppedAt,v.foodBundles.single().point)
  val picker="PrimitiveResident_1";val approach=v.respond(picker,Point(-.20f,.20f),.1f)!!
  assertEquals("foodPickup",v.jobs.getValue(picker).kind)
  v.interrupt(picker,approach.point)
  assertEquals(droppedAt,v.foodBundles.single().point);assertNull(v.foodBundles.single().carrier)
  drive(w,positions,900)
  assertEquals(1,v.ecology.food);assertEquals(1,v.harvests);assertTrue(v.foodBundles.isEmpty())
  drive(w,positions,200)
  assertEquals(1,v.ecology.food)
 }
 @Test fun cultivationRequiresArrivalAndKeepsWorkAcrossRain() {
  val w=world();val v=village(w);v.sources.clear();v.ecology.food=3;v.settled=true
  val id="PrimitiveResident_0";val positions=mutableMapOf(id to w.depot)
  drive(w,positions,1);assertEquals(0f,v.ecology.cultivation,0f)
  var worked=false
  repeat(500) {if(!worked){drive(w,positions,1);worked=v.ecology.cultivation>=2f}}
  assertTrue(worked);val progress=v.ecology.cultivation
  w.cast(Kind.RAIN,v.ecology.field,.034f);drive(w,positions,1)
  assertFalse(v.agriculture);assertEquals(progress,v.ecology.cultivation,.001f)
  drive(w,positions,600)
  assertTrue(v.agriculture);assertEquals(12f,v.ecology.cultivation,.001f)
 }
 @Test fun unreachableFoodSourceDoesNotCreateFoodOrAField() {
  val w=LocalInterventionWorld(LocalNavigation({_,_->.034f},{x,_->x in -.02f.. .02f}))
  val v=LivingVillage(w);w.village=v;v.settled=true;v.sources+=Point(.08f,.2f)
  val positions=mutableMapOf("PrimitiveResident_0" to w.depot)
  drive(w,positions,1800)
  assertEquals(0,v.ecology.food);assertTrue(v.foodBundles.isEmpty());assertFalse(v.agriculture)
 }
 @Test fun fireUnderAlreadyFixedRainIsSuppressed(){val w=world();val p=Point(.1f,.1f);w.cast(Kind.RAIN,p,.03f);w.cast(Kind.FIRE,p,.03f);step(w,3f);assertTrue(w.hazards.none{it.kind==Kind.FIRE})}
 @Test fun wetGroundOutlastsRainAndWeakensReignition(){val w=world();val p=Point(.1f,.1f);w.cast(Kind.RAIN,p,.03f);step(w,16f);assertTrue(w.hazards.isEmpty());assertTrue(w.wetAt(p)>.7f);w.cast(Kind.FIRE,p,.03f);assertTrue(w.hazards.single().strength<.4f);step(w,32f);assertEquals(0f,w.wetAt(p),.001f)}
 @Test fun rainCreatesGrowthAfterCloudGone(){val w=world();w.cast(Kind.RAIN,Point(.1f,.1f),.03f);step(w,30f);assertTrue(w.wetSpots.single().growth>.7f)}
 @Test fun sustainedFireDestroysButEarlyRainSaves(){val w=world();val v=village(w);val h=v.houses.getValue("Shelter_0");w.cast(Kind.FIRE,h.center,.05f,h.id);step(w,13f);assertTrue(h.ruined);assertEquals(0f,h.health,.001f)
 val w2=world();val h2=village(w2).houses.getValue("Shelter_0");w2.cast(Kind.FIRE,h2.center,.05f,h2.id);step(w2,3f);w2.cast(Kind.RAIN,h2.center,.05f);step(w2,15f);assertFalse(h2.ruined);assertTrue(h2.health>50f)}
 @Test fun reservedWoodDoesNotDecay(){val w=world();w.dropWood(w.depot);val p=w.woods.single();p.phase=WoodPhase.STACKED;p.carrier="worker";step(w,31f);assertEquals(1,w.woods.size);p.carrier=null;step(w,30f);assertTrue(w.woods.isEmpty())}
 @Test fun autonomousRebuildReallyCollectsAndConsumesThreeBundles(){val w=world();val v=village(w);val h=v.houses.getValue("Shelter_0");h.health=0f;h.ruined=true
 val positions=(0..8).associate{"PrimitiveResident_$it" to Point(-.03f+it*.008f,.15f)}.toMutableMap()
 repeat(2400){w.advance(.1f);positions.keys.toList().forEach{id->w.respond(id,positions.getValue(id),.1f,id.last() in listOf('2','5','8'))?.let{positions[id]=it.point}}}
 assertEquals(100f,h.health,.001f);assertEquals(1,h.rebuilds);assertEquals(3,v.delivered);assertTrue(v.collected>=3);assertTrue(v.completedTasks>=3)
 }
 @Test fun carriedBuildSupplyIsReleasedWhenThreatened(){val w=world();val v=village(w);val h=v.houses.getValue("Shelter_0");h.health=50f;h.safeSeconds=4f;w.dropWood(w.depot);w.woods.single().phase=WoodPhase.STACKED
 v.respond("worker",w.depot,1f);assertEquals(WoodPhase.CARRIED,w.woods.single().phase)
 v.interrupt("worker",Point(-.02f,.16f));assertNull(w.woods.single().carrier);assertEquals(WoodPhase.WAITING,w.woods.single().phase);assertEquals(0,h.materials)
 }
 @Test fun dailyTasksContinueWithoutDisaster(){val w=world();val v=village(w);var p=Point(.03f,.1f);repeat(1200){w.advance(.1f);w.respond("PrimitiveResident_0",p,.1f,false)?.let{p=it.point}};assertTrue(v.completedTasks>=3);assertEquals(0,v.delivered)}
 @Test fun damagedPublicRoofCannotProtectEitherRainEntry() {
 val rules=FireSliceUseCase()
 val state=com.example.cornercivilization.p37f776dc.domain.model.FireSliceState(phase=com.example.cornercivilization.p37f776dc.domain.model.FirePhase.BURNING,strength=100,shelter=com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.COMPLETE,buildMillis=12_000,sourceWood=0,spentWood=8)
 val water=com.example.cornercivilization.p37f776dc.domain.model.FireTool.WATER
 assertEquals(85,rules.intervene(state,water,true).strength)
 assertEquals(45,rules.intervene(state,water,false).strength)
 assertEquals(45,rules.evolve(state,120_000,false).strength)
 }
 @Test fun interruptedConstructionKeepsInvestmentAndResumes() {
 val w=world();val v=village(w);val h=v.houses.getValue("Shelter_0")
 h.health=0f;h.ruined=true;h.cleared=6f;h.materials=3;h.progress=12f;h.safeSeconds=4f
 var p=h.workPoint
 repeat(15){w.advance(.1f);v.respond("worker",p,.1f)?.let{p=it.point}}
 val progress=h.progress;assertTrue(progress>12f)
 w.cast(Kind.RAIN,h.workPoint,.034f);w.respond("worker",p,.1f,false)
 assertFalse(v.jobs.containsKey("worker"));step(w,17f)
 assertEquals(progress,h.progress,.001f);assertEquals(3,h.materials)
 repeat(600){w.advance(.1f);w.respond("worker",p,.1f,false)?.let{p=it.point}}
 assertEquals(1,h.rebuilds);assertEquals(0,v.delivered)
 }
}
