package com.aravind.spectra.dsp

import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the same accuracy self-test the app has, on the build server. */
class SelfTestTest {

    @Test
    fun allChecksPass() {
        val results = SelfTest.run { _, _ -> }
        assertTrue("self-test has no cases", results.isNotEmpty())
        val failed = results.filter { !it.pass }
        assertTrue(
            "Failed checks:\n" + failed.joinToString("\n") { "- ${it.name}: expected ${it.expected}, got ${it.measured}" },
            failed.isEmpty()
        )
    }
}
