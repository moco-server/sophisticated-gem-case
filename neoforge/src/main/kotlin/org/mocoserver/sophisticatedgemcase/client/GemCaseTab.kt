package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemPageEntry
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import java.util.Locale
import java.util.UUID

/** バッグの既存タブ内で収納一覧と設定画面を切り替える。 */
internal class GemCaseTab(
    container: GemCaseContainer,
    position: Position,
    screen: StorageScreenBase<*>,
) : UpgradeSettingsTab<GemCaseContainer>(container, position, screen, label("title"), label("help")) {
    private val viewState = GemSettingsViewState()
    private val panel =
        addHideableChild(GemCasePanel(container, Position(x + PANEL_OFFSET_X, y + PANEL_OFFSET_Y), screen, viewState))

    @Suppress("unused")
    private val settingsPanel =
        addHideableChild(
            GemSettingsPanel(container, screen, viewState, Position(x + PANEL_OFFSET_X, y + PANEL_OFFSET_Y)),
        )

    override fun moveSlotsToTab() = Unit

    override fun slotIsNotCoveredAt(
        slot: net.minecraft.world.inventory.Slot,
        mouseX: Double,
        mouseY: Double,
    ): Boolean = !isOpen || !panel.isMouseOver(mouseX, mouseY)
}

private const val PANEL_OFFSET_X = 3
private const val PANEL_OFFSET_Y = 24
private const val PANEL_WIDTH = 200
private const val PANEL_HEIGHT = 205
private const val PADDING = 4
private const val VIEW_HEIGHT = 18
private const val SEARCH_Y = 20
private const val LIST_Y = 34
private const val PURITY_Y = 96
private const val ROW_HEIGHT = 12
private const val VISIBLE_ROWS = 5
private const val BUTTON_Y = 160
private const val BUTTON_HEIGHT = 16
private const val STATUS_Y = 184
private const val BACKGROUND = -14671840
private const val FOREGROUND = -1
private const val SELECTED = -256
private const val UNRESOLVED = -21846
private const val BUTTON_COLOR = -11184811

private fun label(key: String): Component = Component.translatable("sophisticated_gem_case.gui.$key")

