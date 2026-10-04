package org.mocoserver.sophisticatedgemcase.client

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewPayload
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import java.util.Locale

/** native sixslotと加工previewを同じviewで操作する。 */
internal class GemProcessingPanel(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemCaseViewState,
    position: Position,
) : WidgetBase(position, Dimension(PANEL_WIDTH, PANEL_HEIGHT)) {
    private val state = GemProcessingPanelState()
    private val content = GemProcessingPanelContent(container, screen, viewState, state)
    private val actions = GemProcessingPanelActions(container, screen, viewState, state)

    override fun renderBg(
        graphics: GuiGraphics,
        minecraft: Minecraft,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (viewState.view == GemCaseView.PROCESSING) graphics.fill(x, y, x + width, y + height, PANEL_BACKGROUND)
    }

    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (viewState.view == GemCaseView.PROCESSING) content.render(graphics, font, x, y)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val panelActive = viewState.view == GemCaseView.PROCESSING && visible && isMouseOver(mouseX, mouseY)
        if (!panelActive || isMaterialSlotAt(mouseX, mouseY)) return false
        return actions.click(mouseX - x, mouseY - y, button)
    }

    // Coreはwidgetをnative slotより先に処理するため、六材料slotのクリックを消費しない。
    private fun isMaterialSlotAt(
        mouseX: Double,
        mouseY: Double,
    ): Boolean =
        container.slots.filterIsInstance<GemMaterialSlot>().any { slot ->
            val left = screen.guiLeft + slot.x - SLOT_HIT_BORDER
            val top = screen.guiTop + slot.y - SLOT_HIT_BORDER
            val horizontal = mouseX >= left && mouseX < left + SLOT_HIT_SIZE
            val vertical = mouseY >= top && mouseY < top + SLOT_HIT_SIZE
            slot.processingVisible && horizontal && vertical
        }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        if (viewState.view != GemCaseView.PROCESSING || !visible || !isMouseOver(mouseX, mouseY)) return false
        state.scrollCost(if (vertical > 0) -SCROLL_STEP else SCROLL_STEP)
        return true
    }

    private companion object {
        const val SLOT_HIT_BORDER = 1
        const val SLOT_HIT_SIZE = 18
        const val PANEL_WIDTH = 200
        const val PANEL_HEIGHT = 205
    }
}

private class GemProcessingPanelState {
    var costOffset: Int = 0

    fun scrollCost(delta: Int) {
        costOffset = maxOf(0, costOffset + delta)
    }
}

