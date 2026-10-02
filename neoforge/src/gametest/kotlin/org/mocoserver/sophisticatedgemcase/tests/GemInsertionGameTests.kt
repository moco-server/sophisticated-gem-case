package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.storage.GemInputOutputHandler
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.GemTransfers
import org.mocoserver.sophisticatedgemcase.storage.StandardGem

/** 通常収納が満杯でも専用へ投入し、超過分と拒否条件を保存する契約。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemInsertionGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun prioritizesDedicatedStorageWithRemainderAndSimulation(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val incoming = GemItem.createStack(gem, Purity.CRACKED, 3)
        val key = requireNotNull(StandardGem.keyOf(incoming))
        repeat(
            backpack.inventoryHandler.slots,
        ) { backpack.inventoryHandler.setStackInSlot(it, ItemStack(Items.STONE, 64)) }
        val io = backpack.inventoryForInputOutput
        helper.assertTrue(io.insertItem(0, incoming, true).isEmpty, "Simulation did not see dedicated space")
        helper.assertTrue((wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 0, "Simulation saved data")
        helper.assertTrue(io.insertItem(incoming, false).isEmpty, "Full normal inventory blocked dedicated storage")
        val before = (wrapper.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(wrapper.replace(before, GemCounts.of(mapOf(key to 32_766))), "Cannot prepare capacity")
        helper.assertTrue(io.insertItem(0, incoming, false).count == 2, "Overflow lost remainder")
        helper.assertTrue(incoming.count == 3, "Incoming stack was changed")
        backpack.inventoryHandler.setStackInSlot(0, ItemStack.EMPTY)
        helper.assertTrue(io.insertItem(0, incoming, false).isEmpty, "Overflow did not enter normal inventory")
        helper.assertTrue(backpack.inventoryHandler.getStackInSlot(0).count == 3, "Unexpected normal count")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun keepsRejectedSlotsAndCustomComponentsOutOfDedicatedStorage(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val base = backpack.inventoryForInputOutput
        val rejecting =
            object : net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler by base {
                override fun isItemValid(
                    slot: Int,
                    stack: ItemStack,
                ): Boolean = false

                override fun insertItem(
                    slot: Int,
                    stack: ItemStack,
                    simulate: Boolean,
                ): ItemStack = stack
            }
        val io = GemInputOutputHandler(backpack, rejecting)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val incoming = GemItem.createStack(gem, Purity.CRACKED, 2)
        helper.assertTrue(io.insertItem(0, incoming, false).count == 2, "Rejected slot bypassed")
        helper.assertTrue(
            (wrapper.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
            "Rejected input saved",
        )
        incoming.set(
            net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component
                .literal("Keep me"),
        )
        helper.assertTrue(base.insertItem(0, incoming, false).isEmpty, "Custom gem should use normal storage")
        helper.assertTrue(
            ItemStack.isSameItemSameComponents(backpack.inventoryHandler.getStackInSlot(0), incoming),
            "Custom data lost",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun movesOnlyAcceptableCountsFromNormalSlots(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val incoming = GemItem.createStack(gem, Purity.CRACKED, 3)
        val key = requireNotNull(StandardGem.keyOf(incoming))
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 32_766))),
            "Cannot prepare capacity",
        )
        backpack.inventoryHandler.setStackInSlot(0, incoming)
        helper.assertTrue(GemTransfers.moveFromNormal(backpack) == 1, "Bulk move exceeded capacity")
        helper.assertTrue(backpack.inventoryHandler.getStackInSlot(0).count == 2, "Bulk move lost remainder")
        helper.assertTrue(
            (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 32_767,
            "Dedicated count incorrect",
        )
        helper.succeed()
    }
}
