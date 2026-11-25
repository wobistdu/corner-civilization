package com.example.cornercivilization.p37f776dc.ui.world

import androidx.lifecycle.ViewModelStore
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldEngine
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WorldViewModelTest {
    private val date = LocalDate.of(2026, 11, 10)
    private class Store(var loaded: WorldLoad) : WorldStore {
        var failure: String? = null
        var failureFor: ((WorldState) -> String?)? = null
        val saved = mutableListOf<WorldState>()
        override suspend fun load() = loaded
        override suspend fun save(state: WorldState): String? {
            val error = failureFor?.invoke(state) ?: failure
            if (error == null) saved += state
            return error
        }
    }
    private fun TestScope.fixture(
        world: WorldState? = WorldFactory.create(date),
        wallStart: Instant = Instant.parse("2026-11-10T04:00:00Z"),
    ): Triple<WorldViewModel, Store, (Long) -> Unit> {
        var millis = 0L
        val store = Store(world?.let { WorldLoad.Loaded(it) } ?: WorldLoad.Empty)
        val vm = WorldViewModel(store, uptime = { millis }, now = { wallStart.plusMillis(millis) }, dispatcher = StandardTestDispatcher(testScheduler), trace = {})
        vm.onEvent(WorldUiEvent.SceneAvailability(true))
        return Triple(vm, store, { millis = it })
    }
    private fun TestScope.close(vm: WorldViewModel) { ViewModelStore().apply { put("test", vm); clear() }; runCurrent() }
    private fun platform(sighted: Boolean, started: Boolean = true) = WorldUiEvent.Platform(started, sighted, false, false, "TEST")

    @Test fun unavailableScenePausesGrowthAndResumedBattleWithoutChargingLoadingTime() = runTest {
        val engine = WorldEngine()
        val space = WorldFactory.create(date).copy(era = Era.SPACE)
        val fighting = engine.dispatch(space, WorldCommand.Tick(TimeSlice(date, 305_000, true, true))).state
        val (vm, _, time) = fixture(fighting)
        runCurrent()
        vm.onEvent(WorldUiEvent.SceneAvailability(false))
        vm.onEvent(platform(true)); runCurrent()
        val initial = vm.state.value.world!!
        time(60_000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertFalse(vm.state.value.growthActive)
        assertEquals(initial.day.growthMillis, vm.state.value.world!!.day.growthMillis)
        assertEquals(initial.invasion, vm.state.value.world!!.invasion)
        vm.onEvent(WorldUiEvent.SceneAvailability(true)); runCurrent()
        time(61_000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(initial.day.growthMillis + 1_000, vm.state.value.world!!.day.growthMillis)
        assertEquals(initial.invasion!!.remainingMillis - 1_000, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.SceneAvailability(false)); runCurrent()
        val suspended = vm.state.value.world!!
        time(90_000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(suspended.day.growthMillis, vm.state.value.world!!.day.growthMillis)
        assertEquals(suspended.invasion, vm.state.value.world!!.invasion)
        close(vm)
    }

    @Test fun emptyLoadShowsFirstOpenAndStartEnablesGrowth() = runTest {
        val (vm, store, _) = fixture(null)
        runCurrent()
        assertEquals(WorldPresentation.FIRST_OPEN, vm.state.value.presentation)
        assertEquals(1, store.saved.size)
        vm.onEvent(platform(true)); vm.onEvent(WorldUiEvent.Start); runCurrent()
        assertTrue(vm.state.value.growthActive)
        close(vm)
    }

    @Test fun growthIgnoresFocusSightAndOnstageAndUsesStrictThreshold() = runTest {
        val (vm, _, time) = fixture()
        runCurrent(); vm.onEvent(platform(false)); runCurrent()
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        time(600000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNull(vm.state.value.world!!.qualification)
        time(600001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNotNull(vm.state.value.world!!.qualification)
        close(vm)
    }

    @Test fun threeGrowthSegmentsExcludeTwoLongStoppedIntervalsBeforeQualifying() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(false)); runCurrent()
        var elapsed = 0L
        for ((index, segment) in listOf(200000L, 200000L, 201000L).withIndex()) {
            elapsed += segment
            time(elapsed); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
            assertEquals(listOf(200000L, 400000L, 601000L)[index], vm.state.value.world!!.day.growthMillis)
            assertEquals(index == 2, vm.state.value.world!!.qualification != null)
            if (index < 2) {
                vm.onEvent(platform(false, started = false)); runCurrent()
                val beforeGap = vm.state.value.world!!.day.growthMillis
                elapsed += 2 * 3600000L
                time(elapsed); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
                assertEquals(beforeGap, vm.state.value.world!!.day.growthMillis)
                vm.onEvent(platform(false)); runCurrent()
            }
        }
        assertEquals(date, vm.state.value.world!!.day.date)
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertEquals(1, store.saved.count { it.qualification != null })
        close(vm)
    }

    @Test fun liveClockAndVmSplitThreeHundredSecondsBeforeMidnightThenThreeOhOneAndSixOhOne() = runTest {
        val start = date.atTime(23, 55).atZone(ZoneId.systemDefault()).toInstant()
        val (vm, store, time) = fixture(wallStart = start)
        runCurrent(); vm.onEvent(platform(false)); runCurrent()
        time(300000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(date, vm.state.value.world!!.day.date)
        assertEquals(300000L, vm.state.value.world!!.day.growthMillis)
        assertNull(vm.state.value.world!!.qualification)
        time(601000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(date.plusDays(1), vm.state.value.world!!.day.date)
        assertEquals(301000L, vm.state.value.world!!.day.growthMillis)
        assertNull(vm.state.value.world!!.qualification)
        time(901000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(601000L, vm.state.value.world!!.day.growthMillis)
        assertEquals(date.plusDays(1), vm.state.value.world!!.qualification!!.earnedOnDate)
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertEquals(vm.state.value.world, store.saved.last())
        close(vm)
    }

    @Test fun invasionStartAndWarningToBattleSaveFailuresRetainOldStateUntilRetryCommitsOnce() = runTest {
        for (enteringBattle in listOf(false, true)) {
            val (vm, store, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
            runCurrent(); vm.onEvent(platform(true)); runCurrent()
            val boundary = if (enteringBattle) 305000L else 300000L
            time(boundary - 1); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
            val previous = vm.state.value.world!!
            if (enteringBattle) {
                assertEquals(InvasionPhase.WARNING, previous.invasion!!.phase)
                assertEquals(1L, previous.invasion!!.remainingMillis)
            } else assertNull(previous.invasion)
            store.failure = "disk full"
            time(boundary); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
            assertEquals(previous, vm.state.value.world)
            assertEquals(previous, store.saved.last())
            assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
            assertFalse(vm.state.value.growthActive)
            assertFalse(vm.state.value.canRebuild)
            time(boundary + 10000); vm.onEvent(WorldUiEvent.Retry); runCurrent()
            assertEquals(previous, vm.state.value.world)
            store.failure = null
            time(boundary + 20000); vm.onEvent(WorldUiEvent.Retry); runCurrent()
            val committed = vm.state.value.world!!
            val duration = if (enteringBattle) 20000L else 5000L
            assertEquals(if (enteringBattle) InvasionPhase.FIGHTING else InvasionPhase.WARNING, committed.invasion!!.phase)
            assertEquals(duration, committed.invasion!!.remainingMillis)
            assertEquals(boundary, committed.day.growthMillis)
            assertEquals(committed, store.saved.last())
            val saves = store.saved.size
            vm.onEvent(WorldUiEvent.Retry); runCurrent()
            assertEquals(committed, vm.state.value.world)
            assertEquals(saves, store.saved.size)
            time(boundary + 21000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
            assertEquals(duration - 1000, vm.state.value.world!!.invasion!!.remainingMillis)
            assertEquals(boundary + 1000, vm.state.value.world!!.day.growthMillis)
            close(vm)
        }
    }

    @Test fun criticalSaveFailureDoesNotPublishInterventionAndRetryCommitsOnce() = runTest {
        val (vm, store, _) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        val old = vm.state.value.world!!
        store.failure = "disk full"
        vm.onEvent(WorldUiEvent.SelectTool(InterventionKind.GRANT_RESOURCE))
        vm.onEvent(WorldUiEvent.PlotTap(old.city.plots.first().id)); runCurrent()
        assertEquals(old.city.resources, vm.state.value.world!!.city.resources)
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        assertFalse(vm.state.value.canRebuild)
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertTrue(vm.state.value.world!!.city.resources > old.city.resources)
        assertEquals(WorldPresentation.INTERVENTION_FEEDBACK, vm.state.value.presentation)
        close(vm)
    }

    @Test fun repeatedCheckpointFailuresReturnToObservingAndExcludeErrorTime() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        val growth = vm.state.value.world!!.day.growthMillis
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        assertFalse(vm.state.value.growthActive)
        time(20000); vm.onEvent(WorldUiEvent.Retry); runCurrent()
        time(30000); vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        store.failure = null
        time(40000); vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.saveFailed)
        assertEquals(WorldPresentation.OBSERVING, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertEquals(growth, vm.state.value.world!!.day.growthMillis)
        time(41000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(growth + 1000, vm.state.value.world!!.day.growthMillis)
        close(vm)
    }

    @Test fun realReopensDuringSaveErrorCollapseToOneDeferredUpgrade() = runTest {
        val qualified = WorldEngine().dispatch(WorldFactory.create(date),
            WorldCommand.Tick(TimeSlice(date, 600001, true, false))).state
        val (vm, store, time) = fixture(qualified)
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        for (day in 1L..3L) {
            vm.onEvent(platform(false, started = false)); runCurrent()
            time(day * 86400000); vm.onEvent(platform(true)); runCurrent()
        }
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        assertEquals(date.plusDays(3), vm.state.value.world!!.day.date)
        assertEquals(date.plusDays(3), vm.state.value.world!!.lastUpgradeDate)
        assertNull(vm.state.value.world!!.qualification)
        assertEquals(0L, vm.state.value.world!!.day.growthMillis)
        val recovered = vm.state.value.world
        val saves = store.saved.size
        vm.onEvent(WorldUiEvent.Retry); vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(recovered, vm.state.value.world)
        assertEquals(saves, store.saved.size)
        close(vm)
    }

    @Test fun deferredOpenSurvivesItsOwnCriticalSaveFailureAndAnotherDateReopen() = runTest {
        val qualified = WorldEngine().dispatch(WorldFactory.create(date),
            WorldCommand.Tick(TimeSlice(date, 600001, true, false))).state
        val (vm, store, time) = fixture(qualified)
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(platform(false, started = false)); runCurrent()
        time(86400000); vm.onEvent(platform(true)); runCurrent()
        store.failure = null
        store.failureFor = { if (it.era == Era.AGRICULTURAL) "upgrade save failed" else null }
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals("upgrade save failed", vm.state.value.error)
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertNotNull(vm.state.value.world!!.qualification)
        // A second real reopen does not create a queue of upgrade credits.
        vm.onEvent(platform(false, started = false)); runCurrent()
        time(2 * 86400000L); vm.onEvent(platform(true)); runCurrent()
        store.failureFor = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        assertEquals(date.plusDays(1), vm.state.value.world!!.lastUpgradeDate)
        assertEquals(date.plusDays(2), vm.state.value.world!!.lastOpenDate)
        assertEquals(date.plusDays(2), vm.state.value.world!!.day.date)
        assertNull(vm.state.value.world!!.qualification)
        val recovered = vm.state.value.world
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(recovered, vm.state.value.world)
        close(vm)
    }

    @Test fun continuousMidnightAndRetryWithoutRealReopenNeverConsumesQualification() = runTest {
        val qualified = WorldEngine().dispatch(WorldFactory.create(date),
            WorldCommand.Tick(TimeSlice(date, 600001, true, false))).state
        val (vm, store, time) = fixture(qualified)
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        time(86400000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertNotNull(vm.state.value.world!!.qualification)
        assertEquals(date, vm.state.value.world!!.lastOpenDate)
        time(86400001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(date.plusDays(1), vm.state.value.world!!.day.date)
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        // The next real stop/start still consumes it normally.
        vm.onEvent(platform(false, started = false)); vm.onEvent(platform(true)); runCurrent()
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        close(vm)
    }

    @Test fun failedHelpCheckpointRestoresHelpAndCloseReturnsToObserving() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); vm.onEvent(WorldUiEvent.Help); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.OBSERVING, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        close(vm)
    }

    @Test fun failedBattleHelpCheckpointRestoresHelpWithoutSpendingDefenseTime() = runTest {
        val (vm, store, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(305000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        val remaining = vm.state.value.world!!.invasion!!.remainingMillis
        vm.onEvent(WorldUiEvent.Help); runCurrent()
        store.failure = "disk full"
        time(316000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        assertEquals(remaining, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.BATTLE, vm.state.value.presentation)
        assertTrue(vm.state.value.battleActive)
        time(317000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(remaining - 1000, vm.state.value.world!!.invasion!!.remainingMillis)
        close(vm)
    }

    @Test fun pendingQualificationRestoresHelpAndAppearsWhenHelpCloses() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(600000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Help); runCurrent()
        store.failure = "disk full"
        time(600001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNull(vm.state.value.world!!.qualification)
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertNotNull(vm.state.value.world!!.qualification)
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.QUALIFIED, vm.state.value.presentation)
        close(vm)
    }

    @Test fun pendingEraUpgradeStillShowsItsCommittedEvolutionAfterRetry() = runTest {
        val yesterday = date.minusDays(1)
        val qualified = WorldEngine().dispatch(WorldFactory.create(yesterday),
            WorldCommand.Tick(TimeSlice(yesterday, 600001, true, false))).state
        val (vm, store, _) = fixture(qualified)
        store.failure = "disk full"
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertNull(vm.state.value.pendingEvolution)
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertNull(vm.state.value.pendingEvolution)
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        assertNull(vm.state.value.world!!.qualification)
        assertEquals(WorldPresentation.EVOLVING, vm.state.value.presentation)
        val evolution = vm.state.value.pendingEvolution!!
        assertEquals(Era.PRIMITIVE, evolution.fromEra)
        assertEquals(Era.AGRICULTURAL, evolution.toEra)
        val saves = store.saved.size
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(evolution, vm.state.value.pendingEvolution)
        assertEquals(saves, store.saved.size)
        close(vm)
    }

    @Test fun coldOpenEvolutionWaitsForMatchingSceneAcknowledgementAcrossTicks() = runTest {
        val yesterday = date.minusDays(1)
        val qualified = WorldEngine().dispatch(WorldFactory.create(yesterday),
            WorldCommand.Tick(TimeSlice(yesterday, 600001, true, false))).state
        val (vm, store, time) = fixture(qualified)
        runCurrent()
        val evolution = vm.state.value.pendingEvolution!!
        assertEquals(Era.PRIMITIVE, evolution.fromEra)
        assertEquals(Era.AGRICULTURAL, evolution.toEra)
        assertEquals(Era.AGRICULTURAL, store.saved.last().era)
        // Scene availability arrives after cold load; it must still receive this event.
        vm.onEvent(WorldUiEvent.SceneAvailability(false)); vm.onEvent(platform(true)); runCurrent()
        time(1000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(evolution, vm.state.value.pendingEvolution)
        vm.onEvent(WorldUiEvent.SceneAvailability(true)); runCurrent()
        time(2000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(evolution, vm.state.value.pendingEvolution)
        vm.onEvent(WorldUiEvent.EvolutionPresented("wrong-id")); runCurrent()
        assertEquals(evolution, vm.state.value.pendingEvolution)
        vm.onEvent(WorldUiEvent.EvolutionPresented(evolution.id)); runCurrent()
        assertNull(vm.state.value.pendingEvolution)
        time(3000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNull(vm.state.value.pendingEvolution)
        close(vm)
    }

    @Test fun restoringAlreadyUpgradedSnapshotDoesNotReplayHistoricEvolution() = runTest {
        val upgraded = WorldFactory.create(date).copy(era = Era.AGRICULTURAL,
            lastUpgradeDate = date.minusDays(1))
        val (vm, _, time) = fixture(upgraded)
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        assertNull(vm.state.value.pendingEvolution)
        time(1000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNull(vm.state.value.pendingEvolution)
        close(vm)
    }

    @Test fun oldEvolutionAcknowledgementCannotClearNewlyCommittedEvolution() = runTest {
        val yesterday = date.minusDays(1)
        val qualified = WorldEngine().dispatch(WorldFactory.create(yesterday),
            WorldCommand.Tick(TimeSlice(yesterday, 600001, true, false))).state
        val (vm, _, time) = fixture(qualified)
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        val first = vm.state.value.pendingEvolution!!
        time(600001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(first, vm.state.value.pendingEvolution)
        vm.onEvent(platform(false, started = false)); runCurrent()
        time(86400000); vm.onEvent(platform(true)); runCurrent()
        val second = vm.state.value.pendingEvolution!!
        assertNotEquals(first.id, second.id)
        assertEquals(Era.AGRICULTURAL, second.fromEra)
        assertEquals(Era.INDUSTRIAL, second.toEra)
        vm.onEvent(WorldUiEvent.EvolutionPresented(first.id)); runCurrent()
        assertEquals(second, vm.state.value.pendingEvolution)
        vm.onEvent(WorldUiEvent.EvolutionPresented(second.id)); runCurrent()
        assertNull(vm.state.value.pendingEvolution)
        close(vm)
    }

    @Test fun pendingVictoryAndResetStillShowTheirCommittedOutcomeAfterRetry() = runTest {
        for (victory in listOf(true, false)) {
            val (vm, store, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
            runCurrent(); vm.onEvent(platform(true)); runCurrent()
            time(305000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
            if (victory) {
                val invasion = vm.state.value.world!!.invasion!!
                repeat(9) { vm.onEvent(WorldUiEvent.InvaderTap(invasion.eventId, invasion.targetId)); runCurrent() }
                store.failure = "disk full"
                vm.onEvent(WorldUiEvent.InvaderTap(invasion.eventId, invasion.targetId)); runCurrent()
                assertEquals(9, vm.state.value.world!!.invasion!!.hits)
            } else {
                store.failure = "disk full"
                time(325000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
                assertEquals(Era.SPACE, vm.state.value.world!!.era)
            }
            assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
            vm.onEvent(WorldUiEvent.Retry); runCurrent()
            store.failure = null
            vm.onEvent(WorldUiEvent.Retry); runCurrent()
            assertNull(vm.state.value.error)
            assertNull(vm.state.value.world!!.invasion)
            assertEquals(if (victory) WorldPresentation.VICTORY else WorldPresentation.RESET, vm.state.value.presentation)
            assertEquals(if (victory) Era.SPACE else Era.PRIMITIVE, vm.state.value.world!!.era)
            assertTrue(vm.state.value.presentationMotionActive)
            if (!victory) {
                assertFalse(vm.state.value.growthActive)
                val freshGrowth = vm.state.value.world!!.day.growthMillis
                time(326000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
                assertEquals(freshGrowth, vm.state.value.world!!.day.growthMillis)
            }
            vm.onEvent(platform(false, started = false)); runCurrent()
            assertFalse(vm.state.value.presentationMotionActive)
            close(vm)
        }
    }

    @Test fun spaceWarningFreezesOutsideSightAndManualPauseFreezesBothClocks() = runTest {
        val (vm, _, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(false)); runCurrent()
        time(300000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(InvasionPhase.WARNING, vm.state.value.world!!.invasion!!.phase)
        time(320000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(5000L, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.Pause); runCurrent()
        val growth = vm.state.value.world!!.day.growthMillis
        time(350000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(growth, vm.state.value.world!!.day.growthMillis)
        close(vm)
    }

    @Test fun helpKeepsGrowingButFreezesVisibleBattleUntilDismissed() = runTest {
        val (vm, _, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(305000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(InvasionPhase.FIGHTING, vm.state.value.world!!.invasion!!.phase)
        val remaining = vm.state.value.world!!.invasion!!.remainingMillis
        val growth = vm.state.value.world!!.day.growthMillis
        vm.onEvent(WorldUiEvent.Help); runCurrent()
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        time(316000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        assertEquals(growth + 11000, vm.state.value.world!!.day.growthMillis)
        assertEquals(remaining, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.BATTLE, vm.state.value.presentation)
        assertTrue(vm.state.value.battleActive)
        time(317000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(remaining - 1000, vm.state.value.world!!.invasion!!.remainingMillis)
        close(vm)
    }

    @Test fun detailsHelpAndFeedbackKeepSeparateReturnPoints() = runTest {
        val (vm, _, _) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        vm.onEvent(WorldUiEvent.SelectTool(InterventionKind.WATER)); runCurrent()
        vm.onEvent(WorldUiEvent.Details); vm.onEvent(WorldUiEvent.Details); runCurrent()
        assertEquals(WorldPresentation.SELECTING, vm.state.value.detailsPresentation)
        vm.onEvent(WorldUiEvent.Help); vm.onEvent(WorldUiEvent.Help); runCurrent()
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.SELECTING, vm.state.value.presentation)
        assertEquals(InterventionKind.WATER, vm.state.value.selectedTool)
        vm.onEvent(WorldUiEvent.PlotTap("plot-0")); runCurrent()
        assertEquals(WorldPresentation.INTERVENTION_FEEDBACK, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.Details); runCurrent()
        assertEquals(WorldPresentation.INTERVENTION_FEEDBACK, vm.state.value.detailsPresentation)
        vm.onEvent(WorldUiEvent.Help); runCurrent()
        vm.onEvent(WorldUiEvent.CloseHelp); vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.INTERVENTION_FEEDBACK, vm.state.value.presentation)
        assertEquals(WorldPresentation.INTERVENTION_FEEDBACK, vm.state.value.detailsPresentation)
        assertEquals(InterventionKind.WATER, vm.state.value.lastEffect)
        close(vm)
    }

    @Test fun detailsKeepsGrowthAndShowsNewQualificationWhenClosed() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(600000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Details); runCurrent()
        time(600001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertEquals(WorldPresentation.QUALIFIED, vm.state.value.detailsPresentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        assertNotNull(vm.state.value.world!!.qualification)
        assertNotNull(store.saved.last().qualification)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.QUALIFIED, vm.state.value.presentation)
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        close(vm)
    }

    @Test fun spaceTriggerKeepsDetailsAndNestedHelpUntilClosedThenStartsWarning() = runTest {
        val (vm, _, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(299000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Details); runCurrent()
        time(310000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertEquals(WorldPresentation.WARNING, vm.state.value.detailsPresentation)
        assertEquals(310000L, vm.state.value.world!!.day.growthMillis)
        assertEquals(InvasionPhase.WARNING, vm.state.value.world!!.invasion!!.phase)
        assertEquals(5000L, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.Help); runCurrent()
        time(320000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertEquals(WorldPresentation.WARNING, vm.state.value.detailsPresentation)
        assertFalse(vm.state.value.battleActive)
        assertEquals(5000L, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.WARNING, vm.state.value.presentation)
        assertTrue(vm.state.value.battleActive)
        time(325000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.BATTLE, vm.state.value.presentation)
        assertEquals(20000L, vm.state.value.world!!.invasion!!.remainingMillis)
        close(vm)
    }

    @Test fun spaceTriggerInsideNestedHelpRefreshesDetailsStatusWithoutClosingReaders() = runTest {
        val (vm, _, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(299000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Details); vm.onEvent(WorldUiEvent.Help); runCurrent()
        time(300000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.HELP, vm.state.value.presentation)
        assertEquals(WorldPresentation.WARNING, vm.state.value.detailsPresentation)
        assertEquals(5000L, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertEquals(WorldPresentation.WARNING, vm.state.value.detailsPresentation)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.WARNING, vm.state.value.presentation)
        close(vm)
    }

    @Test fun detailsPauseResumeAndSceneAvailabilityKeepIndependentClockGates() = runTest {
        val fighting = WorldEngine().dispatch(WorldFactory.create(date).copy(era = Era.SPACE),
            WorldCommand.Tick(TimeSlice(date, 305000, true, true))).state
        val (vm, _, time) = fixture(fighting)
        runCurrent(); vm.onEvent(platform(true)); vm.onEvent(WorldUiEvent.Details); runCurrent()
        val initialGrowth = vm.state.value.world!!.day.growthMillis
        val remaining = vm.state.value.world!!.invasion!!.remainingMillis
        time(1000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(initialGrowth + 1000, vm.state.value.world!!.day.growthMillis)
        vm.onEvent(WorldUiEvent.Pause); runCurrent()
        assertFalse(vm.state.value.growthActive)
        assertFalse(vm.state.value.presentationMotionActive)
        time(5000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(initialGrowth + 1000, vm.state.value.world!!.day.growthMillis)
        vm.onEvent(WorldUiEvent.Resume); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertTrue(vm.state.value.presentationMotionActive)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.SceneAvailability(false)); runCurrent()
        time(8000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertFalse(vm.state.value.growthActive)
        assertFalse(vm.state.value.presentationMotionActive)
        assertEquals(initialGrowth + 1000, vm.state.value.world!!.day.growthMillis)
        vm.onEvent(WorldUiEvent.SceneAvailability(true)); runCurrent()
        vm.onEvent(WorldUiEvent.Help); vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertEquals(remaining, vm.state.value.world!!.invasion!!.remainingMillis)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.BATTLE, vm.state.value.presentation)
        time(9000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(remaining - 1000, vm.state.value.world!!.invasion!!.remainingMillis)
        close(vm)
    }

    @Test fun detailsCheckpointRetryPreservesSelectionReturnPointAndReadingGates() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        vm.onEvent(WorldUiEvent.SelectTool(InterventionKind.GRANT_RESOURCE)); vm.onEvent(WorldUiEvent.Details); runCurrent()
        store.failure = "disk full"
        time(10000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertTrue(vm.state.value.growthActive)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.Help); vm.onEvent(WorldUiEvent.CloseHelp); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.SELECTING, vm.state.value.presentation)
        assertEquals(InterventionKind.GRANT_RESOURCE, vm.state.value.selectedTool)
        close(vm)
    }

    @Test fun pendingQualificationRetryRestoresDetailsUntilExplicitClose() = runTest {
        val (vm, store, time) = fixture()
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(600000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        vm.onEvent(WorldUiEvent.Details); runCurrent()
        store.failure = "disk full"
        time(600001); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        assertNull(vm.state.value.world!!.qualification)
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        assertNotNull(vm.state.value.world!!.qualification)
        assertFalse(vm.state.value.battleActive)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.QUALIFIED, vm.state.value.presentation)
        close(vm)
    }

    @Test fun committedNextDayEraTransitionInterruptsDetailsEvenAfterSaveRetry() = runTest {
        val qualified = WorldEngine().dispatch(WorldFactory.create(date),
            WorldCommand.Tick(TimeSlice(date, 600001, true, false))).state
        val (vm, store, time) = fixture(qualified)
        runCurrent(); vm.onEvent(platform(true)); vm.onEvent(WorldUiEvent.Details); runCurrent()
        vm.onEvent(platform(false, started = false)); runCurrent()
        store.failure = "disk full"
        time(86400000); vm.onEvent(platform(true)); runCurrent()
        assertEquals(WorldPresentation.RECOVERY_ERROR, vm.state.value.presentation)
        store.failure = null
        vm.onEvent(WorldUiEvent.Retry); runCurrent()
        assertEquals(Era.AGRICULTURAL, vm.state.value.world!!.era)
        assertEquals(WorldPresentation.EVOLVING, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.EVOLVING, vm.state.value.presentation)
        close(vm)
    }

    @Test fun detailsRequestAtBattleDeadlineCannotHideCommittedReset() = runTest {
        val (vm, _, time) = fixture(WorldFactory.create(date).copy(era = Era.SPACE))
        runCurrent(); vm.onEvent(platform(true)); runCurrent()
        time(305000); vm.onEvent(WorldUiEvent.Pulse); runCurrent()
        time(325000); vm.onEvent(WorldUiEvent.Details); runCurrent()
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        assertEquals(WorldPresentation.RESET, vm.state.value.presentation)
        assertTrue(vm.state.value.presentationMotionActive)
        assertFalse(vm.state.value.growthActive)
        // A later explicit request may inspect that completed reset, then return to it.
        vm.onEvent(WorldUiEvent.Details); runCurrent()
        assertEquals(WorldPresentation.DETAILS, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.CloseDetails); runCurrent()
        assertEquals(WorldPresentation.RESET, vm.state.value.presentation)
        close(vm)
    }

    @Test fun corruptRecoveryRequiresConfirmationAndKeepsActiveInvasionLocked() = runTest {
        val (vm, store, _) = fixture()
        store.loaded = WorldLoad.Failed("both invalid", canRebuild = true)
        runCurrent()
        assertNull(vm.state.value.world)
        assertTrue(vm.state.value.canRebuild)
        vm.onEvent(WorldUiEvent.AskReset); runCurrent()
        assertEquals(WorldPresentation.RESTORE_CONFIRM, vm.state.value.presentation)
        vm.onEvent(WorldUiEvent.CancelReset); runCurrent()
        assertNull(vm.state.value.world)
        vm.onEvent(WorldUiEvent.AskReset); vm.onEvent(WorldUiEvent.ConfirmReset); runCurrent()
        assertEquals(Era.PRIMITIVE, vm.state.value.world!!.era)
        close(vm)
    }
}
