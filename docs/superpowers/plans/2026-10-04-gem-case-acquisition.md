# Gem Case入手経路・見た目・検収 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 下位と変換itemを通常craftで入手できるようにし、三itemの見た目と保存境界を整え、実施できた検収と未検収を記録する。

**Architecture:** 既存wrapperとcodecの小修正、既存datagenへ接続するrecipe provider、item/generatedのtexture追加で実現する。加工と装着中変換は工程4の実装を使う。coreと保存schemaを変えず、packへの導入はこのrepoの検収後の別変更とする。

**Tech Stack:** MC 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0／Apotheosis 8.9.0／Core 1.5.2.2343／Backpacks 3.26.6.2174。開発限定JEI 19.51.0.418。

**Spec:** [工程5の承認済み設計](../specs/2026-10-04-gem-case-acquisition-design.md)。ユーザーの2026-10-04「対応願う」を設計承認として計画化する。開始branchはfeat/gem-case-acquisition-design、mainはfcf3039。本計画は「ok」で承認され実装・自動検証済み。実player／認証／packの未検収はdocs/client-acceptance.mdとdocs/pack-handoff.mdへ引き継ぐ。

## Global Constraints

- 下位 `gem_case_upgrade`、上位 `advanced_gem_case_upgrade`、変換 `gem_case_upgrade_conversion` と日英名称を維持する。上下合わせて一バッグ一個、32,767／種類×純度、6材料slot、補充初期OFFを維持する。
- 下位は `GGG / DBD / DCD`、glass_blocks 3＋gem_dust 4＋upgrade_base 1＋chest 1。変換は `GGG / BEB / BNU`、glass_blocks 3＋basalt 3＋gem_cutting_table 1＋ender_chest 1＋upgrade_base 1。各出力一個。
- 二recipeだけを追加する。上位の直接craft、下位→上位の通常craft、作者Gem Case itemを消費するrecipeを作らない。既存sample_token recipeは保持する。
- schema・加工コスト・依存版・client分離・サーバースレッドと保存原文保持を維持する。通常JARへ外部MOD・テスト・資料を入れない。
- textureは三種の16×16透過PNG、自作の共通ケース輪郭と上位の留め具／工具、変換の部品シルエットを使い、色だけで区別しない。
- 安価な実行と独立レビューを維持する。統括者が共有ファイル、Gradle、Gitと実測検収を持つ。独立ファイルだけ委任し、常時並列や実装モデルを規約へ固定しない。
- 公開／pushを追加せず、packを変更しない。元worldを上書きせず.localに新規／コピーを置く。GUI・認証接続・pack／Windowsの未実施を成功へ読み替えない。

## Review Focus

1. summaryのint／string／-1／2と欠落field：ClientSummaryやReadyへcoerceせずLocked、原文を保持（Task 1）。
2. transfer setting保存と復元callbackの両方がthrow：原文／未存在と他componentを保持し、元例外と回復失敗を伝える（Task 1）。
3. 異色glassと不足素材：tag代替glassで成立し、一slot欠落と過剰な別itemでは不成立、九素材から出力一個（Task 2）。
4. 通常recipe探索に中身入り下位または作者Gem Caseを渡す：新規上位craftを認めず、中身入り下位の強化は既存装着中変換だけ（Task 2／4）。
5. texture欠落・仮texture参照・GUI scale差：通常JARに三PNGと対応モデルを含め、実画面未確認なら未検収と記録（Task 3／4）。

---

## 実行環境と共通コマンド

Java 21は `/home/osamu/develop/moco-server/server-pack/.local/issue7-gradle/jdks/eclipse_adoptium-21-amd64-linux.2` を読取利用。Gradleは次の環境で統括者だけが実行する。

```bash
JAVA_HOME=/home/osamu/develop/moco-server/server-pack/.local/issue7-gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
GRADLE_USER_HOME="$PWD/.local/gradle" JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew <tasks>
```

ログは `.local/verification/acquisition/`。GameTest結果は `node scripts/check-gametest-result.mjs` で必須件数>0を検査する。既存140件を維持し、新規件数は実測で記録する。生成変更後は別Gradle呼出しでbuildする。

## Task 1: summary厳密検証と転送設定の保存失敗復元

**Files:** Modify `neoforge/src/main/kotlin/org/mocoserver/sophisticatedgemcase/storage/GemStorageCodec.kt`、`upgrade/GemCaseUpgradeWrapper.kt`（同main package下）。Create `neoforge/src/gametest/kotlin/org/mocoserver/sophisticatedgemcase/tests/GemAcquisitionStorageGameTests.kt`。

**Interfaces:** `GemStorageCodec.read(data: CustomData?): GemStorageState`、`GemCaseUpgradeWrapper.replaceSettings(expected: GemTransferSettings, updated: GemTransferSettings): Boolean` を維持する。既存 `saveProcessingData(component: DataComponentType<CustomData>, data: CustomData, verify: () -> Boolean = { true }): Unit` を使い、新しい公開APIを増やさない。

