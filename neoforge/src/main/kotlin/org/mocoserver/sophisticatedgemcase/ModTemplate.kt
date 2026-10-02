package org.mocoserver.sophisticatedgemcase

import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import org.mocoserver.sophisticatedgemcase.data.TemplateDataGenerators
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/** NeoForgeの登録とイベント接続を行う。 */
@Mod(ModTemplate.ID)
object ModTemplate {
    const val ID = "sophisticated_gem_case"

    init {
        ModItems.registry.register(MOD_BUS)
        MOD_BUS.addListener(TemplateDataGenerators::gather)
        MOD_BUS.addListener(::addCreativeItems)
    }

    private fun addCreativeItems(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.INGREDIENTS) event.accept(ModItems.sampleToken.get())
    }
}