/** 加工viewの表示内容を描画する。 */
private class GemProcessingPanelContent(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemCaseViewState,
    private val state: GemProcessingPanelState,
) {
    fun render(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
    ) {
        val context = ProcessingDrawContext(graphics, font, x, y)
        val counts = GemPageCache.forSlot(screen.menu, container.upgradeContainerId)
        val settings = GemSettingsCache.forSlot(screen.menu, container.upgradeContainerId)
        GemProcessingOutputSelection.ensureSelected(screen, container.upgradeContainerId)
        val output = GemProcessingCache.selectedOutput(screen.menu, container.upgradeContainerId)
        val payload = GemProcessingCache.preview(screen.menu, container.upgradeContainerId)
        GemProcessingCache.updateMaterialSlots(screen.menu, container.upgradeContainerId)
        drawViews(context)
        drawPicker(context, output)
        drawCosts(context, payload)
        drawActions(context, payload)
        drawStatus(context, counts.complete && settings.complete, payload)
    }

    private fun drawViews(context: ProcessingDrawContext) {
        GemCaseView.entries.forEachIndexed { index, view ->
            val left = context.x + index * VIEW_WIDTH
            context.graphics.fill(left, context.y, left + VIEW_WIDTH - 1, context.y + VIEW_HEIGHT, BUTTON_COLOR)
            val key = "${view.name.lowercase(Locale.ROOT)}"
            val color = if (viewState.view == view) SELECTED_COLOR else FOREGROUND_COLOR
            drawText(
                context,
                label(key).string,
                ProcessingTextBounds(left + PADDING, context.y + PADDING, VIEW_WIDTH - PADDING * 2, color),
            )
        }
    }

    private fun drawPicker(
        context: ProcessingDrawContext,
        output: GemKey?,
    ) {
        val gemName =
            output?.let { GemProcessingOutputLabels.gemName(it.gemId) }
                ?: label("processing_select_gem").string
        val purityName =
            output?.let { GemProcessingOutputLabels.purityName(it.purity) }
                ?: label("processing_select_purity").string
        drawButton(context, ProcessingButtonBounds(GEM_BUTTON_X, GEM_BUTTON_Y, GEM_BUTTON_WIDTH), gemName)
        drawButton(context, ProcessingButtonBounds(PURITY_BUTTON_X, GEM_BUTTON_Y, PURITY_BUTTON_WIDTH), purityName)
    }

    private fun drawCosts(
        context: ProcessingDrawContext,
        payload: GemProcessingPreviewPayload?,
    ) {
        val costs = payload?.preview?.costs.orEmpty()
        state.costOffset = state.costOffset.coerceIn(0, maxOf(0, costs.size - COST_ROWS))
        drawText(
            context,
            label("processing_materials").string,
            ProcessingTextBounds(context.x + COST_X, context.y + COST_Y, COST_TEXT_WIDTH),
        )
        costs.drop(state.costOffset).take(COST_ROWS).forEachIndexed { index, cost ->
            val item = BuiltInRegistries.ITEM.get(cost.itemId)
            val itemName =
                if (item == Items.AIR) cost.itemId.toString() else Component.translatable(item.descriptionId).string
            val sideKey = if (cost.side.name == "LEFT") "processing_left" else "processing_right"
            val line = "${label(sideKey).string} $itemName ×${cost.count}"
            val top = context.y + COST_Y + COST_ROW_HEIGHT * (index + FIRST_COST_ROW_OFFSET)
            drawText(context, line, ProcessingTextBounds(context.x + COST_X, top, COST_TEXT_WIDTH))
        }
        if (costs.isEmpty()) {
            drawText(
                context,
                label("processing_no_costs").string,
                ProcessingTextBounds(
                    context.x + COST_X,
                    context.y + COST_Y + COST_ROW_HEIGHT,
                    COST_TEXT_WIDTH,
                    UNRESOLVED_COLOR,
                ),
            )
        }
    }

    private fun drawActions(
        context: ProcessingDrawContext,
        payload: GemProcessingPreviewPayload?,
    ) {
        val key =
            if (payload?.preview?.useBackpackMaterials == true) {
                "processing_supplement_on"
            } else {
                "processing_supplement_off"
            }
        drawButton(context, ProcessingButtonBounds(SUPPLEMENT_X, ACTION_Y, SUPPLEMENT_WIDTH), label(key).string)
        drawButton(context, ProcessingButtonBounds(PROCESS_X, ACTION_Y, PROCESS_WIDTH), label("processing_run").string)
    }

    private fun drawStatus(
        context: ProcessingDrawContext,
        pagesComplete: Boolean,
        payload: GemProcessingPreviewPayload?,
    ) {
        val key =
            when {
                !pagesComplete -> "loading"
                payload == null -> "processing_preview_loading"
                else -> "processing_reason_${payload.preview.reason.name.lowercase(Locale.ROOT)}"
            }
        val color = if (payload?.preview?.reason == GemProcessingReason.READY) FOREGROUND_COLOR else UNRESOLVED_COLOR
        drawText(
            context,
            label(key).string,
            ProcessingTextBounds(context.x + PADDING, context.y + STATUS_Y, STATUS_TEXT_WIDTH, color),
        )
    }

    private fun drawButton(
        context: ProcessingDrawContext,
        bounds: ProcessingButtonBounds,
        text: String,
    ) {
        val left = context.x + bounds.left
        val top = context.y + bounds.top
        context.graphics.fill(left, top, left + bounds.width, top + BUTTON_HEIGHT, BUTTON_COLOR)
        drawText(
            context,
            text,
            ProcessingTextBounds(left + PADDING, top + PADDING, bounds.width - PADDING * 2),
        )
    }

    private fun drawText(
        context: ProcessingDrawContext,
        text: String,
        bounds: ProcessingTextBounds,
    ) {
        context.graphics.drawString(
            context.font,
            context.font.plainSubstrByWidth(text, bounds.width),
            bounds.x,
            bounds.y,
            bounds.color,
            false,
        )
    }

    private fun label(key: String): Component = Component.translatable("sophisticated_gem_case.gui.$key")
}

