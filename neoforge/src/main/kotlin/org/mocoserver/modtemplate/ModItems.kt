package org.mocoserver.modtemplate

import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import org.mocoserver.modtemplate.core.checkedQuantity

/** loader側でitemを登録する。coreへゲーム型を渡さない。 */
internal object ModItems {
    private const val MAX_STACK_SIZE = 64
    val registry: DeferredRegister.Items = DeferredRegister.createItems(ModTemplate.ID)
    val sampleToken: DeferredItem<Item> =
        registry.registerSimpleItem(
            "sample_token",
            Item.Properties().stacksTo(checkedQuantity(MAX_STACK_SIZE)),
        )
}
