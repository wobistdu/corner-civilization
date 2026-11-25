package com.example.cornercivilization.p37f776dc.ui.fire

import androidx.lifecycle.ViewModelStore
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceStore
import com.example.cornercivilization.p37f776dc.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FireSliceViewModelTest {
    @Test fun staleArrivalCannotDeliverNewTaskAndFailedDeliveryDoesNotConsumeWood() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = Store(FireSliceState(phase = FirePhase.BURNING, strength = 100, knowsFire = true, shelter = ShelterPhase.NEEDED))
        val models = ViewModelStore()
        try {
            val vm = FireSliceViewModel(store, dispatcher); models.put("test", vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true, 0))
            vm.onEvent(FireSliceEvent.Pulse(100, true, true, ShelterPhase.NEEDED)); runCurrent()
            assertEquals(ShelterPhase.FETCHING, vm.state.value.world!!.shelter)
            vm.onEvent(FireSliceEvent.Pulse(200, true, true, ShelterPhase.NEEDED)); runCurrent()
            assertEquals(8, vm.state.value.world!!.sourceWood)
            store.fail = true
            vm.onEvent(FireSliceEvent.Pulse(300, true, true, ShelterPhase.FETCHING)); runCurrent()
            assertEquals(ShelterPhase.FETCHING, vm.state.value.world!!.shelter)
            assertEquals(8, store.saved.sourceWood)
            assertEquals(0, store.saved.carriedWood)
            assertNotNull(vm.state.value.error)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun selectingToolDoesNotInterveneUntilHearthTap() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = Store(); val models = ViewModelStore()
        try {
            val vm = FireSliceViewModel(store, dispatcher); models.put("test", vm); runCurrent()
            assertEquals(FirePhase.QUIET, vm.state.value.world!!.phase)
            vm.onEvent(FireSliceEvent.Platform(true, 0))
            vm.onEvent(FireSliceEvent.Select(FireTool.FIRE)); runCurrent()
            assertEquals(FireTool.FIRE, vm.state.value.tool)
            assertEquals(FirePhase.QUIET, vm.state.value.world!!.phase)
            vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(FirePhase.BURNING, store.saved.phase)
            assertFalse(store.saved.knowsFire)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun unreadableSaveDoesNotCreateOrOverwriteWorld() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var writes = 0
        val store = object : FireSliceStore {
            override suspend fun load(): FireSliceState = error("unreadable save")
            override suspend fun save(state: FireSliceState) { writes++ }
        }
        val models = ViewModelStore()
        try {
            val vm = FireSliceViewModel(store, dispatcher); models.put("test", vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true, 0))
            vm.onEvent(FireSliceEvent.Pulse(50_000, true))
            vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertNull(vm.state.value.world)
            assertNotNull(vm.state.value.error)
            assertEquals(0, writes)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    private class Store(var saved: FireSliceState = FireSliceState()) : FireSliceStore {
        var fail = false
        override suspend fun load() = saved
        override suspend fun save(state: FireSliceState) { check(!fail); saved = state }
    }
    @Test fun stopAndRestoreExcludeOfflineTimeAndSaveWetState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = Store()
        val models = ViewModelStore()
        try {
            val vm = FireSliceViewModel(store, dispatcher); models.put("first", vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true, 100)); runCurrent()
            vm.onEvent(FireSliceEvent.Pulse(1_100, false)); runCurrent()
            vm.onEvent(FireSliceEvent.Select(FireTool.WATER)); vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(FirePhase.WET, store.saved.phase)
            vm.onEvent(FireSliceEvent.Platform(false, 1_100)); runCurrent()
            vm.onEvent(FireSliceEvent.Pulse(99_000, false)); runCurrent()
            assertEquals(1_000L, vm.state.value.world!!.activeMillis)
            val restored = FireSliceViewModel(store, dispatcher); models.put("restored", restored); runCurrent()
            restored.onEvent(FireSliceEvent.Platform(true, 999_000)); runCurrent()
            restored.onEvent(FireSliceEvent.Pulse(1_000_000, false)); runCurrent()
            assertEquals(2_000L, restored.state.value.world!!.activeMillis)
            assertEquals(FirePhase.WET, restored.state.value.world!!.phase)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun fieldResultAndDeliveryPersistBeforePublish() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store = Store(FireSliceState(phase=FirePhase.BURNING, strength=100, knowsFire=true,
            shelter=ShelterPhase.COMPLETE, sourceWood=0, spentWood=8, buildMillis=12_000,
            evolutionMillis=599_900, weatherCycle=4, forageCycle=9, fuelCycle=19))
        val models = ViewModelStore()
        try {
            val vm=FireSliceViewModel(store, dispatcher); models.put("test",vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true,0))
            vm.onEvent(FireSliceEvent.Pulse(100,true,fieldEstablished=true)); runCurrent()
            assertTrue(vm.state.value.world!!.agricultural)
            assertTrue(store.saved.agricultural)
            assertEquals(ResourcePhase.FETCHING,vm.state.value.world!!.resourcePhase)
            vm.onEvent(FireSliceEvent.Pulse(200,true, resourceReady=true, resourceTask=ResourcePhase.WAITING)); runCurrent()
            assertEquals(0, vm.state.value.world!!.haulingWood)
            vm.onEvent(FireSliceEvent.Pulse(300,true, resourceReady=true, resourceTask=ResourcePhase.FETCHING)); runCurrent()
            assertEquals(4,store.saved.haulingWood)
            vm.onEvent(FireSliceEvent.Pulse(350,true, resourceReady=true, resourceTask=ResourcePhase.FETCHING)); runCurrent()
            assertEquals(0,vm.state.value.world!!.storedWood)
            store.fail=true
            vm.onEvent(FireSliceEvent.Pulse(400,true, resourceReady=true, resourceTask=ResourcePhase.CARRYING)); runCurrent()
            assertEquals(0,vm.state.value.world!!.storedWood)
            assertEquals(4,vm.state.value.world!!.haulingWood)
            assertEquals(0,vm.state.value.world!!.deliveries)
            assertNotNull(vm.state.value.error)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun firstSelectionPersistsOnceAndDoesNotReappearAfterRestore() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val store=Store();val models=ViewModelStore()
        try {
            val vm=FireSliceViewModel(store,dispatcher);models.put("first",vm);runCurrent()
            for(tool in FireTool.entries) {
                vm.onEvent(FireSliceEvent.Select(tool));runCurrent()
                assertEquals(tool,vm.state.value.firstToolHint)
                vm.onEvent(FireSliceEvent.DismissHint(tool));runCurrent()
                assertNull(vm.state.value.firstToolHint)
            }
            assertEquals(15,store.saved.seenTools)
            val restored=FireSliceViewModel(store,dispatcher);models.put("restored",restored);runCurrent()
            for(tool in FireTool.entries) {
                restored.onEvent(FireSliceEvent.Select(tool));runCurrent()
                assertNull(restored.state.value.firstToolHint)
            }
            assertEquals(FirePhase.QUIET,store.saved.phase)
        } finally { models.clear();runCurrent();Dispatchers.resetMain() }
    }
    @Test fun fieldResultNeverRegressesAndFailedSaveIsNotPublished() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val store=Store();val models=ViewModelStore()
        try {
            val vm=FireSliceViewModel(store,dispatcher);models.put("first",vm);runCurrent()
            store.fail=true
            vm.onEvent(FireSliceEvent.Pulse(100,false,fieldEstablished=true));runCurrent()
            assertFalse(vm.state.value.world!!.fieldEstablished)
            assertFalse(store.saved.fieldEstablished)
            assertNotNull(vm.state.value.error)
            store.fail=false
            val restored=FireSliceViewModel(store,dispatcher);models.put("restored",restored);runCurrent()
            restored.onEvent(FireSliceEvent.Pulse(200,false,fieldEstablished=true));runCurrent()
            restored.onEvent(FireSliceEvent.Pulse(300,false,fieldEstablished=false));runCurrent()
            assertTrue(store.saved.fieldEstablished)
            assertTrue(restored.state.value.world!!.fieldEstablished)
        } finally { models.clear();runCurrent();Dispatchers.resetMain() }
    }
    @Test fun inactiveWorldNeverReplaysOfflineEvolution() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val store=Store();val models=ViewModelStore()
        try {
            val vm=FireSliceViewModel(store,dispatcher);models.put("test",vm);runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true,100))
            vm.onEvent(FireSliceEvent.Pulse(1100,false));runCurrent()
            vm.onEvent(FireSliceEvent.Platform(false,1100));runCurrent()
            assertEquals(1000L,store.saved.evolutionMillis)
            vm.onEvent(FireSliceEvent.Pulse(990000,false));runCurrent()
            assertEquals(1000L,vm.state.value.world!!.evolutionMillis)
            vm.onEvent(FireSliceEvent.Platform(true,990000))
            vm.onEvent(FireSliceEvent.Pulse(991000,false));runCurrent()
            assertEquals(2000L,vm.state.value.world!!.evolutionMillis)
        } finally { models.clear();runCurrent();Dispatchers.resetMain() }
    }
    @Test fun repeatSelectionCancelsAndGiftIsCriticalSaved() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store=Store(); val models=ViewModelStore()
        try {
            val vm=FireSliceViewModel(store,dispatcher); models.put("test",vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true,0))
            vm.onEvent(FireSliceEvent.Select(FireTool.WOOD)); runCurrent()
            assertEquals(4,vm.state.value.world!!.pendingWood)
            vm.onEvent(FireSliceEvent.Select(FireTool.WOOD)); vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(FireTool.OBSERVE,vm.state.value.tool)
            assertEquals(4,vm.state.value.world!!.pendingWood)
            vm.onEvent(FireSliceEvent.Select(FireTool.WOOD)); vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(8,store.saved.pendingWood)
            assertEquals(0,store.saved.storedWood)
            vm.onEvent(FireSliceEvent.Pulse(1_100,false)); runCurrent()
            store.fail=true; vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(8,vm.state.value.world!!.pendingWood)
            assertNotNull(vm.state.value.error)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun failedCriticalSaveDoesNotPublishUncommittedWater() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = Store(); val models = ViewModelStore()
        try {
            val vm = FireSliceViewModel(store, dispatcher); models.put("test", vm); runCurrent()
            vm.onEvent(FireSliceEvent.Platform(true, 0)); vm.onEvent(FireSliceEvent.Select(FireTool.WATER)); runCurrent()
            store.fail = true; vm.onEvent(FireSliceEvent.HearthTap); runCurrent()
            assertEquals(FirePhase.QUIET, vm.state.value.world!!.phase)
            assertNotNull(vm.state.value.error)
            assertEquals(0L, store.saved.waterSequence)
        } finally { models.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