- [x] `rejectsMalformedCountSummariesWithoutReplacingRaw` を先行追加する。三fieldのbyte以外、各値-1／2、client_summary=0、欠落、追加fieldでは `read(raw) is Locked`、wrapper現物raw==元raw。全合法has_contents／locked組合せではClientSummaryと一致。通常schema 1の正常countsの既存回帰も維持する。
- [x] `restoresTransferSettingsRawWhenSaveFails` と `retainsPrimaryFailureWhenRecoverySaveFails` を先行追加する。下位／上位それぞれ、旧設定未存在／設定済みの二状態でsave失敗を注入し、gem_transfer_settings原文／未存在、counts、materials、processing設定が不変とassertする。回復も別の例外で失敗ならprimaryとsuppressedをassertする。同一例外instanceの再throwでは自己suppressionを要求しない。
- [x] `:neoforge:runGameTestServer` を実行し、意図した新規失敗を観測する。既存callback fixtureと同様、wrapperを固定現物として登録し、本番へtest hookを入れない。
- [x] summaryの三fieldを厳密byte型と0..1、client_summary=1で検証する。replaceSettingsの成功分岐を既存単component保存helperへ接続する。失敗を握り潰さず、許可条件・expected照合・codec予算は維持する。
- [x] `ktlintFormat check :neoforge:runGameTestServer` とparserを実行し、全必須テストの成功と数量の不変を確認する。独立担当が小diffをレビューし、重要指摘を修正してコミットする。

## Task 2: 通常入手二recipeとadvancement

**Files:** Create `neoforge/src/main/kotlin/org/mocoserver/sophisticatedgemcase/data/GemCaseRecipes.kt`、`neoforge/src/gametest/kotlin/org/mocoserver/sophisticatedgemcase/tests/GemAcquisitionRecipeGameTests.kt`。Modify `data/TemplateDataGenerators.kt`、`neoforge/build.gradle.kts`。Generated: `data/sophisticated_gem_case/recipe/{gem_case_upgrade,gem_case_upgrade_conversion}.json` と `advancement/recipes/misc/` の対応二ファイル。

**Interfaces:** `internal object GemCaseRecipes { fun build(output: RecipeOutput): Unit }` を `TemplateRecipes.buildRecipes(output)` から呼ぶ。private `item(id: String): Item` は `BuiltInRegistries.ITEM` でcanonical IDを解決し、未登録ならdatagenを失敗させる。依存MODの不確かなstatic field名を仮定しない。

- [x] `craftsLowerFromNineIngredients`、`craftsConversionFromNineIngredients` を先行追加する。`CraftingInput.of(3, 3, stacks)` と実RecipeManagerを使い、specの九slotでIDが各recipeと一致、assembleのitem／countが各一個、gem_storage／gem_processing_materials／gem_processing_settings／gem_transfer_settingsが未設定で空収納となることをassertする。Minecraft標準componentまで空であるとは要求しない。recipe未存在のREDを観測する。
- [x] `acceptsTaggedGlassButRejectsMissingOrWrongMaterials` を追加する。clear／異色glassで成立、各九位置を順に空またはDIRTへ変えると対象recipeに不一致。ingredient listは九entryで、Gem Dust四個などspecの数を満たす。
- [x] `doesNotCraftAdvancedFromStoredLowerOrNativeGemCase` を追加する。counts入り下位／作者Gem Caseをspec gridの素材へ置いて不成立、二新recipeのresultに上位直接出力がないことをassertする。既存sample_tokenと装着中変換の回帰は維持する。
- [x] `ShapedRecipeBuilder`、`RecipeCategory.MISC` で二recipeを実装する。Gには `TagKey<Item>(c:glass_blocks)`、他はspecのitem IDを使う。下位はhas_gem_dust、変換はhas_gem_cutting_tableをunlockedByへ置き、両方default recipe IDでsaveする。criterionには公開API `InventoryChangeTrigger.TriggerInstance.hasItems(ItemLike...)` を使う（固定1.21.1実クラスで確認済み）。別objectからprotectedな `RecipeProvider.has` を呼ばず、providerは既存の一個を維持する。
- [x] `:neoforge:classes :neoforge:runData` を実行し、追跡・未追跡双方を確認する。verifyModJar requiredEntriesに二recipe／二advancementを追加する。
- [x] 別呼出しの `ktlintFormat check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer` とparserを実行する。重要指摘を解消した独立レビュー後にコミットする。

## Task 3: 三itemの16pxアイコン

