package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** 材料slotのnative menu操作を専用server経路で原子的に処理する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemMaterialClickGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun supportsNativePickupInsertPartialExtractAndCapacity(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        try {
            val offered = material(helper, 10)
            fixture.menu.carried = offered.copy()
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialAt(fixture).count == 10, "Left click did not insert material")
            helper.assertTrue(fixture.menu.carried.isEmpty, "Left click retained fully inserted cursor")

            fixture.menu.carried = material(helper, 8)
            fixture.menu.clicked(globalSlot(fixture), 1, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialAt(fixture).count == 11, "Right click did not insert one item")
            helper.assertTrue(fixture.menu.carried.count == 7, "Right click consumed more than one item")

            fixture.menu.carried = ItemStack.EMPTY
            fixture.menu.clicked(globalSlot(fixture), 1, ClickType.PICKUP, fixture.player)
            helper.assertTrue(fixture.menu.carried.count == 6, "Right click did not extract half")
            helper.assertTrue(materialAt(fixture).count == 5, "Right click extraction changed wrong count")

            fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 60))
            fixture.menu.carried = material(helper, 10)
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(
                materialAt(fixture).count == GemMaterialContents.MAX_STACK_SIZE,
                "Material slot exceeded or missed capacity",
            )
            helper.assertTrue(fixture.menu.carried.count == 6, "Partial insertion lost its remainder")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsNonMaterialsAndUnsafeNativeGesturesWithoutMutation(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        try {
            fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 4))
            fixture.menu.carried = ItemStack(Items.STONE, 3)
            val before = snapshotMaterials(fixture)
            val cursor = fixture.menu.carried.copy()
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialsMatch(before, fixture), "Non-material changed dedicated slots")
            helper.assertTrue(ItemStack.matches(cursor, fixture.menu.carried), "Non-material cursor changed")

            fixture.menu.carried = material(helper, 2)
            val validCursor = fixture.menu.carried.copy()
            val unchanged = snapshotMaterials(fixture)
            val gestures =
                listOf(
                    ClickType.SWAP to 0,
                    ClickType.THROW to 0,
                    ClickType.CLONE to 2,
                    ClickType.PICKUP_ALL to 0,
                    ClickType.QUICK_CRAFT to 0,
                    ClickType.QUICK_CRAFT to 1,
                    ClickType.QUICK_CRAFT to 2,
                )
            for ((click, button) in gestures) {
                fixture.menu.clicked(globalSlot(fixture), button, click, fixture.player)
                helper.assertTrue(materialsMatch(unchanged, fixture), "$click changed dedicated slots")
                helper.assertTrue(ItemStack.matches(validCursor, fixture.menu.carried), "$click changed cursor")
            }

            fixture.player.inventory.setItem(0, ItemStack.EMPTY)
            fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 4))
            val normalSlot = fixture.menu.slots.first { it.container === fixture.player.inventory && !it.hasItem() }
            val pickupAllCursor = material(helper, 2)
            fixture.menu.carried = pickupAllCursor.copy()
            val pickupAllMaterials = snapshotMaterials(fixture)
            fixture.menu.clicked(normalSlot.index, 0, ClickType.PICKUP_ALL, fixture.player)
            helper.assertTrue(
                materialsMatch(pickupAllMaterials, fixture),
                "Pickup-all collected from dedicated material slots",
            )
            helper.assertTrue(
                ItemStack.matches(pickupAllCursor, fixture.menu.carried),
                "Pickup-all changed cursor from a normal slot",
            )

            val beforeStale =
                (
                    fixture.wrapper.materialsSnapshot(
                        fixture.player.serverLevel().registryAccess(),
                    ) as GemMaterialState.Ready
                ).contents
            val staleContents =
                GemMaterialContents.of(
                    listOf(ItemStack(Items.STONE, 4)) +
                        List(GemMaterialContents.SLOT_COUNT - 1) { ItemStack.EMPTY },
                )
            helper.assertTrue(
                fixture.wrapper.replaceMaterials(
                    beforeStale,
                    staleContents,
                    fixture.player.serverLevel().registryAccess(),
                ),
                "Could not seed a stale material component for the pickup-all boundary",
            )
            val staleCursor = ItemStack(Items.STONE, 2)
            fixture.menu.carried = staleCursor.copy()
            val staleSlot = snapshotMaterials(fixture)
            fixture.menu.clicked(normalSlot.index, 0, ClickType.PICKUP_ALL, fixture.player)
            helper.assertTrue(materialsMatch(staleSlot, fixture), "Pickup-all collected a stale material stack")
            helper.assertTrue(ItemStack.matches(staleCursor, fixture.menu.carried), "Stale pickup-all changed cursor")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun shiftMovesMaterialsBetweenNativeStorageAndDedicatedSlots(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        try {
            val bagSlot = fixture.menu.storageWrapper.inventoryHandler
            bagSlot.setStackInSlot(0, material(helper, 12))
            val normal = fixture.menu.slots.first { fixture.menu.isStorageInventorySlot(it.index) && it.hasItem() }
            fixture.menu.clicked(normal.index, 0, ClickType.QUICK_MOVE, fixture.player)
            helper.assertTrue(materialAt(fixture).count == 12, "Shift click did not move normal storage material")
            helper.assertTrue(bagSlot.getStackInSlot(0).isEmpty, "Shift click left source material behind")

            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.QUICK_MOVE, fixture.player)
            helper.assertTrue(materialAt(fixture).isEmpty, "Shift click did not extract dedicated material")
            val stored = (0 until bagSlot.slots).sumOf { bagSlot.getStackInSlot(it).count }
            helper.assertTrue(stored == 12, "Shift click did not return material to normal backpack storage")

            for (index in 0 until bagSlot.slots) bagSlot.setStackInSlot(index, ItemStack(Items.STONE, 64))
            fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 7))
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.QUICK_MOVE, fixture.player)
            val playerCount =
                (0 until fixture.player.inventory.containerSize)
                    .sumOf {
                        fixture.player.inventory
                            .getItem(it)
                            .takeIf { stack -> ItemStack.isSameItemSameComponents(stack, material(helper)) }
                            ?.count ?: 0
                    }
            helper.assertTrue(
                materialAt(fixture).isEmpty && playerCount == 7,
                "Shift click did not fall back to player inventory",
            )
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun editsUnselectedMaterialsAndRejectsEveryClickAfterViewCloses(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        try {
            helper.assertTrue(
                fixture.container.sessionFor(fixture.menu).processingOutput == null,
                "The boundary fixture must start without a selected output",
            )
            fixture.menu.carried = material(helper, 5)
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialAt(fixture).count == 5, "No gem selection blocked material insertion")
            fixture.menu.clicked(globalSlot(fixture), 1, ClickType.PICKUP, fixture.player)
            helper.assertTrue(fixture.menu.carried.count == 3, "No gem selection blocked material recovery")

            fixture.container.processingViewOpen = false
            val before = snapshotMaterials(fixture)
            val cursor = fixture.menu.carried.copy()
            for (button in 0..1) {
                fixture.menu.clicked(globalSlot(fixture), button, ClickType.PICKUP, fixture.player)
                fixture.menu.clicked(globalSlot(fixture), button, ClickType.QUICK_MOVE, fixture.player)
            }
            helper.assertTrue(materialsMatch(before, fixture), "Closed view changed material contents")
            helper.assertTrue(ItemStack.matches(cursor, fixture.menu.carried), "Closing view consumed carried items")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun keepsOrdinaryShiftRoutingWhenProcessingViewIsClosed(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        try {
            fixture.container.processingViewOpen = false
            val offered = material(helper, 9)
            fixture.player.inventory.setItem(0, offered.copy())
            val playerSlot =
                fixture.menu.slots.first {
                    it.container === fixture.player.inventory && ItemStack.isSameItemSameComponents(it.item, offered)
                }
            fixture.menu.clicked(playerSlot.index, 0, ClickType.QUICK_MOVE, fixture.player)
            helper.assertTrue(materialAt(fixture).isEmpty, "Closed processing view received shifted material")
            val bagCount =
                (0 until fixture.menu.storageWrapper.inventoryHandler.slots)
                    .sumOf {
                        fixture.menu.storageWrapper.inventoryHandler
                            .getStackInSlot(it)
                            .count
                    }
            helper.assertTrue(bagCount == offered.count, "Closed processing view broke ordinary backpack shift routing")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresCursorAndMaterialsWhenSaveCallbackThrows(helper: GameTestHelper) {
        val calls = AtomicInteger()
        val fixture =
            materialMenu(helper) {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("material save failure")
            }
        val before = snapshotMaterials(fixture)
        val cursor = material(helper, 5)
        fixture.menu.carried = cursor.copy()
        try {
            val failure =
                try {
                    fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
                    null
                } catch (caught: RuntimeException) {
                    caught
                }
            helper.assertTrue(failure != null, "Material save callback failure was swallowed")
            helper.assertTrue(materialsMatch(before, fixture), "Save failure left material changes")
            helper.assertTrue(ItemStack.matches(cursor, fixture.menu.carried), "Save failure consumed cursor")
            helper.assertTrue(calls.get() >= 2, "Material recovery callback was not attempted")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresMaterialAndNormalInventoryWhenDestinationSaveThrows(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 5))
        val source = materialAt(fixture)
        val calls = AtomicInteger()
        try {
            installInventorySaveCallback(fixture.menu.storageWrapper.inventoryHandler) {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("inventory destination save failure")
            }
            val failure =
                try {
                    fixture.menu.clicked(globalSlot(fixture), 0, ClickType.QUICK_MOVE, fixture.player)
                    null
                } catch (caught: RuntimeException) {
                    caught
                }
            helper.assertTrue(failure != null, "Destination save callback failure was swallowed")
            helper.assertTrue(
                ItemStack.matches(source, materialAt(fixture)),
                "Failed shift click consumed its material source",
            )
            val bagCount =
                (0 until fixture.menu.storageWrapper.inventoryHandler.slots)
                    .sumOf {
                        fixture.menu.storageWrapper.inventoryHandler
                            .getStackInSlot(it)
                            .count
                    }
            helper.assertTrue(bagCount == 0, "Failed shift click left items in normal backpack inventory")
            val playerCount =
                (0 until fixture.player.inventory.containerSize).sumOf {
                    fixture.player.inventory
                        .getItem(
                            it,
                        ).count
                }
            helper.assertTrue(playerCount == 0, "Failed shift click left items in player inventory")
            helper.assertTrue(calls.get() >= 2, "Destination recovery callback was not attempted")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresEveryDestinationWhenRecoverySaveAlsoThrows(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        val inventory = fixture.menu.storageWrapper.inventoryHandler
        for (index in 0 until inventory.slots) {
            inventory.setStackInSlot(index, ItemStack(Items.STONE, 64))
        }
        inventory.setStackInSlot(0, material(helper, 63))
        inventory.setStackInSlot(1, material(helper, 60))
        fixture.handler.setStackInSlot(fixture.slot.slotIndex, material(helper, 5))
        val before = List(inventory.slots) { inventory.getStackInSlot(it).copy() }
        val materials = snapshotMaterials(fixture)
        val calls = AtomicInteger()
        try {
            installInventorySaveCallback(inventory) {
                if (calls.incrementAndGet() >= 2) throw IllegalStateException("destination and recovery save failure")
            }
            val failure =
                try {
                    fixture.menu.clicked(globalSlot(fixture), 0, ClickType.QUICK_MOVE, fixture.player)
                    null
                } catch (caught: RuntimeException) {
                    caught
                }
            helper.assertTrue(failure != null, "Destination failure was swallowed")
            helper.assertTrue(failure?.suppressed?.isNotEmpty() == true, "Recovery failure was not retained")
            helper.assertTrue(
                before.indices.all {
                    ItemStack.matches(before[it], inventory.getStackInSlot(it))
                },
                "Recovery callback failure left an earlier destination changed",
            )
            helper.assertTrue(materialsMatch(materials, fixture), "Failed transfer consumed dedicated materials")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsClosedDistantAndUuidReplacedMaterialMenu(helper: GameTestHelper) {
        val fixture = materialMenu(helper)
        val backpack = fixture.menu.storageWrapper as BackpackWrapper
        val before = snapshotMaterials(fixture)
        val cursor = material(helper, 5)
        fixture.menu.carried = cursor.copy()
        val originalPosition = fixture.player.position()
        try {
            fixture.player.setPos(100.0, 100.0, 100.0)
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialsMatch(before, fixture), "Distant menu changed materials")
            helper.assertTrue(ItemStack.matches(cursor, fixture.menu.carried), "Distant menu consumed cursor")

            fixture.player.setPos(originalPosition.x, originalPosition.y, originalPosition.z)
            val oldMenu = fixture.menu
            fixture.player.containerMenu = fixture.player.inventoryMenu
            oldMenu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialsMatch(before, fixture), "Closed menu changed materials")
            helper.assertTrue(ItemStack.matches(cursor, oldMenu.carried), "Closed menu consumed cursor")
            fixture.player.containerMenu = oldMenu

            val oldUuid = backpack.contentsUuid.orElse(null)
            val callbacks = AtomicInteger()
            installUpgradeSaveCallback(fixture.menu.storageWrapper.upgradeHandler) {
                if (callbacks.incrementAndGet() == 1) backpack.setContentsUuid(UUID.randomUUID())
            }
            var failed = false
            try {
                oldMenu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            } catch (_: IllegalStateException) {
                failed = true
            }
            helper.assertTrue(failed, "UUID replacement during material save was not rejected")
            helper.assertTrue(backpack.contentsUuid.orElse(null) != oldUuid, "UUID callback did not run")
            helper.assertTrue(materialsMatch(before, fixture), "UUID mismatch left material changes")
            helper.assertTrue(ItemStack.matches(cursor, oldMenu.carried), "UUID mismatch consumed cursor")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsReentrantMaterialClickDuringSaveCallback(helper: GameTestHelper) {
        val fixtureRef =
            java.util.concurrent.atomic
                .AtomicReference<Fixture?>()
        val callbacks = AtomicInteger()
        val fixture =
            materialMenu(helper) {
                if (callbacks.incrementAndGet() == 1) {
                    val active = requireNotNull(fixtureRef.get())
                    active.menu.clicked(globalSlot(active), 0, ClickType.PICKUP, active.player)
                }
            }
        fixtureRef.set(fixture)
        val offered = material(helper, 4)
        fixture.menu.carried = offered.copy()
        try {
            fixture.menu.clicked(globalSlot(fixture), 0, ClickType.PICKUP, fixture.player)
            helper.assertTrue(materialAt(fixture).count == offered.count, "Reentrant click duplicated material")
            helper.assertTrue(fixture.menu.carried.isEmpty, "Successful outer click did not consume cursor")
            helper.assertTrue(callbacks.get() == 1, "Reentrant click performed another save")
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    private data class Fixture(
        val menu: BackpackContainer,
        val player: net.minecraft.server.level.ServerPlayer,
        val wrapper: GemCaseUpgradeWrapper,
        val container: GemCaseContainer,
        val slot: GemMaterialSlot,
    ) {
        val handler
            get() = wrapper.materialHandler(player.serverLevel())

        fun close() {
            player.server.playerList.remove(player)
        }
    }

    private fun materialMenu(
        helper: GameTestHelper,
        saveMaterials: ((ItemStack) -> Unit)? = null,
    ): Fixture {
        val (oldMenu, player, _) = GemMenuFixtures.menu(helper)
        oldMenu.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val storage = oldMenu.storageWrapper as BackpackWrapper
        val wrapper =
            if (saveMaterials == null) {
                oldMenu.storageWrapper.upgradeHandler.slotWrappers
                    .getValue(0) as GemCaseUpgradeWrapper
            } else {
                GemCaseUpgradeWrapper(storage, oldMenu.storageWrapper.upgradeHandler.getStackInSlot(0)) { stack ->
                    saveMaterials(stack)
                }.also { oldMenu.storageWrapper.upgradeHandler.slotWrappers[0] = it }
            }
        val menu = BackpackContainer(18, player, oldMenu.backpackContext)
        player.containerMenu = menu
        val container = menu.upgradeContainers[0] as GemCaseContainer
        val slot = container.slots.filterIsInstance<GemMaterialSlot>().first()
        container.processingViewOpen = true
        return Fixture(
            menu,
            player,
            wrapper,
            container,
            slot,
        )
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

    private fun installInventorySaveCallback(
        handler: InventoryHandler,
        action: () -> Unit,
    ) {
        val field = InventoryHandler::class.java.getDeclaredField("saveHandler")
        field.isAccessible = true
        field.set(handler, Runnable(action))
    }

    private fun material(
        helper: GameTestHelper,
        count: Int = 1,
    ): ItemStack {
        val item =
            GemPurityRecipes.recipes(helper.level).firstOrNull()?.let { recipe ->
                recipe.value
                    .left()
                    .first()
                    .ingredient()
                    .getItems()
                    .firstOrNull()
                    ?: recipe.value
                        .right()
                        .first()
                        .ingredient()
                        .getItems()
                        .firstOrNull()
            } ?: error("The test needs an Apotheosis purity recipe material")
        return item.copyWithCount(count)
    }

    private fun materialAt(fixture: Fixture): ItemStack = fixture.handler.getStackInSlot(fixture.slot.slotIndex)

    private fun globalSlot(fixture: Fixture): Int = (fixture.slot as Slot).index

    private fun snapshotMaterials(fixture: Fixture): List<ItemStack> =
        List(GemMaterialContents.SLOT_COUNT) { fixture.handler.getStackInSlot(it) }

    private fun materialsMatch(
        expected: List<ItemStack>,
        fixture: Fixture,
    ): Boolean = expected.indices.all { ItemStack.matches(expected[it], fixture.handler.getStackInSlot(it)) }
}
