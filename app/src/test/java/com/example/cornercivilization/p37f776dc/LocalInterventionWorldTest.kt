package com.example.cornercivilization.p37f776dc
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import org.junit.Test
import org.junit.Assert.*
class LocalInterventionWorldTest {
 private fun world()=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
 private val p=LocalInterventionWorld.Point(.04f,.10f)
 @Test fun rainStopsAtFifteenSeconds() { val w=world();w.cast(LocalInterventionWorld.Kind.RAIN,p,.034f);w.advance(14.99f);assertEquals(1,w.hazards.size);w.advance(.02f);assertTrue(w.hazards.isEmpty()) }
 @Test fun fireFadesAfterFifteenAndEndsAtEighteen() { val w=world();w.cast(LocalInterventionWorld.Kind.FIRE,p,.05f,"Shelter_0");w.advance(15f);assertEquals(1f,w.hazards.single().strength);w.advance(1.5f);assertEquals(.5f,w.hazards.single().strength);w.advance(1.5f);assertTrue(w.hazards.isEmpty()) }
 @Test fun rainOnlyExtinguishesOverlappingFire() { val w=world();w.cast(LocalInterventionWorld.Kind.FIRE,p,.034f);w.cast(LocalInterventionWorld.Kind.FIRE,LocalInterventionWorld.Point(-.3f,-.2f),.034f);w.cast(LocalInterventionWorld.Kind.RAIN,p,.034f);w.advance(3.1f);assertEquals(1,w.hazards.count{it.kind==LocalInterventionWorld.Kind.FIRE}) }
 @Test fun boundedEventCounts() { val w=world();repeat(3){assertTrue(w.cast(LocalInterventionWorld.Kind.RAIN,p,.034f))};assertFalse(w.cast(LocalInterventionWorld.Kind.RAIN,p,.034f));repeat(6){assertTrue(w.dropWood(p))};assertFalse(w.dropWood(p)) }
 @Test fun waitingWoodDoesNotExpire() { val w=world();w.dropWood(p);w.advance(300f);assertEquals(LocalInterventionWorld.WoodPhase.WAITING,w.woods.single().phase) }
 @Test fun carrierMustArriveBeforeThirtySecondConsumptionBegins() { val w=world();w.dropWood(p);var at=LocalInterventionWorld.Point(.07f,.10f);var steps=0;while(w.woods.single().phase!=LocalInterventionWorld.WoodPhase.STACKED && steps++<500){w.advance(.1f);w.respond("worker",at,.1f,true)?.let{at=it.point}};assertTrue(steps<500);assertTrue(at.distance(w.depot)<.004f);w.advance(29.9f);assertEquals(1,w.woods.size);w.advance(.2f);assertTrue(w.woods.isEmpty()) }
 @Test fun nearbyResidentFleesAndDistantResidentContinues() { val w=world();w.cast(LocalInterventionWorld.Kind.RAIN,p,.034f);var at=p;repeat(50){w.advance(.1f);at=w.respond("near",at,.1f,false)!!.point};assertFalse(w.dangerous(at));assertNull(w.respond("far",LocalInterventionWorld.Point(-.3f,-.2f),.1f,false)) }
 @Test fun claimantIsUnique() { val w=world();w.dropWood(p);assertNotNull(w.respond("a",p,.1f,true));assertNull(w.respond("b",p,.1f,true));assertEquals("a",w.woods.single().carrier) }
 @Test fun obstaclesAndWaterCannotBeCrossed() { val nav=LocalNavigation({x,_->if(kotlin.math.abs(x)<.015f)null else .034f});assertNull(nav.path(LocalInterventionWorld.Point(-.1f,0f),LocalInterventionWorld.Point(.1f,0f)));assertFalse(LocalInterventionWorld(nav).dropWood(LocalInterventionWorld.Point(.1f,0f))) }
 @Test fun pathDetoursAroundBuilding() { val nav=LocalNavigation({_,_->.034f},{x,z->kotlin.math.abs(x)<.03f && kotlin.math.abs(z)<.03f});val path=nav.path(LocalInterventionWorld.Point(-.1f,0f),LocalInterventionWorld.Point(.1f,0f))!!;assertTrue(path.all{nav.open(it)});assertTrue(path.any{kotlin.math.abs(it.z)>.03f}) }

