package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.Apoth
import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem
import dev.shadowsoffire.apotheosis.socket.gem.storage.GemCaseTile
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.items.ItemStackHandler
import net.p3pp3rf1y.sophisticatedbackpacks.api.IItemHandlerInteractionUpgrade
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.deposit.DepositUpgradeWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.restock.RestockUpgradeWrapper
import net.p3pp3rf1y.sophisticatedcore.upgrades.pickup.PickupUpgradeWrapper
import org.mocoserver.sophisticatedgemcase.ModTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** 対象の公開JARの契約を確認する調査用プローブ。本アドオンのGem機能の検収とは区別する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object IntegrationContractGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun gemCaseOverflowRequiresOwnStorage(helper: GameTestHelper) {
        val tile = gemCase(helper)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val stack = GemItem.createStack(gem, Purity.CRACKED, 2)
        val holder = UnsocketedGem.of(stack).gem()
        tile.depositGem(stack.copyWithCount(Short.MAX_VALUE.toInt() - 1))
        val handler = tile.getItemHandler(null)
        val before = tile.getCount(holder, Purity.CRACKED)
        handler.insertItem(0, stack, true)
        helper.assertTrue(tile.getCount(holder, Purity.CRACKED) == before, "Simulation changed Gem Case")
        val remainder = handler.insertItem(0, stack, false)
        helper.assertTrue(remainder.isEmpty, "Upstream overflow contract changed; review integration design")
        helper.assertTrue(
            tile.getCount(holder, Purity.CRACKED) == Short.MAX_VALUE.toInt(),
            "Unexpected native capacity",
        )
        helper.assertTrue(tile.upgradeMaterials.slots == 6, "Expected six material slots")
        helper.assertTrue(
            handler.insertItem(0, ItemStack(Apoth.Items.GEM_DUST), true).count == 1,
            "Automation must not insert processing materials",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun gemCaseUpgradeConsumesRecipeCostOnlyOnSuccess(helper: GameTestHelper) {
        val tile = gemCase(helper)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val stack = GemItem.createStack(gem, Purity.CRACKED, 2)
        val holder = UnsocketedGem.of(stack).gem()
        tile.depositGem(stack)
        helper.assertTrue(!tile.upgradeGem(holder, Purity.CHIPPED), "Upgrade without materials must fail")
        helper.assertTrue(tile.getCount(holder, Purity.CRACKED) == 2, "Failed upgrade consumed gems")
        tile.upgradeMaterials.setStackInSlot(0, ItemStack(Apoth.Items.GEM_DUST, 1))
        tile.upgradeMaterials.setStackInSlot(1, ItemStack(Apoth.Items.MYSTERIOUS_SCRAP_METAL, 3))
        helper.assertTrue(tile.upgradeGem(holder, Purity.CHIPPED), "Native cracked recipe must match")
        helper.assertTrue(
            tile.getCount(holder, Purity.CRACKED) == 0 && tile.getCount(holder, Purity.CHIPPED) == 1,
            "Expected two cracked gems to become one chipped gem",
        )
        helper.assertTrue(
            tile.upgradeMaterials.getStackInSlot(0).isEmpty && tile.upgradeMaterials.getStackInSlot(1).isEmpty,
            "Native recipe material cost changed",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun pickupReturnsRemainderAndRespectsSimulation(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
        val pickup = backpack.upgradeHandler.slotWrappers.getValue(0) as PickupUpgradeWrapper
        pickup.filterLogic.setAllowList(false)
        val inventory = backpack.inventoryHandler
        repeat(inventory.slots) { inventory.setStackInSlot(it, ItemStack(Items.STONE, 64)) }
        val incoming = ItemStack(Items.IRON_INGOT, 2)
        helper.assertTrue(pickup.pickup(helper.level, incoming, false).count == 2, "Full inventory lost remainder")
        inventory.setStackInSlot(0, ItemStack.EMPTY)
        helper.assertTrue(pickup.pickup(helper.level, incoming, true).isEmpty, "Simulation must accept two items")
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty, "Simulation changed inventory")
        helper.assertTrue(pickup.pickup(helper.level, incoming, false).isEmpty, "Actual pickup failed")
        helper.assertTrue(inventory.getStackInSlot(0).count == 2 && incoming.count == 2, "Unexpected input mutation")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun fullInventoryCanSkipBeforeInsertHook(helper: GameTestHelper) {
        val backpack = backpack()
        val inventory = backpack.inventoryHandler
        helper.assertTrue(inventory.slots > 0, "Probe requires a real initialized backpack inventory")
        repeat(inventory.slots) { inventory.setStackInSlot(it, ItemStack(Items.STONE, 64)) }
        val calls = AtomicInteger()
        val remainder =
            inventory.slotTracker.insertItemIntoHandler(
                inventory,
                { _, _ ->
                    calls.incrementAndGet()
                    ItemStack.EMPTY
                },
                { slot, stack, simulate -> inventory.insertItem(slot, stack, simulate) },
                { it },
                { it },
                ItemStack(Items.IRON_INGOT, 2),
                true,
            )
        helper.assertTrue(
            calls.get() == 0 && remainder.count == 2,
            "Upstream shortcut changed; review insertion design",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun depositAndRestockExposeSeparateTransferMethods(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
        helper.assertTrue(
            backpack.upgradeHandler.getWrappersThatImplement(IItemHandlerInteractionUpgrade::class.java).size == 2,
            "Interaction upgrade discovery changed",
        )
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        val restock = backpack.upgradeHandler.slotWrappers.getValue(1) as RestockUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        restock.filterLogic.setAllowList(false)
        backpack.inventoryHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT, 4))
        val target = ItemStackHandler(1)
        deposit.depositToHandler(target)
        helper.assertTrue(target.getStackInSlot(0).count == 4, "Deposit transfer failed")
        helper.assertTrue(backpack.inventoryHandler.getStackInSlot(0).isEmpty, "Deposit duplicated items")
        restock.restockFromHandler(target)
        helper.assertTrue(target.getStackInSlot(0).isEmpty, "Restock did not extract")
        helper.assertTrue(backpack.inventoryHandler.getStackInSlot(0).count == 4, "Restock transfer failed")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun nativeConversionCreatesTargetWithoutStoredComponents(helper: GameTestHelper) {
        val source = ItemStack(BackpackItems.STACK_UPGRADE_TIER_1.get())
        val tag = CompoundTag().apply { putInt("probe_gem_count", 37) }
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        val conversion = BackpackItems.STACK_UPGRADE_TIER_1_TO_TIER_2_CONVERSION.get()
        helper.assertTrue(conversion.canConvert(source), "Expected native conversion to accept source")
        val target = conversion.targetUpgradeStack
        helper.assertTrue(target.`is`(BackpackItems.STACK_UPGRADE_TIER_2.get()), "Incorrect target upgrade")
        helper.assertTrue(!target.has(DataComponents.CUSTOM_DATA), "Upstream now copies data; review conversion design")
        helper.assertTrue(
            source.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt("probe_gem_count") == 37,
            "Source changed",
        )
        helper.succeed()
    }

    private fun gemCase(helper: GameTestHelper): GemCaseTile {
        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, Apoth.Blocks.GEM_CASE.value())
        return helper.getBlockEntity(pos) as GemCaseTile
    }

    private fun backpack(): BackpackWrapper =
        BackpackWrapper(ItemStack(BackpackItems.DIAMOND_BACKPACK.get())).apply {
            // 新規バッグはUUID未付与だと空のUpgradeHandlerを返すため、実インベントリを初期化する。
            setContentsUuid(UUID.randomUUID())
        }
}
