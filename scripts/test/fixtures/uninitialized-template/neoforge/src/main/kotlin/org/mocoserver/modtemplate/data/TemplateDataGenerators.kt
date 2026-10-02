package org.mocoserver.modtemplate.data

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.world.item.Items
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import org.mocoserver.modtemplate.ModItems
import org.mocoserver.modtemplate.ModTemplate
import org.mocoserver.modtemplate.core.checkedQuantity
import java.util.concurrent.CompletableFuture

/** 対象版のnative providersでresourceを生成する。 */
internal object TemplateDataGenerators {
    fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        generator.addProvider(event.includeServer(), TemplateRecipes(output, event.lookupProvider))
        generator.addProvider(event.includeClient(), TemplateModels(output, event.existingFileHelper))
        generator.addProvider(event.includeClient(), TemplateLanguage(output, "ja_jp", "サンプルトークン"))
        generator.addProvider(event.includeClient(), TemplateLanguage(output, "en_us", "Sample Token"))
    }
}

private class TemplateRecipes(
    output: PackOutput,
    lookup: CompletableFuture<HolderLookup.Provider>,
) : RecipeProvider(output, lookup) {
    override fun buildRecipes(output: RecipeOutput) {
        ShapelessRecipeBuilder
            .shapeless(RecipeCategory.MISC, ModItems.sampleToken.get(), checkedQuantity(1))
            .requires(Items.IRON_INGOT)
            .requires(Items.PAPER)
            .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
            .save(output)
    }
}

private class TemplateModels(
    output: PackOutput,
    helper: ExistingFileHelper,
) : ItemModelProvider(output, ModTemplate.ID, helper) {
    override fun registerModels() {
        withExistingParent("sample_token", mcLoc("item/generated")).texture("layer0", mcLoc("item/iron_nugget"))
    }
}

private class TemplateLanguage(
    output: PackOutput,
    locale: String,
    private val itemName: String,
) : LanguageProvider(output, ModTemplate.ID, locale) {
    override fun addTranslations() {
        add(ModItems.sampleToken.get(), itemName)
    }
}