 @Test fun blockedTimberDoesNotSearchEveryFrame() {
  val w=LocalInterventionWorld(LocalNavigation({x,z->
   if(kotlin.math.abs(x)<.021f&&kotlin.math.abs(z-.14f)>.014f)null else .034f
  }))
  val at=LocalInterventionWorld.Point(-.2f,.14f)
  w.wildlife.add("hauler",WildlifeWorld.Species.RESIDENT,at,.003f)
  assertTrue(w.dropWood(LocalInterventionWorld.Point(.15f,.14f)))
  assertTrue(w.dropWood(LocalInterventionWorld.Point(.2f,.2f)))
  assertTrue(w.cast(LocalInterventionWorld.Kind.RAIN,LocalInterventionWorld.Point(0f,.14f),.034f))
  var searches=0
  w.navigation.trace={name,start->if(start&&name in setOf("Village.route","Village.routePreferred"))searches++}
  repeat(50){w.advance(.01f);assertNull(w.respond("hauler",at,.01f,true))}
  assertTrue("blocked timber must not rescan every frame: $searches",searches<=2)
  assertTrue(w.woods.all{it.phase==LocalInterventionWorld.WoodPhase.WAITING&&it.carrier==null})
 }
 @Test fun timberReopensAfterRainAndIsActuallyDelivered() {
  val w=LocalInterventionWorld(LocalNavigation({x,z->
   if(kotlin.math.abs(x)<.021f&&kotlin.math.abs(z-.14f)>.014f)null else .034f
  }))
  var at=LocalInterventionWorld.Point(-.2f,.14f)
  w.wildlife.add("hauler",WildlifeWorld.Species.RESIDENT,at,.003f)
  assertTrue(w.dropWood(LocalInterventionWorld.Point(.10f,.14f)))
  w.cast(LocalInterventionWorld.Kind.RAIN,LocalInterventionWorld.Point(0f,.14f),.034f)
  w.respond("hauler",at,.01f,true)
  assertNull(w.woods.single().carrier)
  w.advance(16f)
  var carried=false
  var stacked=false
  for(step in 0 until 700) {
   w.advance(.1f)
   at=w.respond("hauler",at,.1f,true)?.point?:at
   assertTrue(w.navigation.open(at))
   if(w.woods.single().phase==LocalInterventionWorld.WoodPhase.CARRIED)carried=true
   if(w.woods.single().phase==LocalInterventionWorld.WoodPhase.STACKED){stacked=true;break}
  }
  assertTrue("must really pick up before depositing",carried)
  assertTrue("temporary obstruction must not permanently blacklist the wood",stacked)
  assertTrue(at.distance(w.depot)<.012f)
 }

