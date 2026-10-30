package org.mocoserver.sophisticatedgemcase.client

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemAction
import java.util.Locale

/** 元のGem Caseと同じ種類・純度・強化の順に描画し、数量操作は既存server channelへ送る。 */
@Suppress("MagicNumber") // GUI pxの固定配置。保存・recipeの数値はここへ持ち込まない。
internal class GemCaseBrowser(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    val model: GemBrowserModel,
    private val catalog: GemCaseCatalog,
) {
    private val bounds = GemBrowserBounds()
    private var scrolling = false

    fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
    ) {
        bounds.left = x + 56
        bounds.top = y
        model.visible().forEachIndexed { index, entry ->
            val itemX = bounds.left + 21 + index % 6 * 18
            val itemY = bounds.top + 31 + index / 6 * 19
            GemCaseDrawing.slot(graphics, itemX, itemY, model.selected == entry.id)
            GemCaseDrawing.item(graphics, catalog.stack(entry.id), itemX, itemY, entry.count, entry.count == 0L)
            if (bounds.inside(mouseX.toDouble(), mouseY.toDouble(), itemX, itemY)) {
                graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0x30FFFFFF)
            }
        }
        drawScrollbar(graphics)
        model.selected?.let { drawPurities(graphics, it) }
    }

    private fun drawScrollbar(graphics: GuiGraphics) {
        graphics.fill(bounds.left + 12, bounds.top + 30, bounds.left + 16, bounds.top + 85, -15461356)
        val max = model.maxOffset()
        val offset = if (max == 0) 0 else model.rowOffset * 43 / max
        graphics.fill(
            bounds.left + 12,
            bounds.top + 30 + offset,
            bounds.left + 16,
            bounds.top + 42 + offset,
            GemCaseDrawing.MUTED,
        )
    }

    private fun drawPurities(
        graphics: GuiGraphics,
        id: String,
    ) {
        Purity.entries.forEachIndexed { index, purity ->
            val itemX = bounds.left + 21 + index * 18
            val itemY = bounds.top + 91
            val stack = catalog.stack(id, purity)
            GemCaseDrawing.slot(graphics, itemX, itemY)
            if (!stack.isEmpty) {
                val count = catalog.count(id, purity)
                GemCaseDrawing.item(graphics, stack, itemX, itemY, count.toLong(), count == 0)
            }
        }
        if (container.upgradeWrapper.supportsProcessing) drawUpgrades(graphics, id)
    }

    private fun drawUpgrades(
        graphics: GuiGraphics,
        id: String,
    ) {
        Purity.entries.drop(1).forEachIndexed { index, purity ->
            val itemX = bounds.left + 30 + index * 18
            val itemY = bounds.top + 109
            val ready =
                GemProcessingCache.canProcess(
                    screen.menu,
                    container.upgradeContainerId,
                    GemKey(id, purity.serializedName),
                )
            GemCaseDrawing.button(graphics, itemX, itemY, 16, 16, false, ready)
            GemCaseDrawing.item(graphics, ItemStack(Items.IRON_AXE), itemX, itemY, ghost = !ready)
            if (ready) graphics.fill(itemX + 2, itemY + 14, itemX + 14, itemY + 15, 0xFF71B75B.toInt())
        }
    }

    @Suppress("ReturnCount") // スクロール・種類・純度・加工の相互排他的な入力領域を順にconsumeする。
    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        scrolling = false
        if (button !in 0..1) return false
        if (bounds.overScrollbar(mouseX, mouseY)) {
            scrolling = true
            model.scrollTo(bounds.fraction(mouseY))
            return true
        }
        val groupIndex = bounds.gridIndex(mouseX, mouseY)
        if (groupIndex != null) {
            model.visible().getOrNull(groupIndex)?.let { model.select(it.id) }
            return true
        }
        val id = model.selected ?: return false
        val purity = bounds.purityAt(mouseX, mouseY)
        if (purity != null) {
            withdraw(id, purity, button)
            return true
        }
        val target = bounds.upgradeAt(mouseX, mouseY)
        if (target != null && container.upgradeWrapper.supportsProcessing) {
            GemProcessingCache.process(
                screen.menu,
                container.upgradeContainerId,
                GemKey(id, target.serializedName),
                Screen.hasShiftDown(),
            )
            return true
        }
        return false
    }

    private fun withdraw(
        id: String,
        purity: Purity,
        button: Int,
    ) {
        if (catalog.stack(id, purity).isEmpty || catalog.count(id, purity) == 0) return
        val action =
            when {
                button == 0 && Screen.hasShiftDown() -> GemAction.WITHDRAW_TO_PLAYER
                button == 1 -> GemAction.WITHDRAW_STACK
                else -> GemAction.WITHDRAW_ONE
            }
        GemPageCache.send(screen.menu, container.upgradeContainerId, action, GemKey(id, purity.serializedName))
    }

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        vertical: Double,
    ): Boolean {
        val within =
            mouseX >= bounds.left + 11 &&
                mouseX < bounds.left + 130 &&
                mouseY >= bounds.top + 29 &&
                mouseY < bounds.top + 88
        if (within && vertical != 0.0) model.scroll(if (vertical > 0) -1 else 1)
        return within
    }

    fun mouseDragged(mouseY: Double): Boolean {
        if (scrolling) model.scrollTo(bounds.fraction(mouseY))
        return scrolling
    }

    fun mouseReleased() {
        scrolling = false
    }

    @Suppress("ReturnCount") // 領域ごとのtooltipを返し、未選択・未同期を加工可能へ読み替えない。
    fun tooltip(
        mouseX: Int,
        mouseY: Int,
    ): List<Component> {
        val entry = bounds.gridIndex(mouseX.toDouble(), mouseY.toDouble())?.let { model.visible().getOrNull(it) }
        if (entry != null) {
            return listOf(Component.literal(entry.name), gemLabel("owned", entry.count)) +
                if (entry.known) emptyList() else listOf(gemLabel("unresolved"), Component.literal(entry.id))
        }
        val id = model.selected ?: return emptyList()
        val purity = bounds.purityAt(mouseX.toDouble(), mouseY.toDouble())
        if (purity != null) {
            val stack = catalog.stack(id, purity)
            if (stack.isEmpty) return listOf(gemLabel("unavailable_purity"))
            return listOf(stack.hoverName, gemLabel("owned_capacity", catalog.count(id, purity)), gemLabel("help"))
        }
        val target = bounds.upgradeAt(mouseX.toDouble(), mouseY.toDouble()) ?: return emptyList()
        if (!container.upgradeWrapper.supportsProcessing) return emptyList()
        val title = gemLabel("upgrade_to", purityName(target))
        val payload =
            GemProcessingCache.preview(screen.menu, container.upgradeContainerId, GemKey(id, target.serializedName))
                ?: return listOf(title, gemLabel("processing_preview_loading"))
        val preview = payload.preview
        val result = mutableListOf(title, gemLabel("processing_reason_${preview.reason.name.lowercase(Locale.ROOT)}"))
        preview.costs.forEach { cost ->
            val item = BuiltInRegistries.ITEM.get(cost.itemId)
            result.add(Component.translatable(item.descriptionId).append(Component.literal(" × ${cost.count}")))
        }
        result.add(gemLabel("upgrade_help"))
        return result
    }
}

