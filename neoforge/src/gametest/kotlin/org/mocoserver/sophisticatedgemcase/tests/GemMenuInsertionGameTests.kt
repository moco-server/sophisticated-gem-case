package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 公開capabilityとnative menuを経由し、bridgeだけの単体成功を検収にしない。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemMenuInsertionGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun routesNativeClicksAndShiftWithoutLosingOtherSlots(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val gem = GemMenuFixtures.gem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val inventory = menu.storageWrapper.inventoryHandler
        repeat(inventory.slots) { inventory.setStackInSlot(it, ItemStack(Items.STONE, 64)) }
        try {
            menu.carried = gem.copy()
            menu.clicked(0, 1, ClickType.PICKUP, player)
            helper.assertTrue(menu.carried.count == 2, "Right click must insert one")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 1,
                "Right click failed",
            )
            menu.clicked(0, 0, ClickType.PICKUP, player)
            helper.assertTrue(menu.carried.isEmpty, "Left click did not insert remainder")
            val source = menu.slots.first { it.container === player.inventory }
            source.set(gem.copy())
            menu.quickMoveStack(player, source.index)
            helper.assertTrue(source.item.isEmpty, "Shift did not move source")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 6,
                "Shift duplicated or lost gems",
            )
            helper.assertTrue(
                inventory.getStackInSlot(0).count == 64 && inventory.getStackInSlot(0).`is`(Items.STONE),
                "Target stone changed",
            )
            player.inventory.setItem(0, gem.copy())
            menu.clicked(0, 0, ClickType.SWAP, player)
            helper.assertTrue(player.inventory.getItem(0).count == 3, "Unsafe swap changed hotbar")
            menu.carried = gem.copy()
            menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player)
            menu.clicked(0, 1, ClickType.QUICK_CRAFT, player)
            menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player)
            helper.assertTrue(menu.carried.count == 3, "Rejected drag changed source")
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun routesRealBlockAndItemCapabilities(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val gem = GemMenuFixtures.gem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val itemCap = requireNotNull(backpack.backpack.getCapability(Capabilities.ItemHandler.ITEM))
        helper.assertTrue(itemCap.insertItem(0, gem, false).isEmpty, "Item capability did not route gems")
        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, ModBlocks.BACKPACK.get())
        val tile = helper.getBlockEntity(pos) as BackpackBlockEntity
        tile.setBackpack(backpack.backpack)
        val blockCap =
            requireNotNull(
                helper.level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(pos),
                    net.minecraft.core.Direction.UP,
                ),
            )
        helper.assertTrue(blockCap.insertItem(0, gem, false).isEmpty, "Block capability did not route gems")
        val live =
            tile.backpackWrapper.upgradeHandler.slotWrappers
                .getValue(0) as GemCaseUpgradeWrapper
        helper.assertTrue(
            (live.snapshot() as GemStorageState.Ready).counts.count(key) == 4,
            "Capability counts incorrect",
        )
        helper.assertTrue(wrapper.hasContentsOrLocked(), "Source storage became empty")
        helper.assertTrue(blockCap.extractItem(0, 64, false).isEmpty, "Dedicated slots leaked to automation")
        helper.succeed()
    }
}

internal object GemMenuFixtures {
    data class Fixture(
        val menu: BackpackContainer,
        val player: ServerPlayer,
        val wrapper: GemCaseUpgradeWrapper,
    )

    fun menu(helper: GameTestHelper): Fixture {
        val backpack = GemTestFixtures.backpack()
        GemTestFixtures.install(backpack)
        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, ModBlocks.BACKPACK.get())
        val tile = helper.getBlockEntity(pos) as BackpackBlockEntity
        tile.setBackpack(backpack.backpack)
        val absolute = helper.absolutePos(pos)
        val player =
            net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                helper.level,
                com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "GemTest"),
            )
        player.setPos(absolute.x + 0.5, absolute.y.toDouble(), absolute.z + 0.5)
        val menu = BackpackContainer(17, player, BackpackContext.Block(absolute))
        player.containerMenu = menu
        val wrapper =
            menu.storageWrapper.upgradeHandler.slotWrappers
                .getValue(0) as GemCaseUpgradeWrapper
        return Fixture(menu, player, wrapper)
    }

    fun gem(count: Int): ItemStack =
        GemItem.createStack(
            GemRegistry.INSTANCE.values.first {
                it.minPurity ==
                    Purity.CRACKED
            },
            Purity.CRACKED,
            count,
        )
}
