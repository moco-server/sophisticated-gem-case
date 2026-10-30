package org.mocoserver.sophisticatedgemcase.client

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemPageEntry
import org.mocoserver.sophisticatedgemcase.storage.StandardGem

/** 登録済み種類と保存済み未解決entryを合わせ、未所持も選択できる一覧を作る。 */
internal class GemCaseCatalog {
    private var displayStacks: Map<String, ItemStack> = emptyMap()
    private var entries: List<GemPageEntry> = emptyList()
    private var generation: Any? = null

    fun refresh(
        cache: GemPageAccumulator,
        model: GemBrowserModel,
    ) {
        val registered = GemRegistry.INSTANCE.getValues().toList()
        val language =
            net.minecraft.client.Minecraft
                .getInstance()
                .languageManager.selected
        val nextGeneration =
            listOf(cache.sessionId, cache.revision, cache.entries.size, cache.complete, registered, language)
        if (generation == nextGeneration) return
        generation = nextGeneration
        entries = cache.entries.toList()
        displayStacks = registered.associate { it.id.toString() to GemItem.createStack(it, it.minPurity, 1) }
        val saved = entries.groupBy { it.key.gemId }
        model.update(
            (displayStacks.keys + saved.keys).map { id ->
                val stack = displayStacks[id]
                GemBrowserEntry(
                    id,
                    stack?.let { Component.translatable(it.descriptionId).string } ?: id,
                    saved[id].orEmpty().sumOf { it.count.toLong() },
                    stack != null,
                )
            },
        )
    }

    fun stack(
        id: String,
        purity: Purity? = null,
    ): ItemStack =
        if (purity == null) {
            displayStacks[id] ?: ItemStack(Items.BARRIER)
        } else {
            StandardGem.stackOf(GemKey(id, purity.serializedName)) ?: ItemStack.EMPTY
        }

    fun count(
        id: String,
        purity: Purity,
    ): Int = entries.firstOrNull { it.key.gemId == id && it.key.purity == purity.serializedName }?.count ?: 0

    fun unresolved(): List<GemPageEntry> = entries.filter { !it.resolved }
}
