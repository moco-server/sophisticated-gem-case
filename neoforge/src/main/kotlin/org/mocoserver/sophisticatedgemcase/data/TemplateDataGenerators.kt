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
        generator.addProvider(
            event.includeServer(),
            GemCaseItemTags(output, event.lookupProvider, event.existingFileHelper),
        )
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
        withExistingParent(
            "advanced_gem_case_upgrade",
            mcLoc("item/generated"),
        ).texture("layer0", mcLoc("item/diamond"))
        withExistingParent(
            "gem_case_upgrade_conversion",
            mcLoc("item/generated"),
        ).texture("layer0", mcLoc("item/amethyst_shard"))
        withExistingParent("sample_token", mcLoc("item/generated")).texture("layer0", mcLoc("item/iron_nugget"))
    }
}

private class TemplateLanguage(
    output: PackOutput,
    locale: String,
    private val itemName: String,
) : LanguageProvider(output, ModTemplate.ID, locale) {
    override fun addTranslations() {
        val japanese = itemName == "サンプルトークン"
        addGuiTranslations(japanese)
        addItemTranslations(japanese)
    }

    private fun addGuiTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "title" to ("Gem Case" to "Gem Case"),
                "storage" to ("収納" to "Storage"),
                "settings" to ("転送設定" to "Transfer settings"),
                "processing" to ("加工" to "Processing"),
                "search" to ("検索: " to "Search: "),
                "insert" to ("カーソル投入" to "Insert cursor"),
                "move" to ("一括移動" to "Move from bag"),
                "loading" to ("一覧を同期しています" to "Synchronizing list"),
                "locked" to ("保存を保持して操作停止" to "Storage locked; data retained"),
                "help" to ("左:1 右:stack Shift:取出" to "Left:1 Right:stack Shift:inventory"),
                "deposit" to ("Deposit" to "Deposit"),
                "deposit_selected" to ("▶ Deposit" to "▶ Deposit"),
                "restock" to ("Restock" to "Restock"),
                "restock_selected" to ("▶ Restock" to "▶ Restock"),
                "enabled" to ("対象: ON" to "Enabled"),
                "disabled" to ("対象: OFF" to "Disabled"),
                "mode_all" to ("方式: すべて" to "Mode: all"),
                "mode_include" to ("方式: 指定" to "Mode: include"),
                "mode_exclude" to ("方式: 除外" to "Mode: exclude"),
                "gem_ids" to ("種類" to "Gem types"),
                "purities" to ("純度" to "Purities"),
                "settings_help" to ("一覧を選択して条件を変更" to "Select entries to change the filter"),
                "processing_select_gem" to ("種類を選択" to "Select gem"),
                "processing_select_purity" to ("目標純度を選択" to "Select target purity"),
                "processing_materials" to ("必要素材:" to "Required materials:"),
                "processing_no_costs" to ("素材なし" to "No material costs"),
                "processing_left" to ("左" to "Left"),
                "processing_right" to ("右" to "Right"),
                "processing_supplement_on" to ("通常収納から補充: ON" to "Backpack materials: ON"),
                "processing_supplement_off" to ("通常収納から補充: OFF" to "Backpack materials: OFF"),
                "processing_run" to ("加工 (Shift: 最大)" to "Process (Shift: all)"),
                "processing_preview_loading" to ("加工内容を確認しています" to "Checking processing preview"),
                "processing_reason_ready" to ("加工できます" to "Ready to process"),
                "processing_reason_not_advanced" to ("上位純度の加工ではありません" to "Not an advanced purity"),
                "processing_reason_unresolved_gem" to ("宝石の種類を解決できません" to "Gem type is unresolved"),
                "processing_reason_locked_counts" to ("収納一覧がロックされています" to "Gem storage counts are locked"),
                "processing_reason_locked_materials" to ("加工素材がロックされています" to "Processing materials are locked"),
                "processing_reason_locked_settings" to ("加工設定がロックされています" to "Processing settings are locked"),
                "processing_reason_input_shortage" to ("入力素材が不足しています" to "Input materials are missing"),
                "processing_reason_output_full" to ("出力先に空きがありません" to "Output storage is full"),
                "processing_reason_material_shortage" to ("加工素材が不足しています" to "Processing materials are missing"),
                "processing_reason_budget_exceeded" to ("確認範囲を超えました" to "Processing preview budget exceeded"),
                "processing_reason_preview_oversize" to ("加工内容が大きすぎます" to "Processing preview is too large"),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
        addTransferErrorTranslations(japanese)
    }

    private fun addTransferErrorTranslations(japanese: Boolean) {
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

    private fun addItemTranslations(japanese: Boolean) {
        add(ModItems.sampleToken.get(), itemName)
        add(ModItems.gemCaseUpgrade.get(), if (japanese) "Gem Caseアップグレード" else "Gem Case Upgrade")
        add(ModItems.advancedGemCaseUpgrade.get(), if (japanese) "上位Gem Caseアップグレード" else "Advanced Gem Case Upgrade")
        add(ModItems.gemCaseUpgradeConversion.get(), if (japanese) "Gem Case上位化" else "Gem Case Conversion")
        add("sophisticated_gem_case.upgrade_group.gem_case", "Gem Case")
    }
}
