package org.mocoserver.sophisticatedgemcase.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GemCountsTest {
    private val key = GemKey("apotheosis:probe", "cracked")

    @Test
    fun keepsRemainderAtCapacityWithoutChangingSource() {
        val before = GemCounts.of(mapOf(key to 32_766))
        val change = before.insert(key, 2)
        assertEquals(1, change.moved)
        assertEquals(1, change.remainder)
        assertEquals(32_767, change.state.count(key))
        assertEquals(32_766, before.count(key))
        assertEquals(Int.MAX_VALUE, change.state.insert(key, Int.MAX_VALUE).remainder)
    }

    @Test
    fun keepsKeysIndependentAndRemovesZeroCounts() {
        val second = key.copy(purity = "chipped")
        val counts = GemCounts.of(mapOf(key to 32_767, second to 1))
        val change = counts.extract(second, 2)
        assertEquals(1, change.moved)
        assertEquals(1, change.remainder)
        assertEquals(0, change.state.count(second))
        assertEquals(mapOf(key to 32_767), change.state.entries())
        assertEquals(0, counts.insert(second, 0).moved)
    }

    @Test
    fun rejectsInvalidNumbersAndOwnsItsData() {
        val input = mutableMapOf(key to 1)
        val counts = GemCounts.of(input)
        input.clear()
        assertEquals(1, counts.count(key))
        assertThrows(UnsupportedOperationException::class.java) {
            (counts.entries() as MutableMap).clear()
        }
        assertThrows(IllegalArgumentException::class.java) { counts.insert(key, -1) }
        assertThrows(IllegalArgumentException::class.java) { counts.extract(key, -1) }
        assertThrows(IllegalArgumentException::class.java) { GemCounts.of(mapOf(key to 32_768)) }
        assertThrows(IllegalArgumentException::class.java) { GemCounts.of(mapOf(key to -1)) }
        assertEquals(emptyMap<GemKey, Int>(), GemCounts.of(mapOf(key to 0)).entries())
    }
}
