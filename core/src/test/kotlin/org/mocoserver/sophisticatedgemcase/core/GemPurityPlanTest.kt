package org.mocoserver.sophisticatedgemcase.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GemPurityPlanTest {
    private val gemId = "apotheosis:probe"
    private val materials =
        requireNotNull(
            GemMaterialAllocator.allocate(
                listOf(GemMaterialCandidate(0, 2, matchesLeft = true, matchesRight = true)),
                leftRequired = 1,
                rightRequired = 1,
            ),
        )

    @Test
    fun plansTwoInputsAndOneOutputWithoutChangingOriginal() {
        val input = GemKey(gemId, "chipped")
        val output = GemKey(gemId, "flawed")
        val counts = GemCounts.of(mapOf(input to 3))

        val plan = requireNotNull(GemPurityPlanner.plan(counts, input, output, materials))

        assertEquals(counts, GemCounts.of(mapOf(input to 3)))
        assertEquals(3, counts.count(input))
        assertEquals(1, plan.counts.count(input))
        assertEquals(1, plan.counts.count(output))
        assertEquals(materials, plan.materials)
    }

    @Test
    fun refusesInputShortageAndFullOutput() {
        val input = GemKey(gemId, "chipped")
        val output = GemKey(gemId, "flawed")

        assertNull(GemPurityPlanner.plan(GemCounts.of(mapOf(input to 1)), input, output, materials))
        assertNull(
            GemPurityPlanner.plan(
                GemCounts.of(mapOf(input to 2, output to GemCounts.CAPACITY)),
                input,
                output,
                materials,
            ),
        )
    }

    @Test
    fun refusesSameKeyNonAdjacentAndUnknownPurities() {
        val chipped = GemKey(gemId, "chipped")
        val twoInputs = GemCounts.of(mapOf(chipped to 2))

        assertNull(GemPurityPlanner.plan(twoInputs, chipped, chipped, materials))
        assertNull(
            GemPurityPlanner.plan(
                twoInputs,
                chipped,
                GemKey(gemId, "normal"),
                materials,
            ),
        )
        assertNull(
            GemPurityPlanner.plan(
                twoInputs,
                GemKey(gemId, "unknown"),
                GemKey(gemId, "chipped"),
                materials,
            ),
        )
        assertNull(
            GemPurityPlanner.plan(
                twoInputs,
                chipped,
                GemKey(gemId, "unknown"),
                materials,
            ),
        )
    }

    @Test
    fun acceptsEveryAdjacentUpgradeAcrossKnownPurities() {
        val adjacentUpgrades =
            listOf(
                "cracked" to "chipped",
                "chipped" to "flawed",
                "flawed" to "normal",
                "normal" to "flawless",
                "flawless" to "perfect",
            )

        adjacentUpgrades.forEach { (inputPurity, outputPurity) ->
            val input = GemKey(gemId, inputPurity)
            val output = GemKey(gemId, outputPurity)
            val plan =
                GemPurityPlanner.plan(
                    GemCounts.of(mapOf(input to 2)),
                    input,
                    output,
                    materials,
                )

            assertNotEquals(null, plan, "$inputPurity to $outputPurity")
        }
    }

    @Test
    fun refusesPerfectAsInputAndCrackedAsOutput() {
        val perfect = GemKey(gemId, "perfect")
        val cracked = GemKey(gemId, "cracked")
        val input = GemKey(gemId, "cracked")

        assertNull(
            GemPurityPlanner.plan(GemCounts.of(mapOf(perfect to 2)), perfect, GemKey(gemId, "perfect"), materials),
        )
        assertNull(
            GemPurityPlanner.plan(GemCounts.of(mapOf(input to 2)), input, cracked, materials),
        )
    }

    @Test
    fun refusesDifferentGemKinds() {
        val input = GemKey(gemId, "chipped")
        val otherGemOutput = GemKey("apotheosis:amethyst", "flawed")

        assertNull(
            GemPurityPlanner.plan(
                GemCounts.of(mapOf(input to 2)),
                input,
                otherGemOutput,
                materials,
            ),
        )
    }
}
