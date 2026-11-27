package com.medlens.ai.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerComparatorTest {

    @Test
    fun newerMinorOrPatch_returnsTrue() {
        assertTrue(SemVerComparator.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(SemVerComparator.isNewerVersion("v1.10.0", "1.2.0"))
        assertTrue(SemVerComparator.isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun sameOrOlderVersion_returnsFalse() {
        assertFalse(SemVerComparator.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(SemVerComparator.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(SemVerComparator.isNewerVersion("v0.9.5", "1.0.0"))
    }

    @Test
    fun stableRelease_beatsPreRelease() {
        assertTrue(SemVerComparator.isNewerVersion("v1.2.0", "1.2.0-beta.1"))
        assertFalse(SemVerComparator.isNewerVersion("v1.2.0-rc.1", "1.2.0"))
        assertEquals("1.2.3", SemVerComparator.normalize("  V1.2.3 "))
    }
}