@Suppress("MagicNumber") // 元のGem Caseに合わせたセル境界だけを扱う。
private class GemBrowserBounds {
    var left = 0
    var top = 0

    fun gridIndex(
        mouseX: Double,
        mouseY: Double,
    ): Int? {
        val column = ((mouseX - left - 21) / 18).toInt()
        val row = ((mouseY - top - 31) / 19).toInt()
        if (column !in 0..5 || row !in 0..2) return null
        val index = row * 6 + column
        return index.takeIf { inside(mouseX, mouseY, left + 21 + column * 18, top + 31 + row * 19) }
    }

    fun purityAt(
        mouseX: Double,
        mouseY: Double,
    ): Purity? = Purity.entries.firstOrNull { inside(mouseX, mouseY, left + 21 + it.ordinal * 18, top + 91) }

    fun upgradeAt(
        mouseX: Double,
        mouseY: Double,
    ): Purity? =
        Purity.entries.drop(1).firstOrNull { inside(mouseX, mouseY, left + 30 + (it.ordinal - 1) * 18, top + 109) }

    fun inside(
        mouseX: Double,
        mouseY: Double,
        x: Int,
        y: Int,
    ): Boolean = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16

    fun overScrollbar(
        mouseX: Double,
        mouseY: Double,
    ): Boolean =
        mouseX in (left + 11).toDouble()..(left + 17).toDouble() &&
            mouseY in (top + 29).toDouble()..(top + 86).toDouble()

    fun fraction(mouseY: Double): Double = (mouseY - top - 36) / 43.0
}

private fun purityName(purity: Purity): Component =
    Component.translatable("item.apotheosis.gem.${purity.serializedName}", Component.empty())
