package com.agustin.tarati.testutil

import com.agustin.tarati.testutil.TestLog.info


/**
 * Console sink for test diagnostics — regression reports, step-by-step traces and the
 * like. The output is human-readable and captured by the test runner, so it must reach
 * standard output.
 *
 * Centralizing the console write in a single point keeps the `ReplacePrintlnWithLogging`
 * inspection satisfied at every call site (tests use [info] instead of `println`) and
 * leaves one seam to route through a real logger if ever needed, without touching callers.
 */
object TestLog {
    /** Writes [message] (default empty = blank line) to the test console. */
    @Suppress("ReplacePrintlnWithLogging")
    fun info(message: Any? = ""): Unit = println(message)
}
