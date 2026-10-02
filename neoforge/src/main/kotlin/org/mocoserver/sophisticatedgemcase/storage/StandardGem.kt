package org.mocoserver.sophisticatedgemcase.storage

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.mocoserver.sophisticatedgemcase.core.GemKey

/** 追加componentや暗黙の純度補正を個数保存へ持ち込まず、再生成できるGemだけを扱う。 */
internal object StandardGem {
    fun keyOf(stack: ItemStack): GemKey? {
        if (stack.isEmpty || stack.item !is GemItem) return null
        val holder = GemItem.getGem(stack)
        val purity = GemItem.getPurity(stack)
        val canonical =
            if (holder.isBound && purity.isAtLeast(holder.get().minPurity)) {
                GemItem.createStack(holder.get(), purity, 1)
            } else {
                ItemStack.EMPTY
            }
        return if (!canonical.isEmpty && ItemStack.isSameItemSameComponents(stack, canonical)) {
            GemKey(holder.id.toString(), purity.serializedName)
        } else {
            null
        }
    }

    fun stackOf(key: GemKey): ItemStack? {
        val holder = ResourceLocation.tryParse(key.gemId)?.let { GemRegistry.INSTANCE.holder(it) }
        val purity = Purity.entries.firstOrNull { it.serializedName == key.purity }
        return if (holder?.isBound == true && purity != null && purity.isAtLeast(holder.get().minPurity)) {
            GemItem.createStack(holder.get(), purity, 1)
        } else {
            null
        }
    }
}
