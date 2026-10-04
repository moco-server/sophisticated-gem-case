package org.mocoserver.sophisticatedgemcase

import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import org.mocoserver.sophisticatedgemcase.core.checkedQuantity
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseConversionItem

/** loader側でitemを登録する。coreへゲーム型を渡さない。 */
internal object ModItems {
    private const val MAX_STACK_SIZE = 64
    val registry: DeferredRegister.Items = DeferredRegister.createItems(ModTemplate.ID)
    val gemCaseUpgrade: DeferredItem<org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem> =
        registry.register(
            "gem_case_upgrade",
            java.util.function.Supplier {
                org.mocoserver.sophisticatedgemcase.upgrade
                    .GemCaseUpgradeItem()
            },
        )
    val sampleToken: DeferredItem<Item> =
        registry.registerSimpleItem(
            "sample_token",
            Item.Properties().stacksTo(checkedQuantity(MAX_STACK_SIZE)),
        )
    val advancedGemCaseUpgrade: DeferredItem<org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem> =
        registry.register(
            "advanced_gem_case_upgrade",
            java.util.function.Supplier {
                org.mocoserver.sophisticatedgemcase.upgrade
                    .GemCaseUpgradeItem(supportsProcessing = true)
            },
        )
    val gemCaseUpgradeConversion: DeferredItem<GemCaseConversionItem> =
        registry.register("gem_case_upgrade_conversion", java.util.function.Supplier { GemCaseConversionItem() })
}