 @Test fun repeatedRiverDetoursDoNotRescanTheWholeVillage() {
  val nav=LocalNavigation({x,z->if(kotlin.math.abs(x)<.035f && z<.21f)null else .034f})
  val start=LocalInterventionWorld.Point(-.35f,-.21f)
  val end=LocalInterventionWorld.Point(.35f,-.21f)
  var checks=0
  repeat(3) { turn->
   checks=0
   val path=nav.path(start,end){checks++;true}!!
   assertEquals(end,path.last())
   assertTrue(path.all{nav.open(it)})
   assertTrue("must use the real ford",path.any{it.z>=.21f})
   if(turn>0)assertTrue("fixed terrain must not cause a village-wide body scan: $checks",checks<=2000)
  }
 }
 @Test fun sharedSearchPreservesTargetPriorityAndRechecksDynamicBarrier() {
  val nav=LocalNavigation({_,_->.034f})
  val at=LocalInterventionWorld.Point(-.2f,0f)
  val first=LocalInterventionWorld.Point(.2f,0f)
  val second=LocalInterventionWorld.Point(-.1f,0f)
  var barrier=false
  fun allow(p:LocalInterventionWorld.Point)=!barrier||kotlin.math.abs(p.x)>.014f
  assertEquals(first,nav.pathToPreferred(at,listOf(first,second),::allow)!!.last())
  barrier=true
  val alternate=nav.pathToPreferred(at,listOf(first,second),::allow)!!
  assertEquals(second,alternate.last())
  assertTrue(alternate.all(::allow))
  barrier=false
  assertEquals(first,nav.pathToPreferred(at,listOf(first,second),::allow)!!.last())
  assertNull(nav.pathToPreferred(at,emptyList(),::allow))
 }
 @Test fun sharedFailedTargetsCheckEachCellOnceAndDoNotCutCorners() {
  val nav=LocalNavigation({_,_->.034f})
  val at=LocalInterventionWorld.Point(-.21f,0f)
  val targets=List(6){LocalInterventionWorld.Point(.14f,it*.021f)}
  var checks=0
  assertNull(nav.pathToPreferred(at,targets){checks++;kotlin.math.abs(it.x)>.014f})
  assertTrue("six targets share one dynamic grid scan: $checks",checks<=nav.points.size+100)
  assertNull(nav.pathToPreferred(LocalInterventionWorld.Point(-.1f,-.1f),
   listOf(LocalInterventionWorld.Point(.1f,.1f))) {
   (it.x<.002f&&it.z<.002f)||(it.x>.002f&&it.z>.002f)
  })
 }
 @Test fun sharedSearchMatchesIndependentReachabilityAcrossTerrainAndBodies() {
  val nav=LocalNavigation({x,z->if(kotlin.math.abs(x)<.02f&&z<.12f)null else .034f})
  val at=LocalInterventionWorld.Point(-.21f,-.14f)
  val targets=listOf(LocalInterventionWorld.Point(.21f,-.14f),
   LocalInterventionWorld.Point(-.14f,.14f),LocalInterventionWorld.Point(-.3f,-.21f))
  for(block in listOf(LocalInterventionWorld.Point(0f,.25f),LocalInterventionWorld.Point(.2f,-.14f),
   LocalInterventionWorld.Point(-.21f,.28f))) {
   fun allow(p:LocalInterventionWorld.Point)=p.distance(block)>.14f
   val expected=targets.firstOrNull{nav.path(at,it,::allow)!=null}
   val path=nav.pathToPreferred(at,targets,::allow)
   assertEquals(expected,path?.last())
   if(path!=null) {
    assertTrue(path.dropLast(1).all{nav.open(it)&&allow(it)})
    assertTrue(path.zipWithNext().all{(a,b)->a.distance(b)<.012f})
   }
  }
 }
 @Test fun repeatedRoutesRecheckMovingBlockersAndRecoverAfterTheyLeave() {
  val nav=LocalNavigation({_,_->.034f})
  val start=LocalInterventionWorld.Point(-.15f,0f)
  val end=LocalInterventionWorld.Point(.15f,0f)
  var barrier=false
  var blocker=LocalInterventionWorld.Point(0f,0f)
  fun allow(p:LocalInterventionWorld.Point)=
   (!barrier || kotlin.math.abs(p.x)>.015f) && p.distance(blocker)>.035f
  fun route()=nav.path(start,end,::allow)
  val first=route()!!
  assertTrue(first.all(::allow))
  assertTrue(first.any{kotlin.math.abs(it.z)>.03f})
  barrier=true
  assertNull("a cached terrain route must not cross a new dynamic wall",route())
  barrier=false
  blocker=LocalInterventionWorld.Point(0f,.1f)
  val reopened=route()!!
  assertEquals(end,reopened.last())
  assertTrue(reopened.all(::allow))
  assertTrue("reopened direct route should be usable",reopened.all{kotlin.math.abs(it.z)<.01f})
 }
 @Test fun dynamicDiagonalCornersCannotBeCut() {
  val nav=LocalNavigation({_,_->.034f})
  val a=LocalInterventionWorld.Point(-.1f,-.1f)
  val b=LocalInterventionWorld.Point(.1f,.1f)
  assertNull(nav.path(a,b){(it.x<.002f && it.z<.002f) || (it.x>.002f && it.z>.002f)})
 }

