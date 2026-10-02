package org.mocoserver.modtemplate.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QuantityRulesTest {
    @Test
    fun acceptsNonNegativeQuantities() {
        assertEquals(0, checkedQuantity(0))
        assertEquals(1, checkedQuantity(1))
    }

    @Test
    fun rejectsNegativeQuantities() {
        assertThrows(IllegalArgumentException::class.java) { checkedQuantity(-1) }
    }
}
