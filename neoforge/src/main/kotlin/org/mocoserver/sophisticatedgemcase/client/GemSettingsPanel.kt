package org.mocoserver.sophisticatedgemcase.client

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemFilterAxis
import org.mocoserver.sophisticatedgemcase.network.GemSettingsAction
import org.mocoserver.sophisticatedgemcase.network.GemSettingsEntry
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPageAccumulator
import java.util.Locale

/** 転送方向・条件方式と、適用するジェム／ランクの選択を一つの面で示す。 */
@Suppress("TooManyFunctions", "MagicNumber") // 固定pixel配置とWidgetBaseの入力境界をまとめる。
internal class GemSettingsPanel(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemCaseViewState,
    position: Position,
) : WidgetBase(position, Dimension(268, 205)) {
    private var direction = GemTransferDirection.DEPOSIT
    private var axis = GemFilterAxis.GEM_ID
    private var listOffset = 0
    private var visibleEntries: List<GemSettingsEntry> = emptyList()
    private val searchBox =
        EditBox(font, 0, 0, 228, 16, gemLabel("search")).apply {
            setMaxLength(128)
            setResponder { listOffset = 0 }
        }
    private val search: String get() = searchBox.value
    private val buttons = mutableListOf<Button>()
    private val back =
        makeButton(Dimension(48, 18), gemLabel("back")) {
            viewState.view = GemCaseView.STORAGE
            searchBox.isFocused = false
        }
    private val deposit =
        makeButton(Dimension(130, 18), gemLabel("deposit")) { selectDirection(GemTransferDirection.DEPOSIT) }
    private val restock =
        makeButton(Dimension(130, 18), gemLabel("restock")) { selectDirection(GemTransferDirection.RESTOCK) }
    private val enabled = makeButton(Dimension(198, 16), Component.empty()) { toggleEnabled() }
    private val mode = makeButton(Dimension(198, 16), Component.empty()) { cycleMode() }
    private val types = makeButton(Dimension(130, 16), Component.empty()) { selectAxis(GemFilterAxis.GEM_ID) }
    private val purities = makeButton(Dimension(130, 16), Component.empty()) { selectAxis(GemFilterAxis.PURITY) }
    private val previous = makeButton(Dimension(24, 14), Component.literal("←")) { scroll(-pageSize()) }
    private val next = makeButton(Dimension(24, 14), Component.literal("→")) { scroll(pageSize()) }
    private val cells =
        List(COLUMNS * ROWS) { index ->
            makeButton(Dimension(24, 24), Component.empty()) { clickEntry(index) }
        }

    private fun makeButton(
        size: Dimension,
        text: Component,
        action: () -> Unit,
    ): Button = Button.builder(text) { action() }.size(size.width(), size.height()).build()

    private fun settingsCache(): GemSettingsPageAccumulator =
        GemSettingsCache.forSlot(screen.menu, container.upgradeContainerId)

    private fun canEdit(): Boolean = GemSettingsCache.canEdit(screen.menu, container.upgradeContainerId)

    private fun currentMode(): GemFilterMode =
        settingsCache().let {
            if (direction == GemTransferDirection.DEPOSIT) it.depositMode else it.restockMode
        }

    private fun selectDirection(value: GemTransferDirection) {
        direction = value
        listOffset = 0
    }

    private fun selectAxis(value: GemFilterAxis) {
        axis = value
        listOffset = 0
        searchBox.value = ""
    }

    override fun renderBg(
        graphics: GuiGraphics,
        minecraft: Minecraft,
        mouseX: Int,
        mouseY: Int,
    ) = Unit

    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (viewState.view != GemCaseView.SETTINGS) return
        graphics.fill(x, y + 42, x + width, y + height, -14671840)
        graphics.drawString(font, gemLabel("settings"), x + 60, y + 5, GemCaseDrawing.GOLD, false)
        graphics.drawString(font, gemLabel("auto_transfer"), x, y + 46, GemCaseDrawing.TEXT, false)
        graphics.drawString(font, gemLabel("filter_mode"), x, y + 66, GemCaseDrawing.TEXT, false)
        graphics.drawString(font, gemLabel("search"), x, y + 106, GemCaseDrawing.TEXT, false)
        refreshButtons()
        buttons.forEach { it.render(graphics, mouseX, mouseY, partialTick) }
        drawDirection(graphics)
        searchBox.setPosition(x + 40, y + 102)
        searchBox.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawString(font, selectionTitle(), x + 4, y + 124, GemCaseDrawing.GOLD, false)
        drawCells(graphics)
        drawStatus(graphics)
    }

    private fun drawDirection(graphics: GuiGraphics) {
        GemCaseDrawing.item(graphics, ItemStack(Items.CHEST), x + 3, y + 23)
        GemCaseDrawing.item(graphics, ItemStack(Items.HOPPER), x + 141, y + 23)
    }

    private fun selectionTitle(): Component =
        when (currentMode()) {
            GemFilterMode.ALL -> gemLabel("selection_all")
            GemFilterMode.INCLUDE ->
                gemLabel(
                    if (direction ==
                        GemTransferDirection.DEPOSIT
                    ) {
                        "selection_deposit"
                    } else {
                        "selection_restock"
                    },
                )
            GemFilterMode.EXCLUDE ->
                gemLabel(
                    if (direction ==
                        GemTransferDirection.DEPOSIT
                    ) {
                        "selection_no_deposit"
                    } else {
                        "selection_no_restock"
                    },
                )
        }

    private fun refreshButtons() {
        val cache = settingsCache()
        val editable = canEdit()
        deposit.message = gemLabel(if (direction == GemTransferDirection.DEPOSIT) "deposit_selected" else "deposit")
        restock.message = gemLabel(if (direction == GemTransferDirection.RESTOCK) "restock_selected" else "restock")
        val active = if (direction == GemTransferDirection.DEPOSIT) cache.depositEnabled else cache.restockEnabled
        enabled.message = gemLabel(if (active) "on" else "off")
        mode.message = gemLabel("transfer_mode_${currentMode().name.lowercase(Locale.ROOT)}")
        enabled.active = editable
        mode.active = editable
        types.message = axisTitle(GemFilterAxis.GEM_ID)
        purities.message = axisTitle(GemFilterAxis.PURITY)
        val entries = if (cache.complete) filteredEntries(cache) else emptyList()
        listOffset = listOffset.coerceIn(0, maxOffset(entries.size))
        visibleEntries = entries.drop(listOffset).take(pageSize())
        buttons.clear()
        buttons.addAll(listOf(back, deposit, restock, enabled, mode, types, purities, previous, next))
        val positions =
            listOf(
                0 to 0,
                0 to 22,
                138 to 22,
                70 to 42,
                70 to 62,
                0 to 82,
                138 to 82,
                214 to 190,
                244 to 190,
            )
        buttons.zip(positions).forEach { (button, pos) -> button.setPosition(x + pos.first, y + pos.second) }
        previous.active = listOffset > 0
        next.active = listOffset < maxOffset(entries.size)
        cells.forEachIndexed { index, cell ->
            val entry = visibleEntries.getOrNull(index)
            cell.visible = entry != null
            cell.active = editable &&
                currentMode() != GemFilterMode.ALL &&
                entry != null &&
                (isSaved(cache.entries, entry) || isKnown(entry.value))
            val rank = axis == GemFilterAxis.PURITY
            cell.setSize(if (rank) 40 else 24, if (rank) 44 else 24)
            val pitch = if (rank) 43 else 26
            cell.setPosition(x + 4 + index % columns() * pitch, y + LIST_Y + index / columns() * pitch)
            buttons.add(cell)
        }
    }

    private fun axisTitle(value: GemFilterAxis): Component {
        val count = settingsCache().entries.count { it.direction == direction && it.axis == value }
        return Component
            .literal(
                if (axis ==
                    value
                ) {
                    "▶ "
                } else {
                    ""
                },
            ).append(gemLabel(if (value == GemFilterAxis.GEM_ID) "gem_ids" else "ranks"))
            .append(" ($count)")
    }

    private fun drawCells(graphics: GuiGraphics) {
        visibleEntries.forEachIndexed { index, entry ->
            val cell = cells[index]
            val rank = axis == GemFilterAxis.PURITY
            val selected = isSaved(settingsCache().entries, entry)
            GemCaseDrawing.item(
                graphics,
                entryStack(entry),
                cell.x + if (rank) 12 else 4,
                cell.y + 4,
                ghost = currentMode() == GemFilterMode.ALL,
            )
            if (rank) {
                font.split(Component.literal(displayName(entry.value)), 36).take(2).forEachIndexed { line, text ->
                    graphics.drawCenteredString(font, text, cell.x + 20, cell.y + 24 + line * 9, GemCaseDrawing.TEXT)
                }
            }
            if (selected) {
                graphics.renderOutline(cell.x, cell.y, cell.width, cell.height, GemCaseDrawing.GOLD)
                graphics.pose().pushPose()
                graphics.pose().translate(0.0f, 0.0f, 200.0f)
                graphics.drawString(
                    font,
                    "✓",
                    cell.x + cell.width - 8,
                    cell.y + if (rank) 4 else 15,
                    GemCaseDrawing.GOLD,
                    true,
                )
                graphics.pose().popPose()
            }
        }
    }

    private fun entryStack(entry: GemSettingsEntry): ItemStack {
        val gems = GemRegistry.INSTANCE.getValues()
        val gem =
            if (axis == GemFilterAxis.GEM_ID) {
                gems.firstOrNull { it.id.toString() == entry.value }
            } else {
                gems.minByOrNull { it.minPurity.ordinal }
            }
        val purity =
            if (axis ==
                GemFilterAxis.GEM_ID
            ) {
                gem?.minPurity
            } else {
                Purity.entries.firstOrNull { it.serializedName == entry.value }
            }
        return if (gem != null &&
            purity != null &&
            purity.ordinal >= gem.minPurity.ordinal
        ) {
            GemItem.createStack(gem, purity, 1)
        } else {
            ItemStack(Items.BARRIER)
        }
    }

    private fun drawStatus(graphics: GuiGraphics) {
        val cache = settingsCache()
        val status =
            when {
                cache.locked ||
                    GemPageCache
                        .forSlot(
                            screen.menu,
                            container.upgradeContainerId,
                        ).locked -> gemLabel("locked")
                !canEdit() -> gemLabel("loading")
                visibleEntries.isEmpty() -> gemLabel("selection_no_results")
                else -> gemLabel("selection_summary", axisCount(GemFilterAxis.GEM_ID), axisCount(GemFilterAxis.PURITY))
            }
        graphics.drawString(font, status, x + 4, y + 194, GemCaseDrawing.MUTED, false)
    }

    private fun axisCount(value: GemFilterAxis): Int =
        settingsCache().entries.count {
            it.direction == direction &&
                it.axis == value
        }

    private fun columns(): Int = if (axis == GemFilterAxis.PURITY) 6 else COLUMNS

    private fun visibleRows(): Int = if (axis == GemFilterAxis.PURITY) 1 else ROWS

    private fun pageSize(): Int = columns() * visibleRows()

    private fun maxOffset(size: Int): Int = maxOf(0, (size + columns() - 1) / columns() - visibleRows()) * columns()

    private fun scroll(amount: Int) {
        listOffset = (listOffset + amount).coerceIn(0, maxOffset(filteredEntries(settingsCache()).size))
    }

    private fun toggleEnabled() {
        if (!canEdit()) return
        val cache = settingsCache()
        val current = if (direction == GemTransferDirection.DEPOSIT) cache.depositEnabled else cache.restockEnabled
        GemSettingsCache.send(
            screen.menu,
            container.upgradeContainerId,
            GemSettingsAction.SET_ENABLED,
            direction,
            enabled = !current,
        )
    }

    private fun cycleMode() {
        if (!canEdit()) return
        val modes = GemFilterMode.entries
        GemSettingsCache.send(
            screen.menu,
            container.upgradeContainerId,
            GemSettingsAction.SET_MODE,
            direction,
            mode = modes[(modes.indexOf(currentMode()) + 1) % modes.size],
        )
    }

    private fun filteredEntries(cache: GemSettingsPageAccumulator): List<GemSettingsEntry> {
        val saved =
            cache.entries
                .filter { it.direction == direction && it.axis == axis }
                .map { it.value }
                .toSet()
        val known =
            when (axis) {
                GemFilterAxis.GEM_ID ->
                    GemRegistry.INSTANCE
                        .getValues()
                        .map { it.id.toString() }
                        .toSet()
                GemFilterAxis.PURITY -> Purity.entries.map { it.serializedName }.toSet()
            }
        val entries =
            (known + saved)
                .asSequence()
                .filter { value ->
                    displayName(value).contains(search, ignoreCase = true) ||
                        value.contains(search, ignoreCase = true)
                }.map { GemSettingsEntry(direction, axis, it) }
                .sortedWith(
                    compareBy({ !isKnown(it.value) }, { displayName(it.value).lowercase(Locale.ROOT) }, { it.value }),
                ).toList()
        return if (axis == GemFilterAxis.PURITY) {
            entries.sortedBy { entry ->
                Purity.entries.indexOfFirst { it.serializedName == entry.value }.let {
                    if (it <
                        0
                    ) {
                        Int.MAX_VALUE
                    } else {
                        it
                    }
                }
            }
        } else {
            entries
        }
    }

    private fun isSaved(
        entries: List<GemSettingsEntry>,
        entry: GemSettingsEntry,
    ): Boolean = entries.any { it.direction == direction && it.axis == axis && it.value == entry.value }

    private fun isKnown(value: String): Boolean =
        when (axis) {
            GemFilterAxis.GEM_ID ->
                ResourceLocation.tryParse(value)?.let { id ->
                    GemRegistry.INSTANCE.getValues().any {
                        it.id ==
                            id
                    }
                } ==
                    true
            GemFilterAxis.PURITY -> Purity.entries.any { it.serializedName == value }
        }

    private fun displayName(value: String): String =
        if (axis ==
            GemFilterAxis.GEM_ID
        ) {
            gemName(value)
        } else {
            purityName(value)
        }

    private fun gemName(value: String): String =
        ResourceLocation.tryParse(value)?.let { id ->
            GemRegistry.INSTANCE.getValues().firstOrNull { it.id == id }?.let { gem ->
                Component.translatable(GemItem.createStack(gem, gem.minPurity, 1).descriptionId).string
            }
        } ?: value

    private fun purityName(value: String): String =
        Purity.entries
            .firstOrNull { it.serializedName == value }
            ?.toComponent()
            ?.string ?: value

    private fun clickEntry(index: Int) {
        val entry = visibleEntries.getOrNull(index) ?: return
        val selected = isSaved(settingsCache().entries, entry)
        val selectable = selected || isKnown(entry.value)
        if (canEdit() && currentMode() != GemFilterMode.ALL && selectable) {
            GemSettingsCache.send(
                screen.menu,
                container.upgradeContainerId,
                if (axis == GemFilterAxis.GEM_ID) GemSettingsAction.SET_GEM_ID else GemSettingsAction.SET_PURITY,
                direction,
                value = entry.value,
                selected = !selected,
            )
        }
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (viewState.view != GemCaseView.SETTINGS || !visible || !isMouseOver(mouseX, mouseY)) return false
        searchBox.isFocused = searchBox.isMouseOver(mouseX, mouseY)
        return searchBox.mouseClicked(mouseX, mouseY, button) || buttons.any { it.mouseClicked(mouseX, mouseY, button) }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        val hovered =
            visible &&
                viewState.view == GemCaseView.SETTINGS &&
                mouseX >= x &&
                mouseX < x + width &&
                mouseY >= y + LIST_Y &&
                mouseY < y + LIST_Y + ROWS * 26
        if (hovered && vertical != 0.0) scroll(if (vertical > 0) -columns() else columns())
        return hovered
    }

    override fun charTyped(
        character: Char,
        modifiers: Int,
    ): Boolean = searchBox.charTyped(character, modifiers)

    override fun keyPressed(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean = searchBox.keyPressed(key, scanCode, modifiers)

    override fun isFocused(): Boolean = viewState.view == GemCaseView.SETTINGS && searchBox.isFocused

    override fun setFocused(focused: Boolean) {
        searchBox.isFocused = focused
    }

    override fun renderTooltip(
        screen: Screen,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val index = cells.indexOfFirst { it.visible && it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
        val lines = if (index >= 0) entryTooltip(visibleEntries[index]) else controlTooltip(mouseX, mouseY)
        GemCaseDrawing.tooltip(graphics, lines, mouseX, mouseY)
    }

    private fun entryTooltip(entry: GemSettingsEntry): List<Component> {
        val selected = isSaved(settingsCache().entries, entry)
        val title = Component.literal(displayName(entry.value))
        val state = gemLabel(if (selected) "selection_selected" else "selection_unselected")
        if (!Screen.hasShiftDown()) return listOf(title, state, GemCaseDrawing.shiftHint())
        val action = if (currentMode() == GemFilterMode.ALL) "selection_inactive" else "selection_click"
        return listOf(
            title,
            state,
            selectionTitle(),
            gemLabel(action),
        ) + GemCaseDrawing.description("selection_rules_description") +
            if (isKnown(entry.value)) emptyList() else GemCaseDrawing.description("selection_unknown_description")
    }

    private fun controlTooltip(
        mouseX: Int,
        mouseY: Int,
    ): List<Component> {
        val key =
            when {
                back.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_back"
                deposit.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_deposit"
                restock.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_restock"
                enabled.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_enabled"
                mode.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_mode"
                types.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_types"
                purities.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_ranks"
                searchBox.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_search"
                previous.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) ||
                    next.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) -> "transfer_pages"
                else -> null
            } ?: return emptyList()
        return if (!Screen.hasShiftDown()) {
            listOf(gemLabel("${key}_title"), GemCaseDrawing.shiftHint())
        } else {
            listOf(gemLabel("${key}_title")) + GemCaseDrawing.description("${key}_description")
        }
    }

    companion object {
        private const val COLUMNS = 10
        private const val ROWS = 2
        private const val LIST_Y = 136
    }
}
