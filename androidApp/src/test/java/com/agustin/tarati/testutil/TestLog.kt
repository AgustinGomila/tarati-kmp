package com.agustin.tarati.testutil

/**
 * Console sink for test diagnostics — tournament tables, regression reports, depth
 * sweeps and the like. The output is human-readable and captured by JUnit in the
 * report's `<system-out>`, so it must reach standard output.
 *
 * Centralizing the console write in a single point keeps the `ReplacePrintlnWithLogging`
 * inspection satisfied at every call site (tests use [info] instead of `println`) and
 * leaves one seam to route through a real logger (SLF4J/JUL) if ever needed, without
 * touching callers.
 */
object TestLog {
    /** Writes [message] (default empty = blank line) to the test console. */
    @Suppress("ReplacePrintlnWithLogging")
    fun info(message: Any? = ""): Unit = println(message)
}
