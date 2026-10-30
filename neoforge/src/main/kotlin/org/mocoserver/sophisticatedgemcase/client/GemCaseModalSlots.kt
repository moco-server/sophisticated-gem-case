package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.mixin.client.GemStorageScreenAccessor
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes

/** 実slotの座標を変更せず、同じmenuの予測・差分送信経路へmodal入力を渡す。 */
internal class GemCaseModalSlots(
    private val screen: StorageScreenBase<*>,
    private val container: GemCaseContainer,
) {
    private val input = ModalSlotInput(screen)
    private var positions: List<ModalSlotPosition> = emptyList()
    private var hovered: Slot? = null
    private var closed = false
    private var materialsVisible = false

    @Suppress("LongParameterList") // Widgetのmouse座標・modal原点・表示許可を指定済みの接続契約で受ける。
    fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        showMaterials: Boolean,
    ) {
        if (!isCurrent()) return
        setMaterialsVisible(showMaterials)
        positions = slotPositions(x, y, showMaterials && container.upgradeWrapper.supportsProcessing)
        hovered = positions.firstOrNull { it.contains(mouseX.toDouble(), mouseY.toDouble()) }?.slot
        val font = Minecraft.getInstance().font
        positions.forEach { position ->
            val left = position.x
            val top = position.y
            graphics.fill(left - 1, top - 1, left + ITEM_SIZE + 1, top + ITEM_SIZE + 1, SLOT_BORDER)
            graphics.fill(left, top, left + ITEM_SIZE, top + ITEM_SIZE, SLOT_BACKGROUND)
            graphics.renderItem(position.slot.item, left, top)
            graphics.renderItemDecorations(font, position.slot.item, left, top)
            if (position.slot === hovered) {
                graphics.fill(left, top, left + ITEM_SIZE, top + ITEM_SIZE, SLOT_HOVER)
            }
            if (position.slot is GemMaterialSlot && !position.slot.uiEditable) {
                graphics.fill(left, top, left + ITEM_SIZE, top + ITEM_SIZE, SLOT_LOCKED)
            }
        }
    }

    fun setMaterialsVisible(visible: Boolean) {
        val opened = visible && !closed && container.upgradeWrapper.supportsProcessing
        val wasVisible = materialsVisible
        materialsVisible = opened
        container.processingViewOpen = opened
        container.slots.filterIsInstance<GemMaterialSlot>().forEach {
            it.processingVisible = opened
            if (!opened) it.uiEditable = false
        }
        if (Minecraft.getInstance().player?.containerMenu === screen.menu) {
            GemProcessingViewCache.setProcessingView(screen.menu, container.upgradeContainerId, opened)
            if (opened) GemProcessingCache.updateMaterialSlots(screen.menu, container.upgradeContainerId)
        }
        if (!opened) {
            positions = positions.filter { it.slot !is GemMaterialSlot }
            if (hovered is GemMaterialSlot) hovered = null
            if (wasVisible) input.cancel()
        }
    }

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (!isCurrent()) return true
        val slot = positions.firstOrNull { it.contains(mouseX, mouseY) }?.slot
        if (slot != null && (slot !is GemMaterialSlot || slot.uiEditable)) input.clicked(slot, button)
        return slot != null
    }

    fun mouseReleased(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ) {
        if (isCurrent()) input.released(positions.firstOrNull { it.contains(mouseX, mouseY) }?.slot, button)
    }

    fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ) {
        if (isCurrent() && (dragX != 0.0 || dragY != 0.0)) {
            input.dragged(positions.firstOrNull { it.contains(mouseX, mouseY) }?.slot, button)
        }
    }

    fun keyPressed(
        key: Int,
        scan: Int,
        modifiers: Int,
    ): Boolean {
        if (!isCurrent()) return true
        val slot = hovered
        // 修飾キー付きの検索編集をhotbar操作として解釈しない。
        val eligible = modifiers == 0 && slot != null && slot !is GemMaterialSlot
        val hotbar =
            if (eligible) {
                Minecraft
                    .getInstance()
                    .options.keyHotbarSlots
                    .indexOfFirst { it.matches(key, scan) }
            } else {
                -1
            }
        val handled = slot != null && hotbar >= 0
        if (slot != null && handled) {
            input.cancel()
            input.click(slot, hotbar, ClickType.SWAP)
        }
        return handled
    }

    fun renderTooltip(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (!isCurrent() || !screen.menu.carried.isEmpty) return
        val stack = hovered?.item ?: return
        if (!stack.isEmpty) graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY)
    }

    fun close() {
        if (closed) return
        closed = true
        setMaterialsVisible(false)
        input.cancel()
        positions = emptyList()
        hovered = null
    }

    private fun isCurrent(): Boolean {
        val current = !closed && GemCaseModalLifecycle.isCurrent(screen, container)
        if (!current && !closed) {
            close()
            GemCaseModalLifecycle.closeOverlayLater(screen)
        }
        return current
    }

    private fun slotPositions(
        x: Int,
        y: Int,
        showMaterials: Boolean,
    ): List<ModalSlotPosition> {
        val player = Minecraft.getInstance().player ?: return emptyList()
        val inventory =
            screen.menu.slots
                .filter { it.container === player.inventory && it.slotIndex in 0 until PLAYER_SLOTS }
                .map { slot ->
                    val index = slot.slotIndex
                    val row =
                        if (index <
                            INVENTORY_COLUMNS
                        ) {
                            HOTBAR_Y
                        } else {
                            PLAYER_Y + (index / INVENTORY_COLUMNS - 1) * SLOT_STEP
                        }
                    ModalSlotPosition(slot, x + PLAYER_X + index % INVENTORY_COLUMNS * SLOT_STEP, y + row)
                }
        val materials =
            if (showMaterials) {
                container.slots.filterIsInstance<GemMaterialSlot>().mapIndexed { index, slot ->
                    ModalSlotPosition(
                        slot,
                        x + MATERIAL_X + index % MATERIAL_COLUMNS * SLOT_STEP,
                        y + MATERIAL_Y + index / MATERIAL_COLUMNS * SLOT_STEP,
                    )
                }
            } else {
                emptyList()
            }
        return inventory + materials
    }
}