private data class ProcessingDrawContext(
    val graphics: GuiGraphics,
    val font: Font,
    val x: Int,
    val y: Int,
)

private data class ProcessingTextBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val color: Int = FOREGROUND_COLOR,
)

private data class ProcessingButtonBounds(
    val left: Int,
    val top: Int,
    val width: Int,
)

/** gem種類と純度を回転選択し、出力名を翻訳する。 */
private object GemProcessingOutputSelection {
    fun ensureSelected(
        screen: StorageScreenBase<*>,
        slot: Int,
    ) {
        if (GemProcessingCache.selectedOutput(screen.menu, slot) == null) {
            val gem = choices().firstOrNull()
            val purity = targetPurities().firstOrNull()
            if (gem != null && purity != null) {
                GemProcessingCache.selectOutput(screen.menu, slot, GemKey(gem, purity))
            }
        }
    }

    fun cycleGem(
        screen: StorageScreenBase<*>,
        slot: Int,
    ) {
        val choices = choices()
        if (choices.isEmpty()) return
        val selected = GemProcessingCache.selectedOutput(screen.menu, slot)
        val current = selected?.gemId
        val nextIndex = (choices.indexOf(current) + CYCLE_STEP) % choices.size
        val purity = selected?.purity ?: targetPurities().first()
        GemProcessingCache.selectOutput(screen.menu, slot, GemKey(choices[nextIndex], purity))
    }

    fun cyclePurity(
        screen: StorageScreenBase<*>,
        slot: Int,
    ) {
        val selected = GemProcessingCache.selectedOutput(screen.menu, slot) ?: defaultOutput() ?: return
        val purities = targetPurities()
        val nextIndex = (purities.indexOf(selected.purity) + CYCLE_STEP) % purities.size
        GemProcessingCache.selectOutput(screen.menu, slot, selected.copy(purity = purities[nextIndex]))
    }

    private fun defaultOutput(): GemKey? = choices().firstOrNull()?.let { GemKey(it, targetPurities().first()) }

    private fun choices(): List<String> =
        GemRegistry.INSTANCE
            .getValues()
            .asSequence()
            .map { it.id.toString() }
            .sorted()
            .toList()

    private fun targetPurities(): List<String> =
        Purity.entries.filter { it != Purity.CRACKED }.map { it.serializedName }
}

private object GemProcessingOutputLabels {
    fun gemName(idText: String): String {
        val gem =
            ResourceLocation.tryParse(idText)?.let { id ->
                GemRegistry.INSTANCE.getValues().firstOrNull { it.id == id }
            }
        return gem?.let {
            Component.translatable(GemItem.createStack(it, it.minPurity, DISPLAY_ITEM_COUNT).descriptionId).string
        } ?: idText
    }

    fun purityName(value: String): String =
        if (Purity.entries.any { it != Purity.CRACKED && it.serializedName == value }) {
            Component.translatable("item.apotheosis.gem.$value", Component.empty()).string.trim()
        } else {
            value
        }
}

