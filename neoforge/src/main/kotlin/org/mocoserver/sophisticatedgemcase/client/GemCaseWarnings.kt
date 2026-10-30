package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.mocoserver.sophisticatedgemcase.network.GemPageEntry

/** 未解決の種類・純度も保存IDと正確な個数で確認できる。 */
@Suppress("MagicNumber") // 一覧の描画寸法は中央UIの空き領域へ固定する。
internal class GemCaseWarnings {
    private var offset = 0
    private var visibleEntries: List<GemPageEntry> = emptyList()

    fun render(
        graphics: GuiGraphics,
        entries: List<GemPageEntry>,
        search: String,
        x: Int,
        y: Int,
    ) {
        val font = Minecraft.getInstance().font
        visibleEntries = entries.filter { "${it.key.gemId} ${it.key.purity}".contains(search, ignoreCase = true) }
        offset = offset.coerceIn(0, maxOf(0, visibleEntries.size - 6))
        graphics.drawString(font, gemLabel("unresolved"), x + 68, y + 32, GemCaseDrawing.WARNING, false)
        visibleEntries.drop(offset).take(6).forEachIndexed { index, entry ->
            val text = "${entry.key.gemId}/${entry.key.purity}: ${entry.count}"
            graphics.drawString(
                font,
                font.plainSubstrByWidth(text, 148),
                x + 68,
                y + 47 + index * 12,
                GemCaseDrawing.TEXT,
                false,
            )
        }
    }

    fun scroll(vertical: Double) {
        if (vertical !=
            0.0
        ) {
            offset = (offset + if (vertical > 0) -1 else 1).coerceIn(0, maxOf(0, visibleEntries.size - 6))
        }
    }

    fun tooltip(
        mouseY: Int,
        y: Int,
    ): List<Component> {
        val row = (mouseY - y - 47) / 12
        if (mouseY < y + 47 || row !in 0..5) return emptyList()
        return visibleEntries
            .getOrNull(offset + row)
            ?.let { entry ->
                listOf(
                    Component.literal(entry.key.gemId),
                    Component.literal(entry.key.purity),
                    gemLabel("owned", entry.count),
                )
            }.orEmpty()
    }
}