/** 個数一覧だけを扱う。設定編集はGemSettingsPanelに分離する。 */
@Suppress("TooManyFunctions")
private class GemCasePanel(
    private val container: GemCaseContainer,
    position: Position,
    private val screen: StorageScreenBase<*>,
    private val viewState: GemSettingsViewState,
) : WidgetBase(position, Dimension(PANEL_WIDTH, PANEL_HEIGHT)) {
    private var search = ""
    private var searchFocused = false
    private var selected: String? = null
    private var listOffset = 0
    private var purityOffset = 0
    private var renderedRevision = -1L
    private var renderedEntryCount = -1
    private var renderedSession: UUID? = null
    private var groups: List<Group> = emptyList()

    private data class Group(
        val id: String,
        val name: String,
        val entries: List<GemPageEntry>,
    )

    private fun cache(): GemPageAccumulator = GemPageCache.forSlot(screen.menu, container.upgradeContainerId)

    override fun renderBg(
        graphics: GuiGraphics,
        minecraft: Minecraft,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (!viewState.settings) graphics.fill(x, y, x + width, y + height, BACKGROUND)
    }

    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (viewState.settings) return
        drawViews(graphics)
        refreshGroups()
        graphics.drawString(
            font,
            font.plainSubstrByWidth(label("search").string + search, PANEL_WIDTH - PADDING * 2),
            x + PADDING,
            y + SEARCH_Y,
            FOREGROUND,
            false,
        )
        drawGroups(graphics)
        drawPurities(graphics)
        button(graphics, 0, label("insert"))
        button(graphics, PANEL_WIDTH / 2, label("move"))
        val current = cache()
        val status =
            when {
                current.locked -> label("locked")
                !current.complete -> label("loading")
                else -> label("help")
            }
        graphics.drawString(
            font,
            font.plainSubstrByWidth(status.string, PANEL_WIDTH - PADDING * 2),
            x + PADDING,
            y + STATUS_Y,
            FOREGROUND,
            false,
        )
    }

    private fun drawViews(graphics: GuiGraphics) {
        graphics.fill(x, y, x + PANEL_WIDTH / 2 - 1, y + VIEW_HEIGHT, BUTTON_COLOR)
        graphics.fill(x + PANEL_WIDTH / 2 + 1, y, x + PANEL_WIDTH, y + VIEW_HEIGHT, BUTTON_COLOR)
        graphics.drawString(font, label("storage"), x + PADDING, y + PADDING, SELECTED, false)
        graphics.drawString(font, label("settings"), x + PANEL_WIDTH / 2 + PADDING, y + PADDING, FOREGROUND, false)
    }

    private fun refreshGroups() {
        val current = cache()
        if (renderedRevision != current.revision ||
            renderedSession != current.sessionId ||
            renderedEntryCount != current.entries.size
        ) {
            groups =
                current.entries
                    .groupBy { it.key.gemId }
                    .map { (id, entries) ->
                        val stack = StandardGem.stackOf(entries.first().key)
                        val name = if (stack != null) Component.translatable(stack.descriptionId).string else id
                        Group(id, name, entries)
                    }.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
            renderedEntryCount = current.entries.size
            renderedRevision = current.revision
            renderedSession = current.sessionId
        }
    }

    private fun filtered(): List<Group> =
        groups.filter {
            it.name.lowercase(Locale.ROOT).contains(search.lowercase(Locale.ROOT)) || it.id.contains(search)
        }

    private fun drawGroups(graphics: GuiGraphics) {
        val rows = filtered()
        listOffset = listOffset.coerceIn(0, maxOf(0, rows.size - VISIBLE_ROWS))
        rows.drop(listOffset).take(VISIBLE_ROWS).forEachIndexed { index, group ->
            val text = "${group.name} [${group.entries.sumOf { it.count }}]"
            graphics.drawString(
                font,
                font.plainSubstrByWidth(text, PANEL_WIDTH - PADDING * 2),
                x + PADDING,
                y + LIST_Y + index * ROW_HEIGHT,
                if (group.id == selected) SELECTED else FOREGROUND,
                false,
            )
        }
    }

    private fun purityRows(): List<GemPageEntry> {
        val group = groups.firstOrNull { it.id == selected } ?: return emptyList()
        val known =
            dev.shadowsoffire.apotheosis.socket.gem.Purity.entries
                .map { it.serializedName }
        return (known + group.entries.map { it.key.purity }.filter { it !in known }).map { purity ->
            group.entries.firstOrNull { it.key.purity == purity } ?: GemPageEntry(GemKey(group.id, purity), 0, false)
        }
    }

    private fun drawPurities(graphics: GuiGraphics) {
        val rows = purityRows()
        purityOffset = purityOffset.coerceIn(0, maxOf(0, rows.size - VISIBLE_ROWS))
        rows.drop(purityOffset).take(VISIBLE_ROWS).forEachIndexed { index, entry ->
            val purity =
                if (dev.shadowsoffire.apotheosis.socket.gem.Purity.entries.any {
                        it.serializedName ==
                            entry.key.purity
                    }
                ) {
                    Component.translatable("item.apotheosis.gem.${entry.key.purity}", Component.empty()).string.trim()
                } else {
                    entry.key.purity
                }
            val text = "$purity: ${entry.count}/32767"
            graphics.drawString(
                font,
                font.plainSubstrByWidth(text, PANEL_WIDTH - PADDING * 2),
                x + PADDING,
                y + PURITY_Y + index * ROW_HEIGHT,
                if (entry.resolved) FOREGROUND else UNRESOLVED,
                false,
            )
        }
    }

    private fun button(
        graphics: GuiGraphics,
        offset: Int,
        text: Component,
    ) {
        graphics.fill(
            x + offset,
            y + BUTTON_Y,
            x + offset + PANEL_WIDTH / 2 - PADDING,
            y + BUTTON_Y + BUTTON_HEIGHT,
            BUTTON_COLOR,
        )
        graphics.drawString(font, text, x + offset + PADDING, y + BUTTON_Y + PADDING, FOREGROUND, false)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean =
        when {
            !visible || !isMouseOver(mouseX, mouseY) -> false
            mouseY - y < VIEW_HEIGHT -> selectView(mouseX)
            viewState.settings -> false
            else -> handleStorageClick(mouseX, mouseY, button)
        }

    private fun selectView(mouseX: Double): Boolean {
        viewState.settings = mouseX - x >= PANEL_WIDTH / 2
        searchFocused = false
        return true
    }

    private fun handleStorageClick(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val localY = mouseY - y
        searchFocused = localY in SEARCH_Y.toDouble()..LIST_Y.toDouble()
        when {
            localY >= LIST_Y && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT -> {
                filtered().getOrNull(listOffset + ((localY - LIST_Y) / ROW_HEIGHT).toInt())?.let {
                    selected = it.id
                    purityOffset = 0
                }
            }
            localY >= PURITY_Y && localY < PURITY_Y + VISIBLE_ROWS * ROW_HEIGHT -> {
                purityRows()
                    .getOrNull(
                        purityOffset + ((localY - PURITY_Y) / ROW_HEIGHT).toInt(),
                    )?.let { withdraw(it, button) }
            }
            localY >= BUTTON_Y && localY < BUTTON_Y + BUTTON_HEIGHT -> {
                val action = if (mouseX - x < PANEL_WIDTH / 2) GemAction.INSERT_CURSOR else GemAction.MOVE_NORMAL
                GemPageCache.send(screen.menu, container.upgradeContainerId, action)
            }
        }
        return true
    }

    private fun withdraw(
        entry: GemPageEntry,
        button: Int,
    ) {
        if (entry.resolved && entry.count > 0 && button in 0..1) {
            val action =
                when {
                    Screen.hasShiftDown() && button == 0 -> GemAction.WITHDRAW_TO_PLAYER
                    button == 1 -> GemAction.WITHDRAW_STACK
                    else -> GemAction.WITHDRAW_ONE
                }
            GemPageCache.send(screen.menu, container.upgradeContainerId, action, entry.key)
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        val hovered = !viewState.settings && visible && isMouseOver(mouseX, mouseY)
        if (hovered) {
            val delta = if (vertical > 0) -1 else 1
            if (mouseY - y <
                PURITY_Y
            ) {
                listOffset = maxOf(0, listOffset + delta)
            } else {
                purityOffset = maxOf(0, purityOffset + delta)
            }
        }
        return hovered
    }

    override fun charTyped(
        character: Char,
        modifiers: Int,
    ): Boolean {
        val accepted =
            !viewState.settings &&
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
        val accepted = !viewState.settings && visible && searchFocused && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
        if (accepted &&
            key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE &&
            search.isNotEmpty()
        ) {
            search = search.dropLast(1)
        }
        return accepted
    }

    override fun isFocused(): Boolean = !viewState.settings && searchFocused

    override fun setFocused(focused: Boolean) {
        searchFocused = focused
    }
}
