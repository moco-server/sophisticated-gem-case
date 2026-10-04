package org.mocoserver.sophisticatedgemcase

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry
import org.mocoserver.sophisticatedgemcase.data.TemplateDataGenerators
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemNetworking
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/** NeoForgeの登録とイベント接続を行う。 */
@Mod(ModTemplate.ID)
object ModTemplate {
    const val ID = "sophisticated_gem_case"

    init {
        ModItems.registry.register(MOD_BUS)
        ModDataComponents.registry.register(MOD_BUS)
        MOD_BUS.addListener(TemplateDataGenerators::gather)
        MOD_BUS.addListener(::commonSetup)
        MOD_BUS.addListener(GemNetworking::register)
        MOD_BUS.addListener(::addCreativeItems)
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            UpgradeContainerRegistry.register(
                ResourceLocation.fromNamespaceAndPath(ID, "gem_case_upgrade"),
                GemCaseContainer.TYPE,
            )
            UpgradeContainerRegistry.register(
                ResourceLocation.fromNamespaceAndPath(ID, "advanced_gem_case_upgrade"),
                GemCaseContainer.TYPE,
            )
        }
    }

    private fun addCreativeItems(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.sampleToken.get())
            event.accept(ModItems.gemCaseUpgrade.get())
            event.accept(ModItems.advancedGemCaseUpgrade.get())
            event.accept(ModItems.gemCaseUpgradeConversion.get())
        }
    }
}