 @Test fun residentRouteGridMatchesLiveRulesAtEveryCellAndOffGridTargets() {
  val w=world()
  val random=java.util.Random(1731)
  fun location()=LocalInterventionWorld.Point(random.nextFloat()*.9f-.45f,random.nextFloat()*.66f-.33f)
  repeat(9){w.wildlife.add("r$it",WildlifeWorld.Species.RESIDENT,location(),.004f+it*.0005f)}
  repeat(5){w.wildlife.add("a$it",if(it<2)WildlifeWorld.Species.TIGER else if(it==2)WildlifeWorld.Species.CALF else WildlifeWorld.Species.ADULT,location(),.007f+it*.003f)}
  repeat(3){w.cast(LocalInterventionWorld.Kind.RAIN,location(),.034f)}
  repeat(3){w.cast(LocalInterventionWorld.Kind.FIRE,location(),.034f)}
  val samples=w.navigation.points+List(250){location()}
  assertEquals(11811,w.navigation.points.size)
  repeat(4) { state->
   for(id in listOf("r0","r8","unknown")) {
    val allow=w.residentRouteAllowed(id)
    for(at in samples)assertEquals("state=$state id=$id at=$at",
      !w.dangerous(at)&&w.wildlife.open(id,at),allow(at))
   }
   w.wildlife.bodies.values.forEach{it.point=location()}
   w.wildlife.bodies.getValue("a2").alive=state%2==0
   w.hazards.forEach{it.point=location();it.heat=if(state%2==0)0f else 1f}
  }
 }
 @Test fun routeGridRefreshesMovingRainFireAndAnimalWithoutChangingPreviousSnapshot() {
  val w=world()
  val origin=LocalInterventionWorld.Point(0f,0f)
  val far=LocalInterventionWorld.Point(.25f,.2f)
  w.wildlife.add("resident",WildlifeWorld.Species.RESIDENT,far,.004f)
  val tiger=w.wildlife.add("tiger",WildlifeWorld.Species.TIGER,origin,.012f)
  val first=w.residentRouteAllowed("resident")
  assertFalse(first(origin))
  tiger.point=far
  assertFalse("a search snapshot cannot change underneath it",first(origin))
  assertTrue(w.residentRouteAllowed("resident")(origin))
  val rain=w.beginRain(origin,.034f)!!
  assertFalse(w.residentRouteAllowed("resident")(origin))
  w.moveRain(rain,far,.034f)
  assertTrue(w.residentRouteAllowed("resident")(origin))
  w.cast(LocalInterventionWorld.Kind.FIRE,origin,.034f)
  assertFalse(w.residentRouteAllowed("resident")(origin))
  w.advance(18f)
  assertTrue(w.residentRouteAllowed("resident")(origin))
 }
 @Test fun fallenAnimalsBlockBodiesButDoNotFrightenAndEdgesKeepStrictComparison() {
  val w=world()
  val origin=LocalInterventionWorld.Point(0f,0f)
  val calf=w.wildlife.add("calf",WildlifeWorld.Species.CALF,origin,.007f)
  w.wildlife.add("r",WildlifeWorld.Species.RESIDENT,LocalInterventionWorld.Point(.2f,.2f),.004f)
  val nearby=LocalInterventionWorld.Point(.03f,0f)
  assertFalse(w.residentRouteAllowed("r")(nearby))
  calf.alive=false
  val allow=w.residentRouteAllowed("r")
  assertTrue(allow(nearby))
  assertFalse(allow(origin))
  assertTrue(w.residentRouteAllowed("unknown")(origin))
  val edge=.004f+.007f+.0006f
  assertTrue("touching the exact radius is allowed",allow(LocalInterventionWorld.Point(edge,0f)))
  assertFalse(allow(LocalInterventionWorld.Point(Math.nextDown(edge),0f)))
  assertTrue(allow(LocalInterventionWorld.Point(Math.nextUp(edge),0f)))
 }
 @Test fun routeGridPreservesPathsAcrossNewObstaclesAndTheirRemoval() {
  val w=LocalInterventionWorld(LocalNavigation({x,z->if(kotlin.math.abs(x)<.02f&&z<.12f)null else .034f}))
  val from=LocalInterventionWorld.Point(-.21f,-.14f)
  val to=LocalInterventionWorld.Point(.21f,-.14f)
  w.wildlife.add("r",WildlifeWorld.Species.RESIDENT,from,.004f)
  val animal=w.wildlife.add("adult",WildlifeWorld.Species.ADULT,LocalInterventionWorld.Point(0f,.15f),.025f)
  repeat(3) { state->
   val expected=w.navigation.path(from,to){!w.dangerous(it)&&w.wildlife.open("r",it)}
   val actual=w.navigation.path(from,to,w.residentRouteAllowed("r"))
   assertEquals("same route and no diagonal shortcut, state=$state",expected,actual)
   if(state==0)animal.point=LocalInterventionWorld.Point(.3f,.2f)
   if(state==1)w.cast(LocalInterventionWorld.Kind.RAIN,LocalInterventionWorld.Point(0f,.2f),.034f,radius=.15f)
  }
 }