**Files:** Create `neoforge/src/main/resources/assets/sophisticated_gem_case/textures/item/{gem_case_upgrade,advanced_gem_case_upgrade,gem_case_upgrade_conversion}.png`。Modify `data/TemplateDataGenerators.kt` のTemplateModels、`neoforge/build.gradle.kts`、`docs/client-acceptance.md`。編集用の自作vector／pixel sourceを保存する場合は `art/item-icons/` に置き、通常JARから除外する。

**Interfaces:** TemplateModelsは同じitemモデルIDとminecraft:item/generatedを使い、layer0だけ `sophisticated_gem_case:item/<item ID>` へ置き換える。renderer・保存・通信のAPIは追加しない。

- [x] 共通ケース輪郭の下位、同輪郭＋加工工具／留め具の上位、独立した強化部品の変換を16×16透過で作る。複雑な画像生成を先に行わず、単純な自作vector／pixel assetを制作し、PNGへ書き出す。制作ツールは開発用で通常Gradle buildへ依存させない。
- [x] 原寸と整数倍の比較画像を.localへ用意して目視する。PNGの幅／高さ16、透過、三画像が同一でないこと、シルエットの差を確認する。低影響の見た目変更に実装を写した単体テストを追加しない。
- [x] 三モデルを自MODのtextureへ変更し、verifyModJarのrequiredEntriesに三PNGを追加する。datagenを実行し、生成modelを手編集しない。
- [x] 別呼出しの `check :neoforge:build :neoforge:verifyModJar` を実行する。通常JAR内のモデルlayer0とtexture存在を確認する。GUI scale 2／3／4、日本語／英語とJEI実表示はTask 4の手動検収へ渡す。
- [x] 独立担当が画像・resource・JAR検査結果を確認し、モデルの古い仮参照がないことを検収してコミットする。

## Task 4: 最終結合・実player検収・main統合

**Files:** Modify `docs/verification.md`、`docs/client-acceptance.md`、`docs/design.md`、`README.md`、本計画。Artifacts: `.local/verification/acquisition/`、`.local/acquisition-server`、`.local/client-trial`。

**Interfaces:** 通常JARと固定runtime依存9 JAR、開発限定JEIを使う。新しい本番APIは増やさない。pack側の変更は別計画へ渡す。

- [x] 最終本番変更後 `check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer` とparserを実行し、140既存＋新規の正の件数で全成功を確認する。datagen再実行と全生成一覧／hash一致、tracked／untrackedも記録する。成功済みの同じ本番treeを無理由に再検証しない。
- [x] 通常JARのSHA256と固定依存manifestの全hashを記録し、試遊環境へ同じJARを置く。新規worldと停止後コピーの再起動で上位保存四領域を照合し、reloadと停止exit 0を確認する。item entity probeと装着バッグの実player保存を区別する。
- [ ] JEI限定dev clientを起動し、可能な環境では二recipeの表示／実craft、日本語／英語、scale別のアイコンと六slot、通常click・Shift、収納・転送・加工・補充・変換を実操作する。起動だけで実操作チェックを埋めない。
- [ ] 認証済みclientの通常JAR server接続、装着バッグの再起動・設置回収・tier変更、権限・素材不足・出力満杯の無変更を検収する。環境や認証が不足する項目は手順と成果物を渡し、未検収を残す。pack公開準備完了とは扱わない。
- [x] 実装者とは別の履歴なし安価reviewerへ最終diff・spec／plan・固定作者資料・実測ログを渡す。Review Focusの五点と原文／数量保持を確認し、Important以上を解消して影響する検証を実行する。
- [ ] 実施済み／未実施を記録・コミットする。既存ユーザー承認に従ってローカルmainへ統合し、検収treeとの一致とcleanを確認する。文書だけの更新で本番が不変なら広範囲buildを繰り返さない。
- [x] 未検収の実player項目と通常JAR／hash／依存side／残件をpack側の次計画へ引き渡す。pack変更・push・公開はこの計画で行わない。

## 計画の自己確認と実行方法

承認specの既存Minor改善はTask 1、入手経路と作者recipe整合はTask 2、名称・textureはTask 3、実player検収／保存／配布／packへの引き渡しはTask 4へ対応する。Review Focusの五点はそれぞれ先行GameTestまたはJAR／目視検収へ割り当てた。未実施を合格にしない条件を実行工程にも保持した。

ユーザーが指定した「安価な形で実装、実装担当と独立したサブエージェントレビュー」を維持する。共有wrapper／providerを並行編集させず、統括者自身の小修正か安価な担当への委任を作業量で選ぶ。最終レビューは別履歴で行い、モデル選択を再質問しない。本計画の実装を行い、保存は579252d、recipe／iconは80763a8へコミットした。独立レビュー・147 GameTest・check／JAR・生成再現性は成功。Task 4のdev client起動は実施済み、実操作と認証項目は未検収のまま引き渡す。共有providerとJAR契約のためTask 2／3は一コミットとし、承認済みfeature branchで作業した。詳細と実測はdocs/verification.md。
