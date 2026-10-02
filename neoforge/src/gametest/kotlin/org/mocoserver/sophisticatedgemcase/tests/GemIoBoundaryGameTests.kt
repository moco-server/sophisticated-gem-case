package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.CachedFailedInsertInventoryHandler
import net.p3pp3rf1y.sophisticatedcore.upgrades.filter.FilterUpgradeWrapper
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import java.util.UUID
import java.util.function.LongSupplier
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** 固定版Backpacksの公開Item IO経路とCoreのIO制限を保つ。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemIoBoundaryGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun keepsFreshBackpackItemCapabilityEmptyUntilItHasUuid(helper: GameTestHelper) {
        val stack = ItemStack(BackpackItems.DIAMOND_BACKPACK.get())
        val wrapper = BackpackWrapper.fromStack(stack)
        helper.assertTrue(wrapper.contentsUuid.isEmpty, "Fresh backpack unexpectedly has a storage UUID")
        val emptyHandler = requireNotNull(stack.getCapability(Capabilities.ItemHandler.ITEM))
        helper.assertTrue(emptyHandler.slots == 0, "UUID-less backpack capability exposed inventory slots")

        wrapper.setContentsUuid(UUID.randomUUID())
        val backpack = wrapper as BackpackWrapper
        val storage = GemTestFixtures.install(backpack)
        val handler = requireNotNull(stack.getCapability(Capabilities.ItemHandler.ITEM))
        val gem = gem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        helper.assertTrue(handler.slots > 0, "UUID-backed backpack capability remained empty")
        helper.assertTrue(handler.insertItem(0, gem, false).isEmpty, "UUID-backed public IO rejected dedicated input")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == 2,
            "UUID-backed item capability bypassed dedicated storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun honorsNativeFilterUpgradeWhenRoutingDedicatedInput(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val storage = GemTestFixtures.install(backpack)
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(BackpackItems.FILTER_UPGRADE.get()))
        val filter = backpack.upgradeHandler.slotWrappers.getValue(1) as FilterUpgradeWrapper
        filter.filterLogic.setAllowList(true)
        filter.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.STONE))
        backpack.refreshInventoryForInputOutput()

        val io = backpack.inventoryForInputOutput
        val gem = gem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val remainder = io.insertItem(0, gem, false)
        helper.assertTrue(remainder.count == gem.count, "Core input filter rejection was bypassed")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == 0,
            "Filter-rejected gem entered dedicated storage",
        )
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).isEmpty,
            "Filter-rejected gem entered normal storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesNativeFailedInsertCacheAndRetriesNextTick(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val storage = GemTestFixtures.install(backpack)
        val inventory = backpack.inventoryHandler
        repeat(inventory.slots) { inventory.setStackInSlot(it, ItemStack(Items.STONE, 64)) }
        val gem = gem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        helper.assertTrue(
            storage.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to GemCounts.CAPACITY))),
            "Cannot fill dedicated storage",
        )

        var tick = helper.level.gameTime
        val publicIo = backpack.inventoryForInputOutput
        val cachedIo =
            CachedFailedInsertInventoryHandler(
                { publicIo },
                LongSupplier { tick },
            )
        val initialCount = (storage.snapshot() as GemStorageState.Ready).counts.count(key)
        val simulated = cachedIo.insertItem(0, gem, true)
        helper.assertTrue(simulated.count == gem.count && gem.count == 3, "Simulation changed its input")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == initialCount,
            "Simulation changed dedicated storage",
        )

        val failed = cachedIo.insertItem(0, gem, false)
        helper.assertTrue(failed === gem, "Native handler did not return the original failed stack reference")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == initialCount,
            "Full inventories accepted a failed insertion",
        )
        helper.assertTrue(
            storage.replace(
                GemCounts.of(mapOf(key to GemCounts.CAPACITY)),
                GemCounts.of(mapOf(key to GemCounts.CAPACITY - 1)),
            ),
            "Cannot open dedicated capacity after cached failure",
        )
        val cachedFailure = cachedIo.insertItem(0, gem, false)
        helper.assertTrue(cachedFailure === gem, "Same-tick failed stack did not remain cached")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == GemCounts.CAPACITY - 1,
            "Same-tick cache retry consumed newly available capacity",
        )

        tick++
        val retried = cachedIo.insertItem(0, gem, false)
        helper.assertTrue(retried.count == gem.count - 1, "Next-tick insertion did not retry with dedicated capacity")
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.count(key) == GemCounts.CAPACITY,
            "Next-tick retry did not consume exactly one available slot",
        )
        helper.assertTrue(inventory.getStackInSlot(0).count == 64, "Retry changed the full normal inventory")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun respectsBlockedDirectionBeforePublicIoWrapping(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        GemTestFixtures.install(backpack)
        val position = net.minecraft.core.BlockPos(1, 1, 1)
        helper.setBlock(
            position,
            net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks.BACKPACK
                .get(),
        )
        val tile = helper.getBlockEntity(position) as net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity
        tile.setBackpack(backpack.backpack)
        helper.setBlock(position.above(), net.minecraft.world.level.block.Blocks.STONE)
        val forbidden =
            net.p3pp3rf1y.sophisticatedbackpacks.Config.SERVER.noConnectionBlocks
                .isBlockConnectionDisallowed(net.minecraft.world.level.block.Blocks.STONE)
        val capability =
            helper.level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(position),
                net.minecraft.core.Direction.UP,
            )
        // 既定設定と拒否設定の両方で、外側の方向制限を迂回しないことを確認する。
        helper.assertTrue((capability == null) == forbidden, "Direction restriction changed by dedicated routing")
        val storage =
            tile.backpackWrapper.upgradeHandler.slotWrappers
                .getValue(0) as
                org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
        helper.assertTrue(
            (storage.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
            "Capability lookup changed dedicated contents",
        )
        helper.succeed()
    }

    private fun gem(count: Int): ItemStack =
        GemItem.createStack(
            GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED },
            Purity.CRACKED,
            count,
        )
}
