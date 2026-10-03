package org.mocoserver.sophisticatedgemcase.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GemFilterTest {
    private val ruby = GemKey("apotheosis:ruby", "flawed")
    private val sapphire = GemKey("apotheosis:sapphire", "flawed")
    private val pureRuby = GemKey("apotheosis:ruby", "pure")

    @Test
    fun matchesModesAndAndAxes() {
        assertTrue(GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet()).matches(ruby))
        assertFalse(GemFilter.of(GemFilterMode.INCLUDE, emptySet(), emptySet()).matches(ruby))
        assertTrue(GemFilter.of(GemFilterMode.EXCLUDE, emptySet(), emptySet()).matches(ruby))
        assertTrue(GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:ruby"), emptySet()).matches(ruby))
        assertFalse(GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:ruby"), emptySet()).matches(sapphire))
        assertTrue(GemFilter.of(GemFilterMode.INCLUDE, emptySet(), setOf("flawed")).matches(ruby))
        assertFalse(GemFilter.of(GemFilterMode.INCLUDE, emptySet(), setOf("flawed")).matches(pureRuby))
        assertTrue(GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:ruby"), setOf("flawed")).matches(ruby))
        assertFalse(GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:ruby"), setOf("pure")).matches(ruby))
        assertFalse(GemFilter.of(GemFilterMode.EXCLUDE, setOf("apotheosis:ruby"), setOf("flawed")).matches(ruby))
        assertTrue(GemFilter.of(GemFilterMode.EXCLUDE, setOf("apotheosis:ruby"), setOf("pure")).matches(ruby))
    }

    @Test
    fun ownsBothInputAndExposedSets() {
        val source = mutableSetOf("apotheosis:ruby")
        val filter = GemFilter.of(GemFilterMode.INCLUDE, source, emptySet())
        source.clear()
        assertTrue(filter.matches(ruby))
        assertThrows(UnsupportedOperationException::class.java) { (filter.gemIds as MutableSet<String>).clear() }
        assertTrue(filter.matches(ruby))
    }
}
