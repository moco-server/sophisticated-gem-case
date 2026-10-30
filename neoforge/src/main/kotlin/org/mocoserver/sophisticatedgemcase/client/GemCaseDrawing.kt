package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/** 自作の矩形描画で木の収納面と石の枠を表現し、外部画像を配布物へ複製しない。 */
@Suppress("MagicNumber", "LongParameterList") // 枠のpixel座標と描画引数をまとめ、外部素材を不要にする。
internal object GemCaseDrawing {
    const val TEXT: Int = -1979735
    const val MUTED: Int = -6517117
    const val GOLD: Int = -1195485
    const val WARNING: Int = -21846
    private const val OUTLINE: Int = -15461356
    private const val STONE: Int = -11579569
    private const val STONE_LIGHT: Int = -7960954
    private const val WOOD: Int = -12442855
    private const val WOOD_LIGHT: Int = -10932193
    private const val SLOT: Int = -14281458

    fun frame(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        wooden: Boolean = false,
    ) {
        graphics.fill(x, y, x + width, y + height, OUTLINE)
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, STONE_LIGHT)
        graphics.fill(x + 3, y + 3, x + width - 2, y + height - 2, STONE)
        graphics.fill(x + 7, y + 7, x + width - 7, y + height - 7, if (wooden) WOOD else SLOT)
        if (wooden) {
            for (line in 9 until height - 7 step 6) {
                graphics.fill(x + 7, y + line, x + width - 7, y + line + 1, WOOD_LIGHT)
            }
        }
    }

    fun slot(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        selected: Boolean = false,
    ) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, if (selected) GOLD else -14675195)
        graphics.fill(x, y, x + 16, y + 16, SLOT)
        graphics.fill(x, y, x + 16, y + 1, OUTLINE)
        graphics.fill(x, y, x + 1, y + 16, OUTLINE)
    }

    fun button(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        hovered: Boolean,
        enabled: Boolean = true,
    ) {
        graphics.fill(x, y, x + width, y + height, OUTLINE)
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, if (hovered && enabled) STONE_LIGHT else STONE)
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, if (enabled) -10988248 else -13684945)
    }

    fun item(
        graphics: GuiGraphics,
        stack: ItemStack,
        x: Int,
        y: Int,
        count: Long? = null,
        ghost: Boolean = false,
    ) {
        graphics.renderFakeItem(stack, x, y)
        if (ghost) {
            // アイテムは奥行きを持つため、空在庫の覆いを手前へ描く。
            graphics.pose().pushPose()
            graphics.pose().translate(0.0f, 0.0f, 200.0f)
            graphics.fill(x, y, x + 16, y + 16, 0xB0101010.toInt())
            graphics.pose().popPose()
        }
        if (count != null && count > 0L) count(graphics, count, x, y)
    }

    private fun count(
        graphics: GuiGraphics,
        count: Long,
        x: Int,
        y: Int,
    ) {
        val font = Minecraft.getInstance().font
        val text = count.toString()
        val scale = minOf(1.0f, 16.0f / font.width(text))
        graphics.pose().pushPose()
        graphics.pose().translate((x + 16).toFloat(), (y + 16).toFloat(), 200.0f)
        graphics.pose().scale(scale, scale, 1.0f)
        graphics.drawString(font, text, -font.width(text), -font.lineHeight + 1, -1, true)
        graphics.pose().popPose()
    }

    fun shiftHint(): Component = gemLabel("details_shift").copy().withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC)

    fun description(key: String): List<Component> = gemLabel(key).string.split("\n").map { Component.literal(it) }

    fun tooltip(
        graphics: GuiGraphics,
        lines: List<Component>,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (lines.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val wrapWidth = minOf(300, minecraft.window.guiScaledWidth - 24).coerceAtLeast(40)
        // 段落と書式を保って折り返し、説明を固定行数で切り捨てない。
        val wrapped = lines.flatMap { minecraft.font.split(it, wrapWidth) }
        graphics.renderTooltip(minecraft.font, wrapped, mouseX, mouseY)
    }
}
