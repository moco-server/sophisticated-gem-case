package org.mocoserver.sophisticatedgemcase.data

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.resources.ResourceLocation
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
        GemCaseRecipes.build(output)
    }
}

private class TemplateModels(
    output: PackOutput,
    helper: ExistingFileHelper,
) : ItemModelProvider(output, ModTemplate.ID, helper) {
    override fun registerModels() {
        withExistingParent(
            "gem_case_upgrade",
            mcLoc("item/generated"),
        ).texture("layer0", ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "item/upgrade_base"))
            .texture("layer1", modLoc("item/gem_case_upgrade"))
        withExistingParent(
            "advanced_gem_case_upgrade",
            mcLoc("item/generated"),
        ).texture("layer0", ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "item/upgrade_base"))
            .texture("layer1", modLoc("item/advanced_gem_case_upgrade"))
        withExistingParent(
            "gem_case_upgrade_conversion",
            mcLoc("item/generated"),
        ).texture("layer0", ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "item/upgrade_base"))
            .texture("layer1", modLoc("item/gem_case_upgrade_conversion"))
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
        addModalTranslations(japanese)
        addGuiTranslations(japanese)
        addItemTranslations(japanese)
    }

    private fun addModalTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "open" to ("ケースを開く" to "Open case"),
                "back" to ("戻る" to "Back"),
                "search_hint" to ("ジェムを検索" to "Search gems"),
                "owned" to ("所持数: %s" to "Owned: %s"),
                "owned_capacity" to ("所持数: %s / 32,767" to "Owned: %s / 32,767"),
                "unresolved" to ("読み込めないジェム" to "Unavailable gems"),
                "unavailable_purity" to ("この純度は使用できません" to "This purity is unavailable"),
                "upgrade_to" to ("%s に強化" to "Upgrade to %s"),
                "upgrade_help" to ("クリック: 1回 / Shift: 最大64回" to "Click: once / Shift: up to 64"),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
    }

    private fun addToolTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "details_shift" to ("Shiftで詳しい説明" to "Hold Shift for details"),
                "back_detail" to ("Gem Caseを閉じてリュックへ戻ります。" to "Close Gem Case and return to the backpack."),
                "back_detail_extra" to ("収納内容や設定は保持されます。" to "Stored gems and settings are retained."),
                "settings_detail" to ("Deposit・Restockの対象を設定します。" to "Choose gems for Deposit and Restock."),
                "settings_detail_extra" to ("転送には対応するアップグレードが必要です。" to "The matching transfer upgrade is required."),
                "insert_detail" to ("マウスで持っているジェムをケースにしまいます。" to "Store the gems held on your cursor."),
                "insert_detail_extra" to ("入りきらない分はマウスで持ったまま残ります。" to "Any remainder stays on your cursor."),
                "move_detail" to ("リュックの中身からジェムをケースに移します。" to "Move gems from your backpack into the case."),
                "move_detail_extra" to ("対象外のアイテムや入らない分は元に残ります。" to "Other items and excess gems stay in place."),
                "processing_supplement_detail" to
                    ("強化素材が足りない時、リュックの中身も使います。" to "Use backpack contents for missing materials."),
                "processing_supplement_detail_extra" to
                    ("クリックでON/OFF。専用素材を先に使います。" to "Click to toggle. Dedicated materials are used first."),
                "unresolved_detail" to
                    ("今のMOD構成では読み込めないジェムを確認します。" to "Inspect stored gems unavailable in this setup."),
                "unresolved_detail_extra" to
                    ("種類・ランクの記録と個数は保持されています。" to "Type, purity and counts are retained."),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
    }

    private fun addTransferTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "on" to ("ON" to "ON"),
                "off" to ("OFF" to "OFF"),
                "transfer_target" to ("転送対象" to "Transfer"),
                "filter_mode" to ("条件" to "Match"),
                "filter_axis" to ("選択軸" to "Select"),
                "filter_all" to ("すべて" to "All gems"),
                "filter_include" to ("選択した条件を含む" to "Include selection"),
                "filter_exclude" to ("選択した条件を除く" to "Exclude selection"),
                "deposit_help" to ("ケースから接続先へ搬出（Depositが必要）" to "Case to external storage (requires Deposit)."),
                "restock_help" to ("接続先からケースへ補充（Restockが必要）" to "External storage to case (requires Restock)."),
                "filter_help" to ("指定・除外は種類と純度の両方で照合します。" to "Include/exclude matches both type and purity."),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
    }

    private fun addTransferSelectionTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "auto_transfer" to ("自動転送" to "Auto transfer"),
                "ranks" to ("ランク" to "Purities"),
                "transfer_mode_all" to ("すべてのジェム" to "All gems"),
                "transfer_mode_include" to ("選んだジェムだけ" to "Only selected gems"),
                "transfer_mode_exclude" to ("選んだジェムを除く" to "Except selected gems"),
                "selection_all" to ("すべてが対象（下の選択は適用されません）" to "All gems apply (selection below is ignored)"),
                "selection_deposit" to ("搬出するジェムを選ぶ" to "Select gems to deposit"),
                "selection_restock" to ("補充するジェムを選ぶ" to "Select gems to restock"),
                "selection_no_deposit" to ("搬出しないジェムを選ぶ" to "Select gems to keep in the case"),
                "selection_no_restock" to ("補充しないジェムを選ぶ" to "Select gems to skip when restocking"),
                "selection_summary" to ("選択：種類 %s ／ ランク %s" to "Selected: %s types / %s purities"),
                "selection_no_results" to ("検索に一致する項目がありません" to "No matching entries"),
                "selection_selected" to ("✓ 選択済み" to "✓ Selected"),
                "selection_unselected" to ("未選択" to "Not selected"),
                "selection_click" to ("クリックで選択・解除できます。" to "Click to select or deselect."),
                "selection_inactive" to
                    ("条件を「選んだジェムだけ」か「除く」にすると選べます。" to "Choose Only selected or Except selected to edit."),
                "selection_axes_detail" to
                    ("種類とランクの両方を選ぶと、その組合せが条件です。" to "If both axes are selected, their combination matches."),
                "selection_empty_detail" to
                    ("片方が未選択なら、その種類・ランクは制限しません。" to "An empty axis does not restrict type or purity."),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
    }

    private fun addTransferHelpTranslations(japanese: Boolean) {
        val labels =
            mapOf(
                "transfer_back_title" to ("収納画面に戻る" to "Return to gems"),
                "transfer_back_detail" to ("ジェムの一覧画面へ戻ります。" to "Return to the gem browser."),
                "transfer_back_extra" to ("転送設定は保持されます。" to "Transfer settings are retained."),
                "transfer_deposit_title" to ("搬出：ケース → 接続先" to "Deposit: case → external storage"),
                "transfer_deposit_detail" to
                    ("ケースのジェムを接続先へ送る条件を設定します。" to "Choose which case gems go to external storage."),
                "transfer_deposit_extra" to
                    ("実際の転送にはDepositアップグレードが必要です。" to "The Deposit upgrade performs the transfer."),
                "transfer_restock_title" to ("補充：接続先 → ケース" to "Restock: external storage → case"),
                "transfer_restock_detail" to
                    ("接続先からケースへ集めるジェムを設定します。" to "Choose which gems to collect into the case."),
                "transfer_restock_extra" to
                    ("実際の転送にはRestockアップグレードが必要です。" to "The Restock upgrade performs the transfer."),
                "transfer_enabled_title" to ("この方向の自動転送" to "Automatic transfer for this direction"),
                "transfer_enabled_detail" to
                    ("ONにすると、この方向の転送にケースも参加します。" to "ON lets the case participate in this direction."),
                "transfer_enabled_extra" to ("搬出と補充は別々に設定できます。" to "Deposit and Restock have separate settings."),
                "transfer_mode_title" to ("どのジェムを転送するか" to "Which gems to transfer"),
                "transfer_mode_detail" to
                    ("クリックで「すべて」「選んだものだけ」「除く」を切替。" to "Cycle All, Only selected, and Except selected."),
                "transfer_mode_extra" to
                    ("両方未選択：指定は対象なし、除外はすべてが対象です。" to "With no selections: Only matches none, Except matches all."),
                "transfer_types_title" to ("種類を選ぶ" to "Choose gem types"),
                "transfer_types_detail" to ("下のアイコンをクリックして種類を選びます。" to "Click the icons below to choose gem types."),
                "transfer_types_extra" to
                    ("金枠とチェックが選択済み。所持数とは関係ありません。" to "Gold frames mark selection, regardless of owned count."),
                "transfer_ranks_title" to ("ランクを選ぶ" to "Choose purities"),
                "transfer_ranks_detail" to
                    ("下のアイコンは低いランクから順に並びます。" to "Icons below run from lowest to highest purity."),
                "transfer_ranks_extra" to
                    ("見本のジェムの種類に関係なく、すべての種類に適用します。" to "Purity applies to every gem type, regardless of the sample."),
                "transfer_search_title" to ("選択候補を検索" to "Search available choices"),
                "transfer_search_detail" to ("名前またはIDで、下の選択候補を絞り込みます。" to "Filter the choices below by name or ID."),
                "transfer_search_extra" to ("検索で隠れた項目の選択は保持されます。" to "Hidden selections remain selected."),
                "transfer_pages_title" to ("候補の続きを表示" to "Browse more choices"),
                "transfer_pages_detail" to
                    ("矢印またはマウスホイールで一覧を移動します。" to "Use arrows or the mouse wheel to browse choices."),
                "transfer_pages_extra" to ("ページを移動しても選択は保持されます。" to "Selections are retained across pages."),
            )
        labels.forEach { (key, names) ->
            add("sophisticated_gem_case.gui.$key", if (japanese) names.first else names.second)
        }
    }

    private fun addGuiTranslations(japanese: Boolean) {
        addGemHelpTranslations(japanese) { key, value -> add(key, value) }
        addToolTranslations(japanese)
        addTransferTranslations(japanese)
        addTransferHelpTranslations(japanese)
        addTransferSelectionTranslations(japanese)
        val labels =
            mapOf(
                "title" to ("Gem Case" to "Gem Case"),
                "storage" to ("収納" to "Storage"),
                "settings" to ("転送設定" to "Transfer settings"),
                "processing" to ("加工" to "Processing"),
                "search" to ("検索: " to "Search: "),
                "insert" to ("ジェムをしまう" to "Store held gems"),
                "move" to ("リュックのジェムを移動" to "Move backpack gems"),
                "loading" to ("一覧を同期しています" to "Synchronizing list"),
                "locked" to ("保存を保持して操作停止" to "Storage locked; data retained"),
                "help" to ("左:1 右:stack Shift:取出" to "Left:1 Right:stack Shift:inventory"),
                "deposit" to ("搬出" to "Deposit"),
                "deposit_selected" to ("▶ 搬出" to "▶ Deposit"),
                "restock" to ("補充" to "Restock"),
                "restock_selected" to ("▶ 補充" to "▶ Restock"),
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
                "processing_supplement_on" to ("リュックの素材も使う: ON" to "Backpack materials: ON"),
                "processing_supplement_off" to ("リュックの素材も使う: OFF" to "Backpack materials: OFF"),
                "processing_run" to ("加工 (Shift: 最大)" to "Process (Shift: all)"),
                "processing_preview_loading" to ("加工内容を確認しています" to "Checking processing preview"),
                "processing_reason_ready" to ("加工できます" to "Ready to process"),
                "processing_reason_not_advanced" to ("上位純度の加工ではありません" to "Not an advanced purity"),
                "processing_reason_unresolved_gem" to ("ジェムの種類を読み込めません" to "Gem type is unresolved"),
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
            if (japanese) "リュック専用です" else "This upgrade requires a backpack",
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
