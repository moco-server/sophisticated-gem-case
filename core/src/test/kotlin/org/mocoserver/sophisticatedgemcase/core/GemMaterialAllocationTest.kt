package org.mocoserver.sophisticatedgemcase.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GemMaterialAllocationTest {
    @Test
    fun protectsRightAndUsesNormalLeftOnlyMaterial() {
        val allocation =
            requireNotNull(
                GemMaterialAllocator.allocate(
                    listOf(
                        GemMaterialCandidate(0, 1, matchesLeft = true, matchesRight = true),
                        GemMaterialCandidate(6, 1, matchesLeft = true, matchesRight = false),
                    ),
                    leftRequired = 1,
                    rightRequired = 1,
                ),
            )

        assertEquals(mapOf(0 to 1, 6 to 1), allocation.uses)
        assertEquals(mapOf(6 to 1), allocation.leftUses)
        assertEquals(mapOf(0 to 1), allocation.rightUses)
    }

    @Test
    fun spendsEarlierLeftOnlyCandidatesBeforeSharedCandidates() {
        val allocation =
            requireNotNull(
                GemMaterialAllocator.allocate(
                    listOf(
                        GemMaterialCandidate(0, 2, matchesLeft = true, matchesRight = false),
                        GemMaterialCandidate(1, 3, matchesLeft = true, matchesRight = true),
                        GemMaterialCandidate(2, 2, matchesLeft = false, matchesRight = true),
                    ),
                    leftRequired = 4,
                    rightRequired = 3,
                ),
            )

        assertEquals(mapOf(0 to 2, 1 to 2), allocation.leftUses)
        assertEquals(mapOf(1 to 1, 2 to 2), allocation.rightUses)
        assertEquals(mapOf(0 to 2, 1 to 3, 2 to 2), allocation.uses)
    }

    @Test
    fun returnsNullWhenEitherSideCannotBeSatisfied() {
        assertEquals(
            null,
            GemMaterialAllocator.allocate(
                listOf(GemMaterialCandidate(0, 1, matchesLeft = true, matchesRight = false)),
                leftRequired = 2,
                rightRequired = 0,
            ),
        )
        assertEquals(
            null,
            GemMaterialAllocator.allocate(
                listOf(GemMaterialCandidate(0, 1, matchesLeft = true, matchesRight = false)),
                leftRequired = 1,
                rightRequired = 1,
            ),
        )
    }

    @Test
    fun usesLongTotalsForLargeCandidateCounts() {
        val allocation =
            requireNotNull(
                GemMaterialAllocator.allocate(
                    listOf(
                        GemMaterialCandidate(0, Int.MAX_VALUE, matchesLeft = true, matchesRight = false),
                        GemMaterialCandidate(1, Int.MAX_VALUE, matchesLeft = false, matchesRight = true),
                    ),
                    leftRequired = Int.MAX_VALUE,
                    rightRequired = Int.MAX_VALUE,
                ),
            )

        assertEquals(mapOf(0 to Int.MAX_VALUE), allocation.leftUses)
        assertEquals(mapOf(1 to Int.MAX_VALUE), allocation.rightUses)
        assertEquals(mapOf(0 to Int.MAX_VALUE, 1 to Int.MAX_VALUE), allocation.uses)
    }

    @Test
    fun copiesInputAndReturnedMapsAreReadOnly() {
        val candidates = mutableListOf(GemMaterialCandidate(0, 2, matchesLeft = true, matchesRight = false))
        val allocation =
            requireNotNull(
                GemMaterialAllocator.allocate(candidates, leftRequired = 1, rightRequired = 0),
            )
        candidates.clear()

        assertEquals(mapOf(0 to 1), allocation.uses)
        assertThrows(UnsupportedOperationException::class.java) {
            (allocation.uses as MutableMap).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (allocation.leftUses as MutableMap).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (allocation.rightUses as MutableMap).clear()
        }
    }

    @Test
    fun rejectsNegativeSlotIndices() {
        assertThrows(IllegalArgumentException::class.java) {
            GemMaterialAllocator.allocate(
                listOf(GemMaterialCandidate(-1, 1, matchesLeft = true, matchesRight = false)),
                leftRequired = 1,
                rightRequired = 0,
            )
        }
    }

    @Test
    fun rejectsDuplicateSlotsAndNegativeRequirementsOrCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            GemMaterialAllocator.allocate(
                listOf(
                    GemMaterialCandidate(0, 1, matchesLeft = true, matchesRight = false),
                    GemMaterialCandidate(0, 1, matchesLeft = false, matchesRight = true),
                ),
                leftRequired = 1,
                rightRequired = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GemMaterialAllocator.allocate(emptyList(), leftRequired = -1, rightRequired = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GemMaterialAllocator.allocate(emptyList(), leftRequired = 0, rightRequired = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GemMaterialAllocator.allocate(
                listOf(GemMaterialCandidate(0, -1, matchesLeft = true, matchesRight = false)),
                leftRequired = 0,
                rightRequired = 0,
            )
        }
    }
}
