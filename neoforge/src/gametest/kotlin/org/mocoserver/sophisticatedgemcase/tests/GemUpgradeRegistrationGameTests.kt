package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate

/** test用直接setを迂回し、作者の通常装着判定に上下両版が登録されていることを確かめる。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemUpgradeRegistrationGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun equipsLowerGemCaseThroughNativeMenuClick(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack.EMPTY)
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            menu.carried = ItemStack(ModItems.gemCaseUpgrade.get())
            menu.clicked(menu.firstUpgradeSlot, 0, ClickType.PICKUP, player)
            helper.assertTrue(
                menu.storageWrapper.upgradeHandler
                    .getStackInSlot(0)
                    .`is`(ModItems.gemCaseUpgrade.get()),
                "Native menu did not equip lower Gem Case",
            )
            helper.assertTrue(menu.carried.isEmpty, "Equipping did not consume the carried upgrade")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun acceptsBothGemCaseItemsThroughNativeBackpackUpgradeValidation(helper: GameTestHelper) {
        val handler = GemTestFixtures.backpack().upgradeHandler
        helper.assertTrue(
            handler.isItemValid(0, ItemStack(ModItems.gemCaseUpgrade.get())),
            "Lower Gem Case is absent from the native backpack upgrade tag",
        )
        helper.assertTrue(
            handler.isItemValid(0, ItemStack(ModItems.advancedGemCaseUpgrade.get())),
            "Upper Gem Case is absent from the native backpack upgrade tag",
        )
        helper.succeed()
    }
}
