package com.agustin.tarati.services.clock

import com.agustin.tarati.core.domain.game.pieces.CobColor.BLACK
import com.agustin.tarati.core.domain.game.pieces.CobColor.WHITE
import com.agustin.tarati.core.domain.game.time.ClockState
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ClockLogic].
 *
 * Covers tick semantics, increment/delay/period handling for all time control
 * modes, and boundary conditions (zero delta, pause/resume, timeouts). Because
 * [ClockLogic] accepts `now` as a parameter, tests are fully deterministic —
 * no need for `runTest` or `advanceTimeBy`.
 */
class ClockLogicTest {

    private val t0 = 1_000_000_000L

    // ── start ─────────────────────────────────────────────────────────────────

    @Test
    fun start_setsRunningAndTimestamps() {
        val initial = ClockState.initial(TimeControlMode.Fischer(baseMs = 60_000L, incrementMs = 2_000L))
        val started = ClockLogic.start(initial, WHITE, t0)

        assertTrue(started.running, "Clock should be running")
        assertEquals(WHITE, started.activeColor)
        assertEquals(t0, started.lastTickEpochMs)
        assertEquals(t0, started.activeMoveStartEpochMs)
    }

    @Test
    fun start_transfersOwnershipBetweenColors() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val white = ClockLogic.start(initial, WHITE, t0)
        val black = ClockLogic.start(white, BLACK, t0 + 5_000L)

