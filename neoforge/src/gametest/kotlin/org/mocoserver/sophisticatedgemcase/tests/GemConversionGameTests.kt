package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.items.ItemStackHandler
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.deposit.DepositUpgradeWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.restock.RestockUpgradeWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import net.p3pp3rf1y.sophisticatedcore.upgrades.pickup.PickupUpgradeWrapper
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.integration.GemCaseConversion
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** 装着した下位Gem Caseをnative menu操作からcomponentごと変換する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemConversionGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun convertsOccupiedLowerUpgradeAndConsumesOneCursorItem(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            player.abilities.instabuild = false
            val lower = wrapper.upgradeStack
            val counts = GemStorageCodec.write(GemCounts.of(mapOf(GemKey("future:kept", "normal") to 7)))
            val settings = GemSettingsCodec.write(GemTransferSettings.DEFAULT)
            val foreignData = CustomData.of(CompoundTag().apply { putString("other_mod_key", "preserved") })
            val customName = Component.literal("Keep every component")
            lower.set(ModDataComponents.gemStorage.get(), counts)
            lower.set(ModDataComponents.gemTransferSettings.get(), settings)
            lower.set(DataComponents.CUSTOM_DATA, foreignData)
            lower.set(DataComponents.CUSTOM_NAME, customName)
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
            val lowerContainer = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val oldSession = lowerContainer.sessionFor(menu)

            val slotId = menu.firstUpgradeSlot
            val conversionSlot = menu.getSlot(slotId) as? StorageContainerMenuBase<*>.StorageUpgradeSlot
            helper.assertTrue(conversionSlot != null, "Conversion slot was not native StorageUpgradeSlot")
            val nativeSlot = requireNotNull(conversionSlot)
            helper.assertTrue(
                menu.stillValid(player) && player.containerMenu === menu,
                "Conversion menu was not current and valid",
            )
            helper.assertTrue(
                (nativeSlot as Slot).index == slotId &&
                    nativeSlot.slotIndex == 0 &&
                    nativeSlot.getItemHandler() === menu.storageWrapper.upgradeHandler,
                "Conversion native/global/handler slot indexes did not match",
            )
            helper.assertTrue(
                nativeSlot === menu.upgradeSlots.first() && menu.getSlot(slotId) === nativeSlot,
                "Conversion slot identity was stale before click",
            )
            menu.clicked(slotId, 1, ClickType.PICKUP, player)

            val converted = menu.getSlot(slotId).item
            helper.assertTrue(
                converted.`is`(ModItems.advancedGemCaseUpgrade.get()),
                "Native menu click did not convert the occupied lower upgrade",
            )
            helper.assertTrue(
                converted.get(ModDataComponents.gemStorage.get()) == counts,
                "Gem counts were not inherited",
            )
            helper.assertTrue(
                converted.get(ModDataComponents.gemTransferSettings.get()) == settings,
                "Transfer settings were not inherited",
            )
            helper.assertTrue(
                converted.get(DataComponents.CUSTOM_DATA) == foreignData,
                "Foreign component was not inherited",
            )
            helper.assertTrue(converted.get(DataComponents.CUSTOM_NAME) == customName, "Custom name was not inherited")
            helper.assertTrue(
                menu.carried.`is`(ModItems.gemCaseUpgradeConversion.get()) && menu.carried.count == 1,
                "Conversion cursor must lose exactly one item",
            )
            helper.assertTrue(
                menu.storageWrapper.upgradeHandler
                    .getStackInSlot(0)
                    .`is`(ModItems.advancedGemCaseUpgrade.get()),
                "Upgrade slot was not persisted",
            )
            val upperContainer = menu.upgradeContainers.getValue(0) as? GemCaseContainer
            helper.assertTrue(
                upperContainer != null && upperContainer !== lowerContainer,
                "Upgrade tab was not rebuilt",
            )
            val newContainer = requireNotNull(upperContainer)
            val newSession = newContainer.sessionFor(menu)
            helper.assertTrue(newSession.id != oldSession.id, "Upgrade replacement reused the lower session")
            helper.assertTrue(newContainer.slots.size == 6, "Upper processing slots were not installed")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesLockedStorageAndSettingsRawWithoutAddingProcessingDefaults(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val futureCounts = CustomData.of(CompoundTag().apply { putInt("schema_version", 91) })
            val futureSettings = CustomData.of(CompoundTag().apply { putInt("schema_version", 93) })
            wrapper.upgradeStack.set(ModDataComponents.gemStorage.get(), futureCounts)
            wrapper.upgradeStack.set(ModDataComponents.gemTransferSettings.get(), futureSettings)
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 1)

            val slotId = menu.firstUpgradeSlot
            menu.clicked(slotId, 1, ClickType.PICKUP, player)

            val converted = menu.getSlot(slotId).item
            helper.assertTrue(
                converted.`is`(ModItems.advancedGemCaseUpgrade.get()),
                "Locked lower upgrade did not convert",
            )
            helper.assertTrue(
                converted.get(ModDataComponents.gemStorage.get()) == futureCounts,
                "Locked counts raw changed",
            )
            helper.assertTrue(
                converted.get(ModDataComponents.gemTransferSettings.get()) == futureSettings,
                "Locked transfer settings raw changed",
            )
            helper.assertTrue(
                !converted.has(ModDataComponents.gemProcessingMaterials.get()),
                "Conversion created materials",
            )
            helper.assertTrue(
                !converted.has(ModDataComponents.gemProcessingSettings.get()),
                "Conversion enabled refill",
            )
            helper.assertTrue(menu.carried.isEmpty, "Noncreative cursor was not consumed after conversion")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun refusesLowerUpgradeWithEitherUpperOnlyComponent(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val raw = CustomData.of(CompoundTag().apply { putInt("schema_version", 1) })
            val slotId = menu.firstUpgradeSlot
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)

            wrapper.upgradeStack.set(ModDataComponents.gemProcessingMaterials.get(), raw)
            val materialSource = wrapper.upgradeStack.copy()
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(
                ItemStack.matches(materialSource, menu.getSlot(slotId).item),
                "Material-bearing lower changed",
            )
            helper.assertTrue(menu.carried.count == 2, "Rejected material-bearing conversion consumed cursor")

            wrapper.upgradeStack.remove(ModDataComponents.gemProcessingMaterials.get())
            wrapper.upgradeStack.set(ModDataComponents.gemProcessingSettings.get(), raw)
            val settingsSource = wrapper.upgradeStack.copy()
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(
                ItemStack.matches(settingsSource, menu.getSlot(slotId).item),
                "Settings-bearing lower changed",
            )
            helper.assertTrue(menu.carried.count == 2, "Rejected settings-bearing conversion consumed cursor")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun ignoresLeftClickAndInvalidConversionCursor(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val slotId = menu.firstUpgradeSlot
            helper.assertTrue(
                wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(GemKey("future:kept", "normal") to 1))),
                "Could not seed lower Gem counts",
            )
            val source = wrapper.upgradeStack.copy()

            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
            val conversionCursor = menu.carried.copy()
            menu.clicked(slotId, 0, ClickType.PICKUP, player)
            helper.assertTrue(ItemStack.matches(source, menu.getSlot(slotId).item), "Left click changed lower upgrade")
            helper.assertTrue(
                ItemStack.matches(conversionCursor, menu.carried),
                "Left click consumed conversion cursor",
            )

            menu.carried = ItemStack(Items.IRON_INGOT, 2)
            val invalidCursor = menu.carried.copy()
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(
                ItemStack.matches(source, menu.getSlot(slotId).item),
                "Invalid cursor changed lower upgrade",
            )
            helper.assertTrue(ItemStack.matches(invalidCursor, menu.carried), "Invalid cursor was consumed")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsReentrantOrStaleMenuConversionWithoutMutation(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val slotId = menu.firstUpgradeSlot
            val source = wrapper.upgradeStack.copy()
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
            val cursor = menu.carried.copy()

            val reentrant =
                GemOperationContext.exclusive(menu.storageWrapper) {
                    GemCaseConversion.tryHandle(menu, slotId, 1, ClickType.PICKUP, player)
                }
            helper.assertTrue(reentrant == true, "Target click was not consumed during exclusive work")
            helper.assertTrue(
                ItemStack.matches(source, menu.getSlot(slotId).item),
                "Exclusive conversion changed upgrade",
            )
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Exclusive conversion changed cursor")

            player.containerMenu = player.inventoryMenu
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(
                ItemStack.matches(source, menu.getSlot(slotId).item),
                "Closed-menu conversion changed upgrade",
            )
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Closed-menu conversion changed cursor")
            helper.assertTrue(!GemOperationContext.isExclusive(menu.storageWrapper), "Exclusive context leaked")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsDistantMenuAndRestoresWhenBagUuidChangesDuringSave(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val backpack = menu.storageWrapper as BackpackWrapper
        val originalPosition = player.position()
        val slotId = menu.firstUpgradeSlot
        val source = wrapper.upgradeStack.copy()
        menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
        val cursor = menu.carried.copy()
        try {
            player.setPos(100.0, 100.0, 100.0)
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(ItemStack.matches(source, menu.getSlot(slotId).item), "Distant menu changed upgrade")
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Distant menu changed cursor")

            player.setPos(originalPosition.x, originalPosition.y, originalPosition.z)
            val originalUuid = backpack.contentsUuid.orElse(null)
            val saveCalls = AtomicInteger()
            installUpgradeSaveCallback(backpack.upgradeHandler) {
                if (saveCalls.incrementAndGet() == 1) backpack.setContentsUuid(UUID.randomUUID())
            }
            var failed = false
            try {
                menu.clicked(slotId, 1, ClickType.PICKUP, player)
            } catch (_: IllegalStateException) {
                failed = true
            }

            helper.assertTrue(failed, "UUID change during save was not rejected")
            helper.assertTrue(backpack.contentsUuid.orElse(null) != originalUuid, "Save callback did not change UUID")
            helper.assertTrue(
                ItemStack.matches(source, menu.getSlot(slotId).item),
                "UUID failure did not restore upgrade",
            )
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "UUID failure did not restore cursor")
            helper.assertTrue(
                !GemOperationContext.isExclusive(menu.storageWrapper),
                "UUID failure leaked exclusive context",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresSourceAndCursorWhenUpgradeSaveThrows(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val backpack = menu.storageWrapper as BackpackWrapper
        val source = wrapper.upgradeStack.copy()
        val slotId = menu.firstUpgradeSlot
        menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
        val cursor = menu.carried.copy()
        val callbacks = AtomicInteger()
        try {
            installUpgradeSaveCallback(backpack.upgradeHandler) {
                callbacks.incrementAndGet()
                throw IllegalStateException("save failure")
            }
            val failure =
                try {
                    menu.clicked(slotId, 1, ClickType.PICKUP, player)
                    null
                } catch (caught: RuntimeException) {
                    caught
                }

            helper.assertTrue(failure != null, "Save callback exception was swallowed")
            helper.assertTrue(
                requireNotNull(failure).suppressed.isNotEmpty(),
                "Restore callback failure was not attached",
            )
            helper.assertTrue(ItemStack.matches(source, menu.getSlot(slotId).item), "Source upgrade was not restored")
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Cursor was not restored")
            helper.assertTrue(callbacks.get() >= 2, "Expected target and recovery save callbacks")
            helper.assertTrue(!GemOperationContext.isExclusive(menu.storageWrapper), "Failure leaked exclusive context")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesOriginalFailureWhenSaveReusesTheSameException(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val backpack = menu.storageWrapper as BackpackWrapper
        val source = wrapper.upgradeStack.copy()
        val slotId = menu.firstUpgradeSlot
        menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
        val cursor = menu.carried.copy()
        val callbacks = AtomicInteger()
        val repeatedFailure = IllegalStateException("reused save failure")
        try {
            installUpgradeSaveCallback(backpack.upgradeHandler) {
                callbacks.incrementAndGet()
                throw repeatedFailure
            }
            val failure =
                try {
                    menu.clicked(slotId, 1, ClickType.PICKUP, player)
                    null
                } catch (caught: RuntimeException) {
                    caught
                }

            helper.assertTrue(failure != null, "Save callback exception was swallowed")
            helper.assertTrue(
                failure === repeatedFailure,
                "Repeated save exception replaced the original failure",
            )
            helper.assertTrue(ItemStack.matches(source, menu.getSlot(slotId).item), "Source upgrade was not restored")
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Cursor was not restored")
            helper.assertTrue(callbacks.get() >= 2, "Expected target and recovery save callbacks")
            helper.assertTrue(!GemOperationContext.isExclusive(menu.storageWrapper), "Failure leaked exclusive context")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun keepsCreativeCursorAndRejectsAnotherUpperInTheSameBackpack(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val handler = menu.storageWrapper.upgradeHandler
            handler.setStackInSlot(1, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val source = wrapper.upgradeStack.copy()
            val slotId = menu.firstUpgradeSlot
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get(), 2)
            player.abilities.instabuild = true
            val cursor = menu.carried.copy()

            menu.clicked(slotId, 1, ClickType.PICKUP, player)

            helper.assertTrue(
                ItemStack.matches(source, menu.getSlot(slotId).item),
                "Duplicate upper upgrade was accepted",
            )
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Rejected creative conversion changed cursor")
            handler.setStackInSlot(1, ItemStack.EMPTY)
            menu.clicked(slotId, 1, ClickType.PICKUP, player)
            helper.assertTrue(
                menu.getSlot(slotId).item.`is`(ModItems.advancedGemCaseUpgrade.get()),
                "Creative conversion failed",
            )
            helper.assertTrue(ItemStack.matches(cursor, menu.carried), "Creative conversion consumed cursor")
            helper.assertTrue(
                (0 until handler.slots).count {
                    handler.getStackInSlot(it).`is`(ModItems.advancedGemCaseUpgrade.get())
                } == 1,
                "Converted backpack did not contain exactly one upper Gem Case",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesPickupDepositAndRestockFiltersAndMovementAfterConversion(helper: GameTestHelper) {
        val (menu, player, _) = GemMenuFixtures.menu(helper)
        try {
            val handler = menu.storageWrapper.upgradeHandler
            handler.setStackInSlot(1, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
            handler.setStackInSlot(2, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
            handler.setStackInSlot(3, ItemStack(BackpackItems.PICKUP_UPGRADE.get()))
            var deposit = handler.slotWrappers.getValue(1) as DepositUpgradeWrapper
            var restock = handler.slotWrappers.getValue(2) as RestockUpgradeWrapper
            var pickup = handler.slotWrappers.getValue(3) as PickupUpgradeWrapper
            val gem = GemMenuFixtures.gem(2)
            val key = requireNotNull(StandardGem.keyOf(gem))
            val counts = GemCounts.of(mapOf(key to 2))
            val settings =
                GemTransferSettings.DEFAULT.copy(
                    deposit = GemTransferRule(true, GemFilter.of(GemFilterMode.INCLUDE, emptySet(), emptySet())),
                    restock = GemTransferRule(true, GemFilter.of(GemFilterMode.INCLUDE, emptySet(), emptySet())),
                )
            val currentLower = handler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper
            helper.assertTrue(currentLower.replace(GemCounts.of(emptyMap()), counts), "Could not seed lower Gem counts")
            helper.assertTrue(
                currentLower.replaceSettings(GemTransferSettings.DEFAULT, settings),
                "Could not seed lower transfer settings",
            )
            deposit.filterLogic.setAllowList(true)
            deposit.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
            restock.filterLogic.setAllowList(true)
            restock.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
            pickup.filterLogic.setAllowList(true)
            pickup.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
            menu.carried = ItemStack(ModItems.gemCaseUpgradeConversion.get())

            menu.clicked(menu.firstUpgradeSlot, 1, ClickType.PICKUP, player)
            deposit = handler.slotWrappers.getValue(1) as DepositUpgradeWrapper
            restock = handler.slotWrappers.getValue(2) as RestockUpgradeWrapper
            pickup = handler.slotWrappers.getValue(3) as PickupUpgradeWrapper
            val saved = requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper)).settingsSnapshot()
            helper.assertTrue(
                saved == GemSettingsState.Ready(settings),
                "Own transfer filters changed during conversion",
            )
            val target = ItemStackHandler(1)
            val filteredDeposit = deposit.depositToHandler(target)
            helper.assertTrue(filteredDeposit.isEmpty() && target.getStackInSlot(0).isEmpty, "Deposit filter was lost")
            helper.assertTrue(
                (requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper)).snapshot() as GemStorageState.Ready)
                    .counts
                    .count(key) == 2,
                "Filtered Deposit changed upper Gem counts",
            )

            val upper = requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper))
            val depositAllowed =
                settings.copy(
                    deposit = GemTransferRule(true, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
                )
            helper.assertTrue(upper.replaceSettings(settings, depositAllowed), "Could not enable own Deposit filter")
            helper.assertTrue(!deposit.filterLogic.matchesFilter(gem), "Native Deposit filter was not retained")
            val movedDeposit = deposit.depositToHandler(target)
            helper.assertTrue(
                movedDeposit.sumOf { it.count } == 2 && target.getStackInSlot(0).count == 2,
                "Upper Deposit did not move Gem counts",
            )
            val filteredPickup =
                InventoryHelper.runPickupOnPickupResponseUpgrades(helper.level, handler, gem.copy(), false)
            helper.assertTrue(filteredPickup.count == 2, "Pickup filter was lost")
            pickup.filterLogic.setAllowList(false)
            val movedPickup =
                InventoryHelper.runPickupOnPickupResponseUpgrades(
                    helper.level,
                    handler,
                    gem.copy(),
                    false,
                )
            helper.assertTrue(movedPickup.isEmpty, "Upper Pickup did not accept a Gem")
            val afterPickup = requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper))
            helper.assertTrue(
                (afterPickup.snapshot() as GemStorageState.Ready).counts.count(key) == 2,
                "Upper Pickup did not store its accepted Gem",
            )

            val source = ItemStackHandler(1).apply { setStackInSlot(0, gem.copy()) }
            val filteredRestock = restock.restockFromHandler(source)
            helper.assertTrue(
                filteredRestock.isEmpty() && source.getStackInSlot(0).count == 2,
                "Restock filter was lost",
            )
            helper.assertTrue(!restock.filterLogic.matchesFilter(gem), "Native Restock filter was not retained")
            val restockAllowed =
                depositAllowed.copy(
                    restock = GemTransferRule(true, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
                )
            helper.assertTrue(
                upper.replaceSettings(depositAllowed, restockAllowed),
                "Could not enable own Restock filter",
            )
            val movedRestock = restock.restockFromHandler(source)
            helper.assertTrue(
                movedRestock.sumOf { it.count } == 2 && source.getStackInSlot(0).isEmpty,
                "Upper Restock did not consume its source Gem",
            )
            val afterRestock = requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper))
            helper.assertTrue(
                (afterRestock.snapshot() as GemStorageState.Ready).counts.count(key) == 4,
                "Upper Restock did not store its accepted Gem",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    private fun installUpgradeSaveCallback(
        handler: net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler,
        action: () -> Unit,
    ) {
        val field =
            net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler::class.java
                .getDeclaredField("contentsSaveHandler")
        field.isAccessible = true
        field.set(handler, Runnable(action))
    }
}