 @Test fun enclosedGoalIsRejectedLocallyAndReopensAfterBlockersMove() {
  val nav=LocalNavigation({_,_->.034f})
  val start=LocalInterventionWorld.Point(-.35f,-.21f)
  val end=LocalInterventionWorld.Point(.21f,.14f)
  var checks=0
  var enclosed=true
  fun allow(p:LocalInterventionWorld.Point):Boolean {
   checks++
   val radius=p.distance(end)
   return !enclosed || radius<.015f || radius>.045f
  }
  assertNull(nav.path(start,end,::allow))
  assertTrue("an enclosed destination must not scan the whole village: $checks",checks<=512)
  enclosed=false
  val reopened=nav.path(start,end,::allow)!!
  assertEquals(end,reopened.last())
  assertTrue(reopened.all{nav.open(it)})
 }
 @Test fun openTargetChamberStillFindsItsDistantExit() {
  val nav=LocalNavigation({_,_->.034f})
  val start=LocalInterventionWorld.Point(-.3f,0f)
  val end=LocalInterventionWorld.Point(.21f,0f)
  fun allow(p:LocalInterventionWorld.Point)=kotlin.math.abs(p.x-.1f)>.02f || p.z>.2f
  val path=nav.path(start,end,::allow)!!
  assertEquals(end,path.last())
  assertTrue(path.all(::allow))
  assertTrue("large open target region must not be rejected by a local probe",path.any{it.z>.2f})
 }
 @Test fun navigationSearchesMatchIndependentShortestCostsOnSmallGrids() {
  // 独立Bellman-Ford参考，不复用被测队列、邻接或缓存。
  val side=11
  fun point(i:Int)=LocalInterventionWorld.Point(-.441f+(58+i%side)*.007f,-.322f+(41+i/side)*.007f)
  repeat(12) { seed->
   val random=java.util.Random(seed.toLong())
   val cells=(0 until side*side).map(::point)
   val valid=BooleanArray(cells.size){random.nextFloat()>.22f}
   valid[0]=true;valid[valid.lastIndex]=true
   val terrain=cells.filterIndexed { i,_->valid[i] }.toSet()
   val nav=LocalNavigation({x,z->if(LocalInterventionWorld.Point(x,z) in terrain).034f else null})
   val distance=FloatArray(cells.size){Float.POSITIVE_INFINITY};distance[0]=0f
   repeat(cells.size) {
    for(i in cells.indices) if(valid[i])for(j in cells.indices) if(valid[j]) {
     val dx=kotlin.math.abs(i%side-j%side);val dz=kotlin.math.abs(i/side-j/side)
     if(dx>1||dz>1||dx+dz==0)continue
     if(dx==1&&dz==1&&(!valid[i/side*side+j%side]||!valid[j/side*side+i%side]))continue
     val cost=if(dx+dz==2)kotlin.math.sqrt(2f) else 1f
     distance[j]=minOf(distance[j],distance[i]+cost)
    }
   }
   val end=cells.last()
   fun verify(path:List<LocalInterventionWorld.Point>?) {
    if(!distance.last().isFinite()){assertNull("seed $seed",path);return}
    assertNotNull("seed $seed",path)
    assertEquals(end,path!!.last())
    assertTrue(path.all{it in terrain})
    val cost=path.zipWithNext().sumOf{(a,b)->a.distance(b).toDouble()}/.007
    assertEquals("seed $seed",distance.last().toDouble(),cost,.0001)
   }
   verify(nav.path(cells.first(),end))
   verify(nav.pathToNearest(cells.first(),{it==end},{true}))
   verify(nav.pathToAny(cells.first(),listOf(end),{true}))
  }
 }

 @Test fun heldRainMovesAndOnlyReleaseStartsExpiry() { val w=world();val id=w.beginRain(p,.034f)!!;w.advance(40f);assertEquals(1f,w.hazards.single().strength);val moved=LocalInterventionWorld.Point(-.2f,.15f);assertTrue(w.moveRain(id,moved,.04f));assertEquals(moved,w.hazards.single().point);assertFalse(w.dangerous(p));assertTrue(w.dangerous(moved));w.endRain(id);assertFalse(w.moveRain(id,p,.034f));w.advance(14.99f);assertEquals(moved,w.hazards.single().point);w.advance(.02f);assertTrue(w.hazards.isEmpty()) }
 @Test fun heldAndFixedRainShareThreeSlotsWithoutReplacingAny() { val w=world();val a=w.beginRain(p,.034f)!!;w.endRain(a);val b=w.beginRain(p,.034f)!!;val c=w.beginRain(p,.034f)!!;assertNull(w.beginRain(p,.034f));assertEquals(listOf(a,b,c),w.hazards.map{it.id});w.endRain(b,true);assertNotNull(w.beginRain(p,.034f));assertEquals(3,w.hazards.size) }
 @Test fun cancelledRainFreesSlotWithoutLeavingHazard() { val w=world();val id=w.beginRain(p,.034f)!!;w.endRain(id,true);assertFalse(w.dangerous(p));assertTrue(w.hazards.isEmpty()) }
 @Test fun movingRainExtinguishesOnlyNewOverlap() { val w=world();val other=LocalInterventionWorld.Point(-.25f,-.1f);w.cast(LocalInterventionWorld.Kind.FIRE,other,.034f);val id=w.beginRain(p,.034f)!!;assertEquals(0f,w.hazards.first().age);w.moveRain(id,other,.034f);assertEquals(15f,w.hazards.first().age) }
}