/** 加工viewの操作をクリック領域ごとに振り分ける。 */
private class GemProcessingPanelActions(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemCaseViewState,
    private val state: GemProcessingPanelState,
) {
    fun click(
        localX: Double,
        localY: Double,
        button: Int,
    ): Boolean =
        when {
            localY < VIEW_HEIGHT -> selectView(localX)
            isOutputButton(localX, localY) -> {
                selectOutput(localX)
                true
            }
            isCostArea(localY) -> {
                scrollCosts(localY)
                true
            }
            isActionArea(localY) -> {
                clickAction(localX, button)
                true
            }
            else -> true
        }

    private fun selectView(localX: Double): Boolean {
        val viewIndex = (localX / VIEW_WIDTH).toInt().coerceIn(0, GemCaseView.entries.lastIndex)
        val view = GemCaseView.entries[viewIndex]
        viewState.view =
            if (view == GemCaseView.PROCESSING && !container.upgradeWrapper.supportsProcessing) {
                GemCaseView.STORAGE
            } else {
                view
            }
        return true
    }

    private fun isOutputButton(
        localX: Double,
        localY: Double,
    ): Boolean =
        localY in GEM_BUTTON_Y.toDouble()..(GEM_BUTTON_Y + BUTTON_HEIGHT).toDouble() &&
            localX < PANEL_WIDTH - PADDING

    private fun selectOutput(localX: Double) {
        if (localX < PURITY_BUTTON_X) {
            GemProcessingOutputSelection.cycleGem(screen, container.upgradeContainerId)
        } else {
            GemProcessingOutputSelection.cyclePurity(screen, container.upgradeContainerId)
        }
    }

    private fun isCostArea(localY: Double): Boolean =
        localY in COST_Y.toDouble()..(STATUS_Y - COST_AREA_BOTTOM_INSET).toDouble()

    private fun scrollCosts(localY: Double) {
        val delta = if (localY < COST_Y + COST_ROW_HEIGHT) -SCROLL_STEP else SCROLL_STEP
        state.scrollCost(delta)
    }

    private fun isActionArea(localY: Double): Boolean =
        localY in ACTION_Y.toDouble()..(ACTION_Y + BUTTON_HEIGHT).toDouble()

    private fun clickAction(
        localX: Double,
        button: Int,
    ) {
        if (localX < PROCESS_X) toggleSupplement() else process(button)
    }

    private fun toggleSupplement() {
        if (!GemProcessingCache.canSetBackpackMaterials(screen.menu, container.upgradeContainerId)) return
        val enabled =
            GemProcessingCache
                .preview(screen.menu, container.upgradeContainerId)
                ?.preview
                ?.useBackpackMaterials ?: return
        GemProcessingCache.sendAction(
            screen.menu,
            container.upgradeContainerId,
            GemProcessingAction.SET_BACKPACK_MATERIALS,
            !enabled,
        )
    }

    private fun process(button: Int) {
        if (button !in LEFT_MOUSE_BUTTON..RIGHT_MOUSE_BUTTON) return
        if (!GemProcessingCache.canProcess(screen.menu, container.upgradeContainerId)) return
        val action = if (Screen.hasShiftDown()) GemProcessingAction.PROCESS_MANY else GemProcessingAction.PROCESS_ONE
        GemProcessingCache.sendAction(screen.menu, container.upgradeContainerId, action)
    }
}

private const val PANEL_WIDTH = 200
private const val PANEL_HEIGHT = 205
private const val VIEW_COUNT = 3
private const val VIEW_WIDTH = PANEL_WIDTH / VIEW_COUNT
private const val VIEW_HEIGHT = 18
private const val PADDING = 4
private const val BUTTON_HEIGHT = 18
private const val GEM_BUTTON_X = 4
private const val GEM_BUTTON_Y = 22
private const val GEM_BUTTON_WIDTH = 88
private const val PURITY_BUTTON_X = 96
private const val PURITY_BUTTON_WIDTH = 100
private const val COST_X = 76
private const val COST_Y = 48
private const val COST_ROW_HEIGHT = 14
private const val COST_ROWS = 4
private const val FIRST_COST_ROW_OFFSET = 1
private const val COST_AREA_BOTTOM_INSET = 2
private const val COST_TEXT_WIDTH = PANEL_WIDTH - COST_X - PADDING
private const val SUPPLEMENT_X = 4
private const val ACTION_Y = 158
private const val SUPPLEMENT_WIDTH = 92
private const val PROCESS_X = 100
private const val PROCESS_WIDTH = 96
private const val STATUS_Y = 188
private const val STATUS_TEXT_WIDTH = PANEL_WIDTH - PADDING * 2
private const val SCROLL_STEP = 1
private const val CYCLE_STEP = 1
private const val LEFT_MOUSE_BUTTON = 0
private const val RIGHT_MOUSE_BUTTON = 1
private const val DISPLAY_ITEM_COUNT = 1
private const val PANEL_BACKGROUND = -14671840
private const val FOREGROUND_COLOR = -1
private const val SELECTED_COLOR = -256
private const val UNRESOLVED_COLOR = -21846
private const val BUTTON_COLOR = -11184811
