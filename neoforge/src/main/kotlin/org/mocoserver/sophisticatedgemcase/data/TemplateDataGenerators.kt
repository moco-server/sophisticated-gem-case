package org.mocoserver.sophisticatedgemcase.data

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
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.checkedQuantity
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
        withExistingParent("gem_case_upgrade", mcLoc("item/generated")).texture("layer0", mcLoc("item/emerald"))
        withExistingParent("sample_token", mcLoc("item/generated")).texture("layer0", mcLoc("item/iron_nugget"))
    }
}

private class TemplateLanguage(
    output: PackOutput,
    locale: String,
    private val itemName: String,
) : LanguageProvider(output, ModTemplate.ID, locale) {
    override fun addTranslations() {
        val labels =
            mapOf(
                "title" to ("Gem Case" to "Gem Case"),
                "search" to ("検索: " to "Search: "),
                "insert" to ("カーソル投入" to "Insert cursor"),
                "move" to ("一括移動" to "Move from bag"),
                "loading" to ("一覧を同期しています" to "Synchronizing list"),
                "locked" to ("保存を保持して操作停止" to "Storage locked; data retained"),
                "help" to ("左:1 右:stack Shift:取出" to "Left:1 Right:stack Shift:inventory"),
            )
        labels.forEach { (key, names) ->
            add(
                "sophisticated_gem_case.gui.$key",
                if (itemName ==
                    "サンプルトークン"
                ) {
                    names.first
                } else {
                    names.second
                },
            )
        }
        add(ModItems.sampleToken.get(), itemName)
        val japanese = itemName == "サンプルトークン"
        add(ModItems.gemCaseUpgrade.get(), if (japanese) "Gem Caseアップグレード" else "Gem Case Upgrade")
        add(
            "sophisticated_gem_case.error.duplicate",
            if (japanese) "専用アップグレードは一個までです" else "Only one Gem Case upgrade is allowed",
        )
        add(
            "sophisticated_gem_case.error.not_empty",
            if (japanese) "中身のある収納は取り外せません" else "Empty the Gem Case before removing it",
        )
        add(
            "sophisticated_gem_case.error.operation",
            if (japanese) "操作できません。保存状態と取出先を確認してください" else "Operation rejected; check storage and destination",
        )
        add(
            "sophisticated_gem_case.error.unsafe_click",
            if (japanese) "GemはクリックかShiftで投入してください" else "Insert gems with a click or Shift-click",
        )
        add(
            "sophisticated_gem_case.error.backpack_only",
            if (japanese) "バッグ専用です" else "This upgrade requires a backpack",
        )
    }
}