        assertEquals(BLACK, black.activeColor)
        assertEquals(t0 + 5_000L, black.lastTickEpochMs)
        assertEquals(t0 + 5_000L, black.activeMoveStartEpochMs)
    }

    // ── tick: Unlimited ───────────────────────────────────────────────────────

    @Test
    fun tick_unlimited_doesNothing() {
        val initial = ClockState.initial(TimeControlMode.Unlimited)
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 10_000L)

        assertEquals(started, outcome.state)
        assertNull(outcome.timeoutColor)
    }

    // ── tick: pre-conditions ──────────────────────────────────────────────────

    @Test
    fun tick_notRunning_doesNothing() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val outcome = ClockLogic.tick(initial, t0 + 10_000L)

        assertEquals(initial, outcome.state)
        assertNull(outcome.timeoutColor)
    }

    @Test
    fun tick_zeroDelta_onlyUpdatesLastTick() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0)

        assertEquals(started.whiteRemainingMs, outcome.state.whiteRemainingMs)
        assertEquals(t0, outcome.state.lastTickEpochMs)
        assertNull(outcome.timeoutColor)
    }

    // ── tick: SuddenDeath ─────────────────────────────────────────────────────

    @Test
    fun tick_suddenDeath_subtractsDelta() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 5_000L)

        assertEquals(55_000L, outcome.state.whiteRemainingMs)
        assertEquals(60_000L, outcome.state.blackRemainingMs)
        assertNull(outcome.timeoutColor)
    }

    @Test
    fun tick_suddenDeath_timeoutAtZero() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(5_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 5_000L)

        assertEquals(0L, outcome.state.whiteRemainingMs)
        assertFalse(outcome.state.running)
        assertEquals(WHITE, outcome.timeoutColor)
    }

    @Test
    fun tick_suddenDeath_timeoutClampedWhenExceeded() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(5_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 10_000L)

        assertEquals(
            0L,
            outcome.state.whiteRemainingMs,
            "Remaining should be clamped to zero, not negative",
        )
        assertEquals(WHITE, outcome.timeoutColor)
    }

    // ── applyIncrement: Fischer ───────────────────────────────────────────────

    @Test
    fun applyIncrement_fischer_addsIncrementToMover() {
        val initial = ClockState.initial(TimeControlMode.Fischer(baseMs = 60_000L, incrementMs = 2_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 5_000L).state  // white: 55_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 5_000L)

        assertEquals(57_000L, result.whiteRemainingMs)
        assertEquals(60_000L, result.blackRemainingMs)
    }

    @Test
    fun applyIncrement_suddenDeath_isNoOp() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 5_000L).state  // white: 55_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 5_000L)

        assertEquals(55_000L, result.whiteRemainingMs)
    }

    @Test
    fun applyIncrement_unlimited_isNoOp() {
        val initial = ClockState.initial(TimeControlMode.Unlimited)
        val result = ClockLogic.applyIncrement(initial, WHITE, t0)

        assertEquals(initial, result)
    }

    // ── applyIncrement: Bronstein ─────────────────────────────────────────────

    @Test
    fun applyIncrement_bronstein_elapsedLessThanDelay_creditsFullElapsed() {
        // delay=3000, elapsed=2000 → credit=2000, net consumption = 0
        val initial = ClockState.initial(TimeControlMode.Bronstein(baseMs = 60_000L, delayMs = 3_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 2_000L).state  // white: 58_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 2_000L)

        assertEquals(
            60_000L,
            result.whiteRemainingMs,
            "Elapsed (2s) ≤ delay (3s) — full elapsed is credited back",
        )
    }

    @Test
    fun applyIncrement_bronstein_elapsedGreaterThanDelay_cappedAtDelay() {
        // delay=3000, elapsed=5000 → credit=3000 (capped), net consumption = 2s
        val initial = ClockState.initial(TimeControlMode.Bronstein(baseMs = 60_000L, delayMs = 3_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 5_000L).state  // white: 55_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 5_000L)

        assertEquals(
            58_000L,
            result.whiteRemainingMs,
            "Elapsed (5s) > delay (3s) — credit capped at delay; net cost = 2s",
        )
    }

    @Test
    fun applyIncrement_bronstein_elapsedEqualsDelay_creditsExact() {
        val initial = ClockState.initial(TimeControlMode.Bronstein(baseMs = 60_000L, delayMs = 3_000L))
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 3_000L).state  // white: 57_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 3_000L)

        assertEquals(60_000L, result.whiteRemainingMs)
    }

    // ── Byoyomi: base → byoyomi transition ────────────────────────────────────

    @Test
    fun tick_byoyomi_baseExhaustedTransitionsToByoyomiWithoutConsumingPeriod() {
        val mode = TimeControlMode.Byoyomi(baseMs = 5_000L, periodMs = 30_000L, periods = 3)
        val initial = ClockState.initial(mode)
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 5_000L)

        assertEquals(
            30_000L,
            outcome.state.whiteRemainingMs,
            "Remaining should reset to periodMs on base exhaustion",
        )
        assertTrue(outcome.state.whiteInByoyomi, "White should be in byoyomi phase")
        assertEquals(
            3,
            outcome.state.whiteByoyomiPeriodsLeft,
            "First byoyomi period is free — periods not decremented on transition",
        )
        assertNull(outcome.timeoutColor)
        assertTrue(outcome.state.running, "Clock must keep running after transition")
    }

    // ── Byoyomi: period exceeded ──────────────────────────────────────────────

    @Test
    fun tick_byoyomi_periodExceededConsumesPeriodAndResets() {
        val mode = TimeControlMode.Byoyomi(baseMs = 5_000L, periodMs = 30_000L, periods = 3)
        val initial = ClockState.initial(mode).copy(
            whiteRemainingMs = 30_000L,
            whiteInByoyomi = true,
        )
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 30_000L)

        assertEquals(30_000L, outcome.state.whiteRemainingMs)
        assertEquals(2, outcome.state.whiteByoyomiPeriodsLeft, "Period consumed")
        assertTrue(outcome.state.whiteInByoyomi)
        assertNull(outcome.timeoutColor)
    }

    @Test
    fun tick_byoyomi_lastPeriodExceededEmitsTimeout() {
        val mode = TimeControlMode.Byoyomi(baseMs = 5_000L, periodMs = 30_000L, periods = 3)
        val initial = ClockState.initial(mode).copy(
            whiteRemainingMs = 30_000L,
            whiteByoyomiPeriodsLeft = 1,
            whiteInByoyomi = true,
        )
        val started = ClockLogic.start(initial, WHITE, t0)
        val outcome = ClockLogic.tick(started, t0 + 30_000L)

        assertEquals(WHITE, outcome.timeoutColor)
        assertFalse(outcome.state.running)
        assertEquals(0, outcome.state.whiteByoyomiPeriodsLeft)
    }

    // ── Byoyomi: applyIncrement semantics ─────────────────────────────────────

    @Test
    fun applyIncrement_byoyomi_inBasePhaseDoesNotReset() {
        // White aún tiene base; completa un medio-movimiento. Remaining NO se reinicia.
        val mode = TimeControlMode.Byoyomi(baseMs = 60_000L, periodMs = 30_000L, periods = 3)
        val initial = ClockState.initial(mode)
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 10_000L).state  // white: 50_000, not in byoyomi
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 10_000L)

        assertEquals(50_000L, result.whiteRemainingMs, "Remaining untouched in base phase")
        assertFalse(result.whiteInByoyomi)
    }

    @Test
    fun applyIncrement_byoyomi_inByoyomiPhaseResetsToPeriodMs() {
        // White en byoyomi; completa dentro del periodo. Remaining resetea a periodMs.
        val mode = TimeControlMode.Byoyomi(baseMs = 60_000L, periodMs = 30_000L, periods = 3)
        val initial = ClockState.initial(mode).copy(
            whiteRemainingMs = 10_000L,
            whiteInByoyomi = true,
        )
        val started = ClockLogic.start(initial, WHITE, t0)
        val afterTick = ClockLogic.tick(started, t0 + 5_000L).state  // white: 5_000
        val result = ClockLogic.applyIncrement(afterTick, WHITE, t0 + 5_000L)

        assertEquals(30_000L, result.whiteRemainingMs, "Period resets to full periodMs")
        assertEquals(3, result.whiteByoyomiPeriodsLeft, "Periods unchanged on successful in-period move")
    }

    // ── Multi-tick integration ────────────────────────────────────────────────

    @Test
    fun tick_accumulatesAcrossMultipleCalls() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val s0 = ClockLogic.start(initial, WHITE, t0)
        val s1 = ClockLogic.tick(s0, t0 + 1_000L).state
        val s2 = ClockLogic.tick(s1, t0 + 2_500L).state
        val s3 = ClockLogic.tick(s2, t0 + 5_000L).state

        assertEquals(55_000L, s3.whiteRemainingMs)
        assertEquals(t0 + 5_000L, s3.lastTickEpochMs)
    }

    @Test
    fun tick_blackColorDoesNotAffectWhite() {
        val initial = ClockState.initial(TimeControlMode.SuddenDeath(60_000L))
        val started = ClockLogic.start(initial, BLACK, t0)
        val outcome = ClockLogic.tick(started, t0 + 5_000L)

        assertEquals(60_000L, outcome.state.whiteRemainingMs, "White untouched")
        assertEquals(55_000L, outcome.state.blackRemainingMs, "Black decremented")
    }
}