private data class ModalSlotPosition(
    val slot: Slot,
    val x: Int,
    val y: Int,
) {
    fun contains(
        mouseX: Double,
        mouseY: Double,
    ): Boolean = mouseX >= x - 1 && mouseX < x + ITEM_SIZE + 1 && mouseY >= y - 1 && mouseY < y + ITEM_SIZE + 1
}

/** dragの計画はreleaseまでローカルに保持し、中断時はnative側へ半端な状態を残さない。 */
private class ModalSlotInput(
    private val screen: StorageScreenBase<*>,
) {
    private var pressed: PressedSlot? = null
    private var lastClick: LastSlotClick? = null

    fun clicked(
        slot: Slot,
        button: Int,
    ) {
        cancel()
        if (button !in LEFT_BUTTON..RIGHT_BUTTON || !slot.isActive) return
        val cursor = screen.menu.carried
        val now = System.nanoTime()
        val previous = lastClick
        val doubleClick =
            previous?.index == slot.index && previous.button == button && now - previous.time < DOUBLE_CLICK_NANOS
        lastClick = LastSlotClick(slot.index, button, now)
        when {
            Screen.hasShiftDown() -> click(slot, button, ClickType.QUICK_MOVE)
            doubleClick && button == LEFT_BUTTON && !cursor.isEmpty && permitsAdvancedGesture(slot, cursor) -> {
                click(slot, button, ClickType.PICKUP_ALL)
                lastClick = null
            }
            !cursor.isEmpty && permitsAdvancedGesture(slot, cursor) -> pressed = PressedSlot(slot, button)
            else -> click(slot, button, ClickType.PICKUP)
        }
    }

    fun dragged(
        slot: Slot?,
        button: Int,
    ) {
        val pending = pressed ?: return
        if (button != pending.button || slot == null || !canDragInto(slot)) return
        pending.slots.add(slot)
    }

    fun released(
        slot: Slot?,
        button: Int,
    ) {
        val pending = pressed ?: return
        pressed = null
        if (button != pending.button) return
        if (pending.slots.size > 1) {
            // Core 1.5.2.2343のheader/type契約。開始/終了時も有効な論理indexを渡す。
            click(pending.origin, quickCraftButton(QUICK_CRAFT_START, button), ClickType.QUICK_CRAFT)
            pending.slots.forEach { click(it, quickCraftButton(QUICK_CRAFT_ADD, button), ClickType.QUICK_CRAFT) }
            click(pending.origin, quickCraftButton(QUICK_CRAFT_END, button), ClickType.QUICK_CRAFT)
        } else if (slot === pending.origin) {
            click(pending.origin, button, ClickType.PICKUP)
        }
    }

    fun click(
        slot: Slot,
        button: Int,
        type: ClickType,
    ) {
        // 旧menuのslot番号が現menuで別の意味を持つ場合に送信しない。
        if (slot.index in 0 until screen.menu.totalSlotsNumber && screen.menu.getSlot(slot.index) === slot) {
            (screen as GemStorageScreenAccessor).gemCaseClickSlot(slot, slot.index, button, type)
        }
    }

    fun cancel() {
        pressed = null
    }

    private fun canDragInto(slot: Slot): Boolean {
        val cursor = screen.menu.carried
        val acceptsCursor =
            slot.mayPlace(cursor) &&
                screen.menu.canDragTo(slot) &&
                StorageContainerMenuBase.canItemQuickReplace(slot, cursor)
        return permitsAdvancedGesture(slot, cursor) && slot.isActive && acceptsCursor
    }

    private fun permitsAdvancedGesture(
        slot: Slot,
        cursor: ItemStack,
    ): Boolean {
        val player = Minecraft.getInstance().player
        if (slot is GemMaterialSlot || player == null) return false
        val storedMaterial =
            screen.menu.upgradeContainers.values
                .filterIsInstance<GemCaseContainer>()
                .flatMap { it.slots.filterIsInstance<GemMaterialSlot>() }
                .any { !it.item.isEmpty && ItemStack.isSameItemSameComponents(it.item, cursor) }
        // 素材の原子的な保存境界は左右/Shiftだけを許すため、clientも同じ制約を守る。
        return !GemPurityRecipes.isMaterial(player.level(), cursor) && !storedMaterial
    }

    private fun quickCraftButton(
        header: Int,
        button: Int,
    ): Int = header or (button shl QUICK_CRAFT_TYPE_SHIFT)

    private data class PressedSlot(
        val origin: Slot,
        val button: Int,
        val slots: MutableSet<Slot> = linkedSetOf(origin),
    )

    private data class LastSlotClick(
        val index: Int,
        val button: Int,
        val time: Long,
    )
}

private const val ITEM_SIZE = 16
private const val SLOT_STEP = 18
private const val MATERIAL_COLUMNS = 2
private const val MATERIAL_X = 12
private const val MATERIAL_Y = 36
private const val INVENTORY_COLUMNS = 9
private const val PLAYER_SLOTS = 36
private const val PLAYER_X = 64
private const val PLAYER_Y = 148
private const val HOTBAR_Y = 206
private const val SLOT_BORDER = -14673908
private const val SLOT_BACKGROUND = -11450310
private const val SLOT_HOVER = 0x50FFFFFF
private const val SLOT_LOCKED = -2147483648
private const val LEFT_BUTTON = 0
private const val RIGHT_BUTTON = 1
private const val DOUBLE_CLICK_NANOS = 250_000_000L
private const val QUICK_CRAFT_START = 0
private const val QUICK_CRAFT_ADD = 1
private const val QUICK_CRAFT_END = 2
private const val QUICK_CRAFT_TYPE_SHIFT = 2
