package org.mocoserver.sophisticatedgemcase.client

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import java.util.Locale

/** 同期済みの設定だけを編集する、収納一覧と独立したview。 */
@Suppress("TooManyFunctions")
internal class GemSettingsPanel(
    private val container: GemCaseContainer,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemSettingsViewState,
    position: Position,
) : WidgetBase(position, Dimension(PANEL_WIDTH, PANEL_HEIGHT)) {
    private var search = ""
    private var searchFocused = false
    private var direction = GemTransferDirection.DEPOSIT
    private var axis = GemFilterAxis.GEM_ID
    private var listOffset = 0

    private fun label(key: String): Component = Component.translatable("sophisticated_gem_case.gui.$key")

    override fun renderBg(
        graphics: GuiGraphics,
        minecraft: Minecraft,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (viewState.settings) graphics.fill(x, y, x + width, y + height, BACKGROUND)
    }

    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (!viewState.settings) return
        drawViews(graphics)
        val cache = settingsCache()
        val editable = GemSettingsCache.canEdit(screen.menu, container.upgradeContainerId)
        drawControls(graphics, cache, editable)
        drawSearch(graphics)
        drawEntries(graphics, cache)
        drawStatus(graphics, cache, editable)
    }

    private fun settingsCache(): GemSettingsPageAccumulator =
        GemSettingsCache.forSlot(screen.menu, container.upgradeContainerId)

    private fun drawControls(
        graphics: GuiGraphics,
        cache: GemSettingsPageAccumulator,
        editable: Boolean,
    ) {
        val depositSelected = direction == GemTransferDirection.DEPOSIT
        drawOption(graphics, 0, DIRECTION_Y, label(if (depositSelected) "deposit_selected" else "deposit"), editable)
        drawOption(
            graphics,
            PANEL_WIDTH / 2,
            DIRECTION_Y,
            label(if (depositSelected) "restock" else "restock_selected"),
            editable,
        )
        val enabled = if (depositSelected) cache.depositEnabled else cache.restockEnabled
        val mode = if (depositSelected) cache.depositMode else cache.restockMode
        drawOption(graphics, 0, MODE_Y, label(if (enabled) "enabled" else "disabled"), editable)
        drawOption(graphics, PANEL_WIDTH / 2, MODE_Y, label("mode_${mode.name.lowercase(Locale.ROOT)}"), editable)
        drawOption(graphics, 0, AXIS_Y, label(if (axis == GemFilterAxis.GEM_ID) "gem_ids" else "purities"), editable)
    }

    private fun drawSearch(graphics: GuiGraphics) {
        graphics.drawString(
            font,
            font.plainSubstrByWidth(label("search").string + search, PANEL_WIDTH - PADDING * 2),
            x + PADDING,
            y + SEARCH_Y,
            FOREGROUND,
            false,
        )
    }

    private fun drawEntries(
        graphics: GuiGraphics,
        cache: GemSettingsPageAccumulator,
    ) {
        val entries = if (cache.complete) filteredEntries(cache) else emptyList()
        listOffset = listOffset.coerceIn(0, maxOf(0, entries.size - VISIBLE_ROWS))
        entries.drop(listOffset).take(VISIBLE_ROWS).forEachIndexed { index, entry ->
            drawEntry(graphics, cache, entry, index)
        }
    }

    private fun drawEntry(
        graphics: GuiGraphics,
        cache: GemSettingsPageAccumulator,
        entry: GemSettingsEntry,
        index: Int,
    ) {
        val selected = isSaved(cache.entries, entry)
        val color = entryColor(selected, isKnown(entry.value))
        val name = displayName(entry.value)
        val marker = if (selected) "[x] " else "[ ] "
        graphics.drawString(
            font,
            font.plainSubstrByWidth(marker + name, PANEL_WIDTH - PADDING * 2),
            x + PADDING,
            y + LIST_Y + index * ROW_HEIGHT,
            color,
            false,
        )
    }

    private fun entryColor(
        selected: Boolean,
        known: Boolean,
    ): Int =
        when {
            selected -> SELECTED
            !known -> UNRESOLVED
            else -> FOREGROUND
        }

    private fun drawStatus(
        graphics: GuiGraphics,
        cache: GemSettingsPageAccumulator,
        editable: Boolean,
    ) {
        val counts = GemPageCache.forSlot(screen.menu, container.upgradeContainerId)
        val statusKey =
            when {
                cache.locked || counts.locked -> "locked"
                !editable -> "loading"
                else -> "settings_help"
            }
        graphics.drawString(
            font,
            font.plainSubstrByWidth(label(statusKey).string, PANEL_WIDTH - PADDING * 2),
            x + PADDING,
            y + STATUS_Y,
            FOREGROUND,
            false,
        )
    }

    private fun drawViews(graphics: GuiGraphics) {
        graphics.fill(x, y, x + PANEL_WIDTH / 2 - 1, y + VIEW_HEIGHT, BUTTON_COLOR)
        graphics.fill(x + PANEL_WIDTH / 2 + 1, y, x + PANEL_WIDTH, y + VIEW_HEIGHT, BUTTON_COLOR)
        graphics.drawString(font, label("storage"), x + PADDING, y + PADDING, FOREGROUND, false)
        graphics.drawString(font, label("settings"), x + PANEL_WIDTH / 2 + PADDING, y + PADDING, SELECTED, false)
    }

    private fun drawOption(
        graphics: GuiGraphics,
        offset: Int,
        top: Int,
        text: Component,
        enabled: Boolean,
    ) {
        graphics.fill(
            x + offset,
            y + top,
            x + offset + PANEL_WIDTH / 2 - PADDING,
            y + top + OPTION_HEIGHT,
            if (enabled) BUTTON_COLOR else DISABLED_COLOR,
        )
        graphics.drawString(
            font,
            font.plainSubstrByWidth(text.string, PANEL_WIDTH / 2 - PADDING * 2),
            x + offset + PADDING,
            y + top + PADDING,
            if (enabled) FOREGROUND else UNRESOLVED,
            false,
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
        return (known + saved)
            .asSequence()
            .filter { value ->
                displayName(value).contains(search, ignoreCase = true) ||
                    value.contains(search, ignoreCase = true)
            }.map { GemSettingsEntry(direction, axis, it) }
            .sortedWith(
                compareBy({ it.value !in saved }, { displayName(it.value).lowercase(Locale.ROOT) }, { it.value }),
            ).toList()
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
        if (Purity.entries.any { it.serializedName == value }) {
            Component.translatable("item.apotheosis.gem.$value", Component.empty()).string.trim()
        } else {
            value
        }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean =
        when {
            !viewState.settings || !visible || !isMouseOver(mouseX, mouseY) -> false
            mouseY - y < VIEW_HEIGHT -> clickView(mouseX)
            else -> {
                handlePanelClick(mouseX, mouseY - y)
                true
            }
        }

    private fun clickView(mouseX: Double): Boolean {
        viewState.settings = mouseX - x >= PANEL_WIDTH / 2
        searchFocused = false
        return true
    }

    private fun handlePanelClick(
        mouseX: Double,
        localY: Double,
    ) {
        val cache = GemSettingsCache.forSlot(screen.menu, container.upgradeContainerId)
        val editable = GemSettingsCache.canEdit(screen.menu, container.upgradeContainerId)
        when {
            isRow(localY, DIRECTION_Y) -> selectDirection(mouseX)
            isRow(localY, MODE_Y) && editable -> clickModeOption(mouseX, cache)
            localY in AXIS_Y.toDouble()..(AXIS_Y + OPTION_HEIGHT).toDouble() -> {
                axis = if (mouseX - x < PANEL_WIDTH / 2) GemFilterAxis.GEM_ID else GemFilterAxis.PURITY
                listOffset = 0
            }
            localY in SEARCH_Y.toDouble()..LIST_Y.toDouble() -> searchFocused = true
            isEntryRow(localY) -> clickEntry(localY, cache, editable)
        }
    }

    private fun isRow(
        localY: Double,
        top: Int,
    ): Boolean = localY in top.toDouble()..(top + OPTION_HEIGHT).toDouble()

    private fun isEntryRow(localY: Double): Boolean = localY >= LIST_Y && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT

    private fun selectDirection(mouseX: Double) {
        direction = if (mouseX - x < PANEL_WIDTH / 2) GemTransferDirection.DEPOSIT else GemTransferDirection.RESTOCK
    }

    private fun clickModeOption(
        mouseX: Double,
        cache: GemSettingsPageAccumulator,
    ) {
        if (mouseX - x < PANEL_WIDTH / 2) {
            val current = if (direction == GemTransferDirection.DEPOSIT) cache.depositEnabled else cache.restockEnabled
            GemSettingsCache.send(
                screen.menu,
                container.upgradeContainerId,
                GemSettingsAction.SET_ENABLED,
                direction,
                enabled = !current,
            )
        } else {
            val current = if (direction == GemTransferDirection.DEPOSIT) cache.depositMode else cache.restockMode
            val modes = GemFilterMode.entries
            GemSettingsCache.send(
                screen.menu,
                container.upgradeContainerId,
                GemSettingsAction.SET_MODE,
                direction,
                mode = modes[(modes.indexOf(current) + 1) % modes.size],
            )
        }
    }

    private fun clickEntry(
        localY: Double,
        cache: GemSettingsPageAccumulator,
        editable: Boolean,
    ) {
        val row = ((localY - LIST_Y) / ROW_HEIGHT).toInt()
        val entry = filteredEntries(cache).getOrNull(listOffset + row) ?: return
        val selected = isSaved(cache.entries, entry)
        if (editable && (selected || isKnown(entry.value))) {
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

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        val hovered = viewState.settings && visible && isMouseOver(mouseX, mouseY)
        if (hovered) listOffset = maxOf(0, listOffset + if (vertical > 0) -1 else 1)
        return hovered
    }

    override fun charTyped(
        character: Char,
        modifiers: Int,
    ): Boolean {
        val accepted =
            viewState.settings &&
                visible &&
                searchFocused &&
                !Character.isISOControl(character) &&
                GemStorageCodec.validText(search + character)
        if (accepted) search += character
        return accepted
    }

    override fun keyPressed(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        val accepted = viewState.settings && visible && searchFocused && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
        if (accepted &&
            key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE &&
            search.isNotEmpty()
        ) {
            search = search.dropLast(1)
        }
        return accepted
    }

    override fun isFocused(): Boolean = viewState.settings && searchFocused

    override fun setFocused(focused: Boolean) {
        searchFocused = focused
    }

    companion object {
        private const val PANEL_WIDTH = 200
        private const val PANEL_HEIGHT = 205
        private const val PADDING = 4
        private const val VIEW_HEIGHT = 18
        private const val OPTION_HEIGHT = 12
        private const val DIRECTION_Y = 20
        private const val MODE_Y = 34
        private const val AXIS_Y = 48
        private const val SEARCH_Y = 62
        private const val LIST_Y = 76
        private const val ROW_HEIGHT = 12
        private const val VISIBLE_ROWS = 7
        private const val STATUS_Y = 190
        private const val BACKGROUND = -14671840
        private const val FOREGROUND = -1
        private const val SELECTED = -256
        private const val UNRESOLVED = -21846
        private const val BUTTON_COLOR = -11184811
        private const val DISABLED_COLOR = -13421773
    }
}

internal class GemSettingsViewState(
    var settings: Boolean = false,
)
