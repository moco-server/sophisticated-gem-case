package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction

/** menuを開き直さず、種類選択・純度別取出・加工を同じ面にまとめる。 */
@Suppress("MagicNumber", "TooManyFunctions") // 固定pixel配置とWidgetBaseの入力callbackを一つの画面境界で扱う。
internal class GemCaseModal(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
) : WidgetBase(Position(0, 0), Dimension(232, 230)),
    GemCaseModalContent {
    private val slots = GemCaseModalSlots(screen, container)
    private val model = GemBrowserModel()
    private val catalog = GemCaseCatalog()
    private val browser = GemCaseBrowser(container, screen, model, catalog)
    private val warnings = GemCaseWarnings()
    private val viewState = GemCaseViewState(::onViewChanged)
    private val settingsPanel = GemSettingsPanel(container, screen, viewState, Position(0, 0))
    private var warningsOpen = false
    private var closed = false
    private var closingPending = false
    private val search =
        EditBox(font, 0, 0, 110, 11, gemLabel("search")).apply {
            setBordered(false)
            setTextColor(GemCaseDrawing.TEXT)
            setMaxLength(128)
            setHint(gemLabel("search_hint"))
            setResponder { model.search = it }
        }

    override fun renderBg(
        graphics: GuiGraphics,
        minecraft: Minecraft,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (viewState.view == GemCaseView.SETTINGS) {
            GemCaseDrawing.frame(graphics, x, y, width, height, true)
        } else {
            if (container.upgradeWrapper.supportsProcessing) GemCaseDrawing.frame(graphics, x, y + 16, 56, 96)
            GemCaseDrawing.frame(graphics, x + 56, y, 176, 147, true)
            GemCaseDrawing.frame(graphics, x + 56, y + 140, 176, 90)
            graphics.drawString(font, gemLabel("title"), x + 73, y + 5, GemCaseDrawing.TEXT, false)
        }
    }

    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (!currentMenu()) {
            deferClose()
            return
        }
        if (viewState.view == GemCaseView.SETTINGS) {
            settingsPanel.setPosition(Position(x + 16, y + 12))
            settingsPanel.render(graphics, mouseX, mouseY, partialTick)
        } else {
            renderStorage(graphics, mouseX, mouseY, partialTick)
        }
    }

    private fun renderStorage(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val counts = GemPageCache.forSlot(screen.menu, container.upgradeContainerId)
        catalog.refresh(counts, model)
        if (model.selected == null && counts.complete) {
            model.visible().firstOrNull { it.known }?.let { model.select(it.id) }
        }
        if (container.upgradeWrapper.supportsProcessing) {
            GemProcessingCache.selectGem(screen.menu, container.upgradeContainerId, model.selected)
        }
        search.x = x + 72
        search.y = y + 17
        graphics.fill(x + 70, y + 15, x + 184, y + 29, -15461356)
        search.render(graphics, mouseX, mouseY, partialTick)
        if (warningsOpen) {
            warnings.render(graphics, catalog.unresolved(), model.search, x, y)
        } else {
            browser.render(graphics, mouseX, mouseY, x, y)
        }
        renderTools(graphics, mouseX, mouseY)
        slots.render(graphics, mouseX, mouseY, x, y, container.upgradeWrapper.supportsProcessing && !warningsOpen)
        val text =
            when {
                counts.locked -> gemLabel("locked")
                !counts.complete -> gemLabel("loading")
                else -> model.selected?.let { catalog.stack(it).hoverName } ?: gemLabel("processing_select_gem")
            }
        graphics.drawString(
            font,
            font.plainSubstrByWidth(text.string, 120),
            x + 72,
            y + 131,
            GemCaseDrawing.TEXT,
            false,
        )
    }

    private fun renderTools(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        tools().forEachIndexed { index, tool ->
            val toolY = y + 15 + index * 21
            val hovered = mouseX in x + 199 until x + 217 && mouseY in toolY until toolY + 18
            GemCaseDrawing.button(graphics, x + 199, toolY, 18, 18, hovered)
            if (index == 0) {
                graphics.drawCenteredString(font, "←", x + 208, toolY + 5, GemCaseDrawing.TEXT)
            } else {
                GemCaseDrawing.item(graphics, ItemStack(tool.second), x + 200, toolY + 1)
            }
        }
    }

    private fun tools() =
        listOf(
            "back" to Items.ARROW,
            "settings" to Items.COMPARATOR,
            "insert" to Items.HOPPER,
            "move" to Items.CHEST,
            (
                if (GemProcessingCache.useBackpackMaterials(screen.menu, container.upgradeContainerId) ==
                    true
                ) {
                    "processing_supplement_on"
                } else {
                    "processing_supplement_off"
                }
            ) to
                Items.BUNDLE,
            "unresolved" to Items.BARRIER,
        ).filterIndexed { index, _ -> index != 4 || container.upgradeWrapper.supportsProcessing }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean =
        when {
            closed || !currentMenu() -> true
            !isMouseOver(mouseX, mouseY) -> false
            viewState.view == GemCaseView.SETTINGS -> settingsPanel.mouseClicked(mouseX, mouseY, button)
            else -> clickStorage(mouseX, mouseY, button)
        }

    private fun clickStorage(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val tool = toolAt(mouseX, mouseY)
        return when {
            tool != null -> {
                if (button == 0) clickTool(tools()[tool].first)
                true
            }
            clickSearch(mouseX, mouseY, button) -> true
            !warningsOpen && browser.mouseClicked(mouseX, mouseY, button) -> true
            else -> {
                slots.mouseClicked(mouseX, mouseY, button)
                true
            }
        }
    }

    private fun toolAt(
        mouseX: Double,
        mouseY: Double,
    ): Int? =
        if (mouseX >= x + 199 && mouseX < x + 217) {
            tools().indices.firstOrNull { mouseY >= y + 15 + it * 21 && mouseY < y + 33 + it * 21 }
        } else {
            null
        }

    private fun clickSearch(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        search.isFocused = search.isMouseOver(mouseX, mouseY)
        if (search.isFocused && button == 1) {
            search.value = ""
            return true
        }
        return search.mouseClicked(mouseX, mouseY, button)
    }

    private fun clickTool(key: String) {
        when (key) {
            "back" -> close()
            "settings" -> viewState.view = GemCaseView.SETTINGS
            "insert" -> GemPageCache.send(screen.menu, container.upgradeContainerId, GemAction.INSERT_CURSOR)
            "move" -> GemPageCache.send(screen.menu, container.upgradeContainerId, GemAction.MOVE_NORMAL)
            "unresolved" -> {
                warningsOpen = !warningsOpen
                slots.setMaterialsVisible(!warningsOpen && container.upgradeWrapper.supportsProcessing)
            }
            else -> toggleSupplement()
        }
    }

    private fun toggleSupplement() {
        val slot = container.upgradeContainerId
        val enabled = GemProcessingCache.useBackpackMaterials(screen.menu, slot) ?: return
        if (GemProcessingCache.canSetBackpackMaterials(screen.menu, slot)) {
            GemProcessingCache.sendAction(screen.menu, slot, GemProcessingAction.SET_BACKPACK_MATERIALS, !enabled)
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        if (viewState.view ==
            GemCaseView.SETTINGS
        ) {
            return settingsPanel.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }
        if (warningsOpen) warnings.scroll(vertical) else browser.mouseScrolled(mouseX, mouseY, vertical)
        return true
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean =
        when {
            viewState.view != GemCaseView.STORAGE -> true
            browser.mouseDragged(mouseY) -> true
            search.isFocused && search.mouseDragged(mouseX, mouseY, button, dragX, dragY) -> true
            else -> {
                slots.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                true
            }
        }

    override fun mouseReleased(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        browser.mouseReleased()
        if (viewState.view == GemCaseView.STORAGE) slots.mouseReleased(mouseX, mouseY, button)
        return true
    }

    override fun keyPressed(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean =
        when {
            viewState.view == GemCaseView.SETTINGS -> settingsPanel.keyPressed(key, scanCode, modifiers)
            search.isFocused -> {
                search.keyPressed(key, scanCode, modifiers)
                true
            }
            minecraft.options.keyInventory.matches(key, scanCode) -> {
                close()
                true
            }
            else -> {
                slots.keyPressed(key, scanCode, modifiers)
                true
            }
        }

    override fun charTyped(
        character: Char,
        modifiers: Int,
    ): Boolean =
        if (viewState.view ==
            GemCaseView.SETTINGS
        ) {
            settingsPanel.charTyped(character, modifiers)
        } else {
            search.charTyped(character, modifiers)
        }

    override fun renderTooltip(
        screen: Screen,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (closed || closingPending) return
        if (viewState.view == GemCaseView.STORAGE) {
            val index =
                tools().indices.firstOrNull {
                    mouseX in x + 199 until x + 217 &&
                        mouseY in y + 15 + it * 21 until y + 33 + it * 21
                }
            val lines =
                when {
                    index != null -> toolTooltip(tools()[index].first)
                    warningsOpen -> warnings.tooltip(mouseY, y)
                    else -> browser.tooltip(mouseX, mouseY)
                }
            if (this.screen.menu.carried.isEmpty) {
                slots.renderTooltip(graphics, mouseX, mouseY)
            }
            if (index != null || this.screen.menu.carried.isEmpty) {
                GemCaseDrawing.tooltip(graphics, lines, mouseX, mouseY)
            }
        }
        if (viewState.view == GemCaseView.SETTINGS) {
            settingsPanel.renderTooltip(screen, graphics, mouseX, mouseY)
        }
        val carried = this.screen.menu.carried
        if (!carried.isEmpty) {
            graphics.pose().pushPose()
            graphics.pose().translate(0.0f, 0.0f, 400.0f)
            graphics.renderItem(carried, mouseX - 8, mouseY - 8)
            graphics.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8)
            graphics.pose().popPose()
        }
    }

    private fun toolTooltip(key: String): List<net.minecraft.network.chat.Component> {
        val title = gemLabel(key)
        if (!Screen.hasShiftDown()) {
            return listOf(title, GemCaseDrawing.shiftHint())
        }
        val helpKey = if (key.startsWith("processing_supplement_")) "processing_supplement" else key
        return listOf(title) + GemCaseDrawing.description("${helpKey}_description")
    }

    private fun onViewChanged() {
        updateDimensions(if (viewState.view == GemCaseView.SETTINGS) 300 else 232, 230)
        setPosition(Position((screen.width - width) / 2, (screen.height - height) / 2))
        search.isFocused = false
        slots.setMaterialsVisible(
            viewState.view == GemCaseView.STORAGE && container.upgradeWrapper.supportsProcessing && !warningsOpen,
        )
    }

    private fun currentMenu(): Boolean =
        minecraft.player?.containerMenu === screen.menu && container.isCurrent(screen.menu)

    private fun deferClose() {
        if (closingPending) return
        closingPending = true
        // Coreはrender直後にもoverlay fieldを読むため、この描画の途中ではnullへ差し替えない。
        minecraft.tell { close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        slots.close()
        screen.setModalOverlay(null)
    }
}
