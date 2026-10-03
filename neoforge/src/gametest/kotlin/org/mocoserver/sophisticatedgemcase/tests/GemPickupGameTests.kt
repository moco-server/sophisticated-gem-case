package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.upgrades.IPickupResponseUpgrade
import net.p3pp3rf1y.sophisticatedcore.upgrades.pickup.PickupUpgradeWrapper
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import java.util.UUID
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** Pickupはnative response upgradeと既存filterを通った後、処理用handlerから専用収納へ届く。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemPickupGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun enabledPickupRoutesGemAfterItsFilterAndPreservesSimulation(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val pickup = backpack.upgradeHandler.slotWrappers.getValue(0) as PickupUpgradeWrapper
        pickup.filterLogic.setAllowList(false)
        val gem = standardGem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val initial = GemCounts.of(mapOf(key to GemCounts.CAPACITY - 1))
        helper.assertTrue(case.replace(GemCounts.of(emptyMap()), initial), "Could not prepare one free dedicated slot")
        fillInventory(backpack)

        val simulated =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem.copy(),
                true,
            )
        helper.assertTrue(
            simulated.count == 2,
            "Simulation must leave two items outside the one free dedicated capacity",
        )
        helper.assertTrue(
            case.snapshot() is GemStorageState.Ready && (case.snapshot() as GemStorageState.Ready).counts == initial,
            "Pickup simulation changed dedicated storage",
        )
        val remainder =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem,
                false,
            )
        helper.assertTrue(
            remainder.count == 2,
            "Native pickup must preserve the remainder after one dedicated acceptance",
        )
        helper.assertTrue(
            (case.snapshot() as GemStorageState.Ready).counts.entries()[key] == GemCounts.CAPACITY,
            "Pickup did not fill exactly one dedicated slot",
        )
        helper.assertTrue(gem.count == 3, "Pickup changed the caller's input stack")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun gemCaseDoesNotBecomePickupUpgradeByItself(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.gemCaseUpgrade.get()))
        helper.assertTrue(
            backpack.upgradeHandler.getWrappersThatImplement(IPickupResponseUpgrade::class.java).isEmpty(),
            "Gem Case must not advertise a pickup response upgrade",
        )
        val gem = standardGem(2)
        val remainder =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem,
                false,
            )
        helper.assertTrue(remainder.count == 2, "Gem Case alone must not collect items")
        helper.assertTrue(
            (
                requireNotNull(
                    GemCaseUpgradeItem.find(backpack),
                ).snapshot() as GemStorageState.Ready
            ).counts.entries().isEmpty(),
            "Gem Case alone changed its storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun disabledPickupIsOmittedFromNativePickupEnumeration(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val pickup = backpack.upgradeHandler.slotWrappers.getValue(0) as PickupUpgradeWrapper
        pickup.setEnabled(false)
        helper.assertTrue(
            backpack.upgradeHandler.getWrappersThatImplement(IPickupResponseUpgrade::class.java).isEmpty(),
            "Disabled Pickup must not be returned by native enabled-wrapper enumeration",
        )
        val gem = standardGem(2)
        val remainder =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem,
                false,
            )
        helper.assertTrue(remainder.count == 2, "Disabled Pickup must not route a Gem")
        helper.assertTrue(
            (
                requireNotNull(
                    GemCaseUpgradeItem.find(backpack),
                ).snapshot() as GemStorageState.Ready
            ).counts.entries().isEmpty(),
            "Disabled Pickup changed dedicated storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectedNativePickupFilterDoesNotRouteToDedicatedStorage(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val pickup = backpack.upgradeHandler.slotWrappers.getValue(0) as PickupUpgradeWrapper
        pickup.filterLogic.setAllowList(true)
        pickup.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
        val gem = standardGem(2)
        val remainder =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem,
                false,
            )
        helper.assertTrue(remainder.count == 2, "Native Pickup filter rejection must preserve the whole Gem stack")
        helper.assertTrue(
            (
                requireNotNull(
                    GemCaseUpgradeItem.find(backpack),
                ).snapshot() as GemStorageState.Ready
            ).counts.entries().isEmpty(),
            "Rejected pickup changed dedicated storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun gemWithExtraComponentsStaysInNormalInventory(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val pickup = backpack.upgradeHandler.slotWrappers.getValue(0) as PickupUpgradeWrapper
        pickup.filterLogic.setAllowList(false)
        val gem = standardGem(2).apply { set(DataComponents.CUSTOM_NAME, Component.literal("marked")) }
        val remainder =
            InventoryHelper.runPickupOnPickupResponseUpgrades(
                helper.level,
                backpack.upgradeHandler,
                gem,
                false,
            )
        helper.assertTrue(
            remainder.isEmpty,
            "An allowed Gem with extra components should fall through to normal inventory",
        )
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).count == 2,
            "Extra-component Gem was not retained by normal storage",
        )
        helper.assertTrue(
            (
                requireNotNull(
                    GemCaseUpgradeItem.find(backpack),
                ).snapshot() as GemStorageState.Ready
            ).counts.entries().isEmpty(),
            "Extra-component Gem entered dedicated storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun publicIoSimulationAndActualInsertShareOneDedicatedReservation(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.gemCaseUpgrade.get()))
        val gem = standardGem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val initial = GemCounts.of(mapOf(key to GemCounts.CAPACITY - 1))
        helper.assertTrue(case.replace(GemCounts.of(emptyMap()), initial), "Could not prepare one free dedicated slot")
        fillInventory(backpack)

        val handler = backpack.inventoryForInputOutput
        val simulated = handler.insertItem(gem, true)
        helper.assertTrue(
            simulated.count == 2,
            "Nested simulation must expose the same two-item remainder as the real insert",
        )
        helper.assertTrue(
            (case.snapshot() as GemStorageState.Ready).counts == initial,
            "Nested simulation changed dedicated storage",
        )
        val remainder = handler.insertItem(gem, false)
        helper.assertTrue(remainder.count == 2, "Real insert must preserve the same two-item remainder")
        helper.assertTrue(
            (case.snapshot() as GemStorageState.Ready).counts.entries()[key] == GemCounts.CAPACITY,
            "Expected exactly one accepted Gem",
        )
        helper.assertTrue(gem.count == 3, "Public IO changed the caller's input stack")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun routingContextNestsByBagAndAlwaysClearsAfterFailure(helper: GameTestHelper) {
        val first = backpack()
        val sameBag =
            BackpackWrapper(ItemStack(BackpackItems.DIAMOND_BACKPACK.get())).apply {
                setContentsUuid(requireNotNull(first.contentsUuid.orElse(null)))
            }
        val other = backpack()
        helper.assertTrue(!GemOperationContext.routingSuppressed(first), "Routing context leaked before entry")

        GemOperationContext.withoutRouting(first) {
            helper.assertTrue(
                GemOperationContext.routingSuppressed(sameBag),
                "Wrappers with the same UUID must share routing suppression",
            )
            helper.assertTrue(
                !GemOperationContext.routingSuppressed(other),
                "Routing suppression crossed to a different bag",
            )
            GemOperationContext.withoutRouting(other) {
                helper.assertTrue(GemOperationContext.routingSuppressed(first), "Nested context hid its outer bag")
                helper.assertTrue(
                    GemOperationContext.routingSuppressed(other),
                    "Nested context did not suppress its own bag",
                )
            }
            helper.assertTrue(
                !GemOperationContext.routingSuppressed(other),
                "Nested context did not restore the previous stack",
            )
        }
        helper.assertTrue(
            !GemOperationContext.routingSuppressed(first),
            "Routing context remained after normal completion",
        )
        try {
            GemOperationContext.withoutRouting(first) { throw IllegalStateException("probe") }
        } catch (_: IllegalStateException) {
            // 例外後のThreadLocal解除を確認するため、GameTestを継続する。
        }
        helper.assertTrue(!GemOperationContext.routingSuppressed(first), "Routing context remained after an exception")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun lockedAndClientSummarySettingsDisableAutomaticDedicatedRouting(helper: GameTestHelper) {
        for (clientSummary in listOf(false, true)) {
            val processingBackpack = backpack()
            processingBackpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
            processingBackpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
            val processingCase = requireNotNull(GemCaseUpgradeItem.find(processingBackpack))
            lockSettings(processingCase, clientSummary)
            val pickupRemainder =
                InventoryHelper.runPickupOnPickupResponseUpgrades(
                    helper.level,
                    processingBackpack.upgradeHandler,
                    standardGem(2),
                    false,
                )
            helper.assertTrue(
                pickupRemainder.isEmpty,
                "Locked settings should leave valid Pickup items to normal storage",
            )
            helper.assertTrue(
                processingBackpack.inventoryHandler.getStackInSlot(0).count == 2,
                "Locked settings blocked normal Pickup storage",
            )
            helper.assertTrue(
                (processingCase.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
                "Locked settings routed Pickup into dedicated storage",
            )

            val ioBackpack = backpack()
            ioBackpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.gemCaseUpgrade.get()))
            val ioCase = requireNotNull(GemCaseUpgradeItem.find(ioBackpack))
            lockSettings(ioCase, clientSummary)
            val ioRemainder = ioBackpack.inventoryForInputOutput.insertItem(standardGem(2), false)
            helper.assertTrue(
                ioRemainder.isEmpty,
                "Locked settings should leave valid public IO items to normal storage",
            )
            helper.assertTrue(
                ioBackpack.inventoryHandler.getStackInSlot(0).count == 2,
                "Locked settings blocked normal public IO storage",
            )
            helper.assertTrue(
                (ioCase.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
                "Locked settings routed public IO into dedicated storage",
            )
        }
        helper.succeed()
    }

    private fun standardGem(count: Int): ItemStack {
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        return GemItem.createStack(gem, Purity.CRACKED, count)
    }

    private fun backpack(): BackpackWrapper =
        BackpackWrapper(ItemStack(BackpackItems.DIAMOND_BACKPACK.get())).apply {
            setContentsUuid(UUID.randomUUID())
        }

    private fun fillInventory(backpack: BackpackWrapper) {
        repeat(
            backpack.inventoryHandler.slots,
        ) { slot -> backpack.inventoryHandler.setStackInSlot(slot, ItemStack(Items.STONE, 64)) }
    }

    private fun lockSettings(
        wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        clientSummary: Boolean,
    ) {
        val raw =
            CompoundTag().apply {
                if (clientSummary) {
                    putBoolean("client_summary", true)
                    putBoolean("locked", false)
                } else {
                    putInt("schema_version", 2)
                }
            }
        wrapper.upgradeStack.set(ModDataComponents.gemTransferSettings.get(), CustomData.of(raw))
    }
}
