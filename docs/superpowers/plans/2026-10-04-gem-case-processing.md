# 上位Gem Case・材料枠・加工・装着中上位化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 上位Gem Caseへ6材料枠と実行時recipeによる純度強化を追加し、中身入り下位を保存データごと装着中に上位化する。

**Architecture:** 下位と上位で既存wrapperと連携を共有する。coreは二材料要求の数量配分とGem 2→1の不変計画、loaderはrecipe解決・保存・同バッグinventory・server確定を担当する。加工と変換には既存exclusive文脈を使い、個数／転送設定のschema 1を維持する。

**Tech Stack:** MC 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0／FML 4.0.44／Apotheosis 8.9.0／Core 1.5.2.2343／Backpacks 3.26.6.2174。

**Spec:** [2026-10-04承認の上位加工設計](../specs/2026-10-04-gem-case-processing-design.md)。工程3統合後mainは `85608fc`、spec commitは `57a504d`。工程4は実装・自動検証済み。GUI実操作と認証接続などの未検収は検証記録へ分ける。

## Global Constraints

- item IDは `advanced_gem_case_upgrade` と `gem_case_upgrade_conversion`。上下合わせて一バッグ一個、Gem容量32,767／種類×純度、Stack／バッグtier倍率を受けない。
- 材料6枠は各1..min(64, item.maxStackSize)。public IO・Pickup・Deposit・Restockから材料を搬送しない。専用6枠だけの成立を先に選ぶ。
- 通常収納補充は初期OFF。有効時だけ同じバッグの通常slotから不足を補い、プレイヤー・別バッグ・外部handlerを使わない。
- `gem_processing_materials` schema 1は `schema_version: Int` と `entries: [{slot: Int, stack: Compound}]`。NBT 1MiB／深さ16／6entry／各stack永続encode 32KiB。未知field・slot重複・未知item・過大／破損はraw保持で材料変更と加工停止。
- `gem_processing_settings` schema 1は `schema_version: Int` と `use_backpack_materials: byte(0/1)`。不在はOFF、破損はraw保持で設定変更と加工停止。転送設定だけのLockedは加工を止めない。
- 入力は同種類の直前純度2個、出力はCHIPPED..PERFECTの1個。CRACKED出力／PERFECTからの強化なし。出力満杯・入力不足・材料不足では全状態無変更。
- 1計画最大512recipe照会／4,096左右候補組合せ／16,384 Ingredient判定／先頭8,192通常slot。上限到達は無変更。通常クリック1回、Shift最大64回、各回再計画して成功分だけ保持。
- preview最大32KiB。raw材料は通常ItemStack wireへ送らず要約だけ、6材料詳細はnative menu slot同期で送る。client専用型はclient packageだけに置く。
- 変換は装着下位をcursorの変換itemで右クリックし、全componentを継承する。旧下位に上位専用componentが既に付いていれば拒否。通常remove/swap制限を緩めない。
- coreはpure Kotlin/JVM、公開APIのpublic／型を明示し、所有する集合をコピーする。val既定、通常コードの!!なし、理由と契約は日本語。
- 固定版を変更しない。通常入手recipe・最終texture・pack導入は工程5。生成resourceはdatagen入力だけから作る。
- 統括者が共有wrapperとGradleの所有者を一人に限定する。独立作業だけ安価agentへ委譲できる。全体を実装担当とは別の履歴なしの安価reviewerへ渡す。

## Review Focus

1. 両Ingredientに適合する専用材料をleftが先取りする場合：rightに必要数を残し、成立する補充計画を見落とさない（Task 1／3）。
2. 加工commitの途中に同バッグcallbackが再入／例外：重複加工・取り外しを拒否し、予約した保存とslotを復元、次操作へ文脈を残さない（Task 3）。
3. recipe／タグreloadで表示材料コストだけが変わる場合：counts不変でも世代を更新し、旧previewからの加工を拒否する（Task 3／5）。
4. 中身入りまたはLocked rawの下位変換：原文と外来componentを継承し、検証失敗ならcursorも旧upgradeも消費しない（Task 4）。
5. 上位6slotのShift／数字キー／ドラッグやview切替：通常Gem投入と競合せず、未同期／破損材料を変更せず、隠したslotが別inventoryと重ならない（Task 2／5）。

---

## ファイルと実行単位

以下の相対パスを展開する：`C = core/src/main/kotlin/org/mocoserver/sophisticatedgemcase/core/`、`CT = core/src/test/kotlin/org/mocoserver/sophisticatedgemcase/core/`、`K = neoforge/src/main/kotlin/org/mocoserver/sophisticatedgemcase/`、`G = neoforge/src/gametest/kotlin/org/mocoserver/sophisticatedgemcase/tests/`。既存MixinのJava pathは `neoforge/src/main/java/org/mocoserver/sophisticatedgemcase/mixin/`。

Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6を基本とする。Task 4の変換はTask 2の保存境界だけへ依存するが、shared wrapperを同時編集させない。各単位で統括者がdiffと必要な実測を確認してコミットし、最後に独立した全体レビューを行う。

## Task 1: pure材料配分とGem強化計画

**Files:** Create C`GemMaterialAllocation.kt`／C`GemPurityPlan.kt`、CT`GemMaterialAllocationTest.kt`／CT`GemPurityPlanTest.kt`。

**Interfaces:** public `GemMaterialCandidate(slot: Int, count: Int, matchesLeft: Boolean, matchesRight: Boolean)`。public `GemMaterialAllocator.allocate(candidates: List<GemMaterialCandidate>, leftRequired: Int, rightRequired: Int): GemMaterialAllocation?`。allocationはコピーして所有する読み取り専用 `leftUses: Map<Int, Int>`／`rightUses: Map<Int, Int>` と、合算した `uses: Map<Int, Int>` を返す。候補順が優先順、slotは専用0..5／通常6+native index、重複slot・負数は契約違反、合計はLongで扱う。

public `GemPurityPlanner.plan(counts: GemCounts, input: GemKey, output: GemKey, allocation: GemMaterialAllocation): GemPurityPlan?`。planは `counts: GemCounts`／`materials: GemMaterialAllocation` を公開する。既知6純度の隣接と同種類を確認し、入力2・出力1の容量条件を満たす新しいcountsだけ返す。

- [x] `protectsRightAndUsesNormalLeftOnlyMaterial` を先に追加する。slot0はcount1で左右適合、slot6はcount1でleftだけ、要求各1ならusesは `{0:1,6:1}`。不足ならnull、専用優先、共有slotの合計は元count以下、Int.MAX_VALUE合計でoverflowなし、入力list／返却mapの所有権もassertする。

```kotlin
val allocation = requireNotNull(GemMaterialAllocator.allocate(
    listOf(GemMaterialCandidate(0, 1, true, true), GemMaterialCandidate(6, 1, true, false)),
    leftRequired = 1,
    rightRequired = 1,
))
assertEquals(mapOf(0 to 1, 6 to 1), allocation.uses)
assertEquals(mapOf(6 to 1), allocation.leftUses)
assertEquals(mapOf(0 to 1), allocation.rightUses)
```

- [x] `:core:test` を実行し、新API未定義のREDを記録する。
- [x] allocatorを実装する。right適合の総残数を計算し、共有候補をleftへ割り当てる量をright必要数を残せる範囲へ制限してleftを順に配分する。その後rightを残数から順に配分し、両方成立した場合だけ不変usesを返す。
- [x] `plansTwoInputsAndOneOutputWithoutChangingOriginal` を追加する。input3→1、output0→1、input1／output32,767→null、同一キー／非隣接／未知純度→nullを検査し、plannerを実装する。
- [x] `:core:test :core:check` が成功しcore境界を通った後、この単位をコミットする。

## Task 2: 上位登録・共有wrapper・6材料枠の保存

**Files:** Create K`storage/GemMaterialContents.kt`／K`storage/GemMaterialCodec.kt`／K`storage/GemMaterialState.kt`／K`storage/GemMaterialNbtBudget.kt`／K`storage/GemProcessingSettingsCodec.kt`／K`storage/GemProcessingSettingsState.kt`／K`storage/GemMaterialHandler.kt`、K`processing/GemProcessingCommit.kt`、G`GemMaterialStorageGameTests.kt`。Modify K`ModItems.kt`／K`ModDataComponents.kt`／K`ModTemplate.kt`／K`upgrade/GemCaseUpgradeItem.kt`／K`upgrade/GemCaseUpgradeWrapper.kt`／K`data/TemplateDataGenerators.kt`。

**Interfaces:** `GemMaterialContents.of(stacks: List<ItemStack>): GemMaterialContents` は6stackをcopyして所有し、`stackAt(slot: Int): ItemStack` はcopyを返す。equalsは各ItemStack.matchesで比較する。`GemMaterialState.Ready(contents)`／`Locked(reason)`／`ClientSummary(hasContents, locked)`、`GemMaterialCodec.read(data: CustomData?, registries: HolderLookup.Provider): GemMaterialState`／`write(contents: GemMaterialContents, registries: HolderLookup.Provider): CustomData`／`canWrite(contents: GemMaterialContents, registries: HolderLookup.Provider): Boolean`。

`GemProcessingSettingsState.Ready(useBackpackMaterials: Boolean)`／`Locked(reason)`／`ClientSummary(useBackpackMaterials, locked)`、`GemProcessingSettingsCodec.read(data: CustomData?): GemProcessingSettingsState`／`write(useBackpackMaterials: Boolean): CustomData`。

同じ `GemCaseUpgradeItem(supportsProcessing: Boolean = false)` と既存TYPE／wrapperを両itemに使う。wrapperは `supportsProcessing: Boolean`、`materialsSnapshot(registries: HolderLookup.Provider): GemMaterialState`／`processingSettingsSnapshot(): GemProcessingSettingsState` を追加する。`GemProcessingCommit(expectedCounts: GemCounts, expectedMaterials: GemMaterialContents, expectedSettings: Boolean, updatedCounts: GemCounts, updatedMaterials: GemMaterialContents)` を受ける `commitProcessing(change: GemProcessingCommit, registries: HolderLookup.Provider, permit: GemTransferPermit): Boolean` は現在の装着identity・server・expected・codecを検証し、countsとmaterialsを一回のsave callbackで確定する。手動材料変更は独立した `replaceMaterials(expected: GemMaterialContents, updated: GemMaterialContents, registries: HolderLookup.Provider, permit: GemTransferPermit? = null): Boolean` を使い、同じserver／current／expected／canWrite／mayMutate検証を要求する。正常材料の手動入出力はcounts／補充設定のLockedに依存させない。

`replaceProcessingSettings(expected: Boolean, updated: Boolean): Boolean` は通常のmayMutate(null)を要求する。`GemMaterialHandler(storage: IStorageWrapper, level: ServerLevel, isMaterial: (ItemStack) -> Boolean): IItemHandlerModifiable` はserver専用6slot、材料の一部受入／余り／simulate無変更を扱い、保存codec予算を超える入力を拒む。levelからregistry lookupを得る。predicateを渡して新規材料を検査する。Task 2の保存テストは固定predicateを使い、Task 3以後の本番は `GemPurityRecipes.isMaterial(level, stack)` を渡す。このtaskでhandlerを材料編集UIへ登録しない。

- [x] GameTestsを先に追加し、6枠の永続往復・名前/component保持・各count限度・copy所有権、不在→空／OFF、slot重複／範囲外／不正型／未知schema／未知item／深さ17／32KiB stack超過／1MiB超過→同raw Lockedをassertする。`:neoforge:runGameTestServer` で新API未定義のREDを確認する。
- [x] 材料と補充componentをCustomData永続codec＋boolean-only network codecで登録する。材料summary2byte（中身あり／locked）、補充summary2byte（ON／locked）を実測する。client_summaryはserverの更新sourceにしない。材料budgetを独立ファイルへ置き、既存GemNbtBudget深さ8を緩めない。
- [x] 上位itemと変換用通常Itemを登録し、共有 `UpgradeGroup("gem_case", "sophisticated_gem_case.upgrade_group.gem_case")` を一つ所有して両itemへ返す。extraInsertConditionsの現物wrapper重複拒否も維持する。上位container登録、creative入手、仮item modelとja_jp／en_usをdatagen入力へ追加する。
- [x] wrapperの保存と取り外し条件を実装する。材料／補充Locked時も正常なcountsの手動操作・既存連携は維持し、上位は中身あり／材料Lockedならremove/swap拒否、空＋設定だけなら取り外し成功。上位＋下位の同時装着、tier／Stack倍率不変をGameTestで検査する。
- [x] `commitProcessing` のcallback失敗では旧counts/material componentを戻し、復元失敗をsuppressed付きで再送出する。上位専用componentを下位の通常操作で書かない。GameTestでexpected不一致／Summary source／client側／exclusive外tokenの全無変更と、補充設定だけのLockedでも正常材料の手動取出し成功を確認する。
- [x] `:neoforge:classes :neoforge:runData`、別呼出しの `check :neoforge:runGameTestServer` と結果parserが成功後コミットする。tracked／untracked生成差分を確認する。

## Task 3: 実行時recipe選択・加工計画・server確定

**Files:** Create K`processing/GemPurityRecipes.kt`／K`processing/GemProcessingPlanner.kt`／K`processing/GemProcessingPlan.kt`／K`processing/GemProcessingEngine.kt`／K`processing/GemProcessingPreview.kt`／K`processing/GemRecipeEpoch.kt`、G`GemProcessingGameTests.kt`／G`GemProcessingFailureGameTests.kt`。Modify K`ModTemplate.kt`／K`storage/GemMaterialHandler.kt`。

**Interfaces:** `GemPurityRecipes.isMaterial(level: Level, stack: ItemStack): Boolean`。`GemProcessingPlanner.preview(storage: IStorageWrapper, level: ServerLevel, output: GemKey): GemProcessingPreview` と `plan(storage: IStorageWrapper, level: ServerLevel, output: GemKey): GemProcessingPlan?`。planは現在wrapper、core GemPurityPlan、recipe IDと左右候補ordinal／必要count、変更前materials/settings、使用する通常slotの変更前ItemStack copyと変更後copyをloader内に保持する。

`GemProcessingEngine.process(storage: IStorageWrapper, level: ServerLevel, output: GemKey, limit: Int): Int` は成功回数を返す（limitは1／64だけ）。`GemProcessingPreview` は `output: GemKey`、`hasMaterials/materialsLocked/useBackpackMaterials/settingsLocked: Boolean`、`reason: GemProcessingReason`、`recipeId: ResourceLocation?`、実際の材料配分をitem ID・個数・左右別にまとめた `GemMaterialCost(itemId: ResourceLocation, count: Int, side: GemMaterialSide)` を持つ。`costs: List<GemMaterialCost>` をコピーして所有する。GemMaterialSideはLEFT／RIGHT。GemProcessingReasonはREADY／NOT_ADVANCED／UNRESOLVED_GEM／LOCKED_COUNTS／LOCKED_MATERIALS／LOCKED_SETTINGS／INPUT_SHORTAGE／OUTPUT_FULL／MATERIAL_SHORTAGE／BUDGET_EXCEEDED／PREVIEW_OVERSIZEとし、任意文字列をGUI理由に使わない。材料のraw NBTや全tag候補はpreviewへ載せず、材料枠の詳細は通常slot同期で表示する。

`GemRecipeEpoch.current(): Long`／`advance(): Long`／`install(event: AddReloadListenerEvent): Unit`。NeoForge.EVENT_BUSの固定版AddReloadListenerEventへ登録したResourceManagerReloadListenerがapply時にAtomicLong epochを進める。recipe表を恒久cacheせず毎計画にGemCuttingMenu.getRecipes(level)を使う。

- [x] 実バッグ・上位・作者実recipeを使うGameTestを先に追加する。Gem2→1・左右消費・入力不足／出力満杯／材料不足の無変更、通常材料OFF／ON、プレイヤーと別バッグ不使用をassertし `:neoforge:runGameTestServer` でREDを記録する。
- [x] 専用6枠だけの選択は固定GemUpgradeMatchのslot走査とGemCuttingRecipe.getMatchの順を再現する。両材料は別slot、最初の成立recipeを使う。成立しない場合だけ補充ONの左右候補組合せを試し、Ingredient判定結果とslot残数をTask 1へ渡す。GemMaterialHandlerのpredicateへ現在recipeの左右Ingredient判定を接続する。
- [x] 計画budgetを一つの所有値にまとめ、512／4,096／16,384／8,192で停止する。Ingredient候補を順序そのまま扱い、固定のgem_dust等の材料表は作らない。Gem input／outputをStandardGemで解決して構成可能性を検査する。
- [x] 同バッグexclusiveで計画、選択した全slot／counts／settings／epochの再照合、全codec事前検証、通常slot変更とwrapper.commitProcessingを行う。通常inventoryはstorage.inventoryHandlerの実slotだけを使い、processing／IO wrapperや仮想Gem slotを通さない。例外時に自身が変更した通常slotとcomponentsをsnapshotへ復元し、成功通知前のpartial変更を残さない。
- [x] 複数候補／tag／左右共有stackと正常Gem＋material総量、専用優先と不足分だけのnormal消費、同native6slotの場合の作者選択一致をテストする。recipe変更テストは同期メソッド内でRecipeManager.replaceRecipesを使い、旧一覧をfinallyで必ず復元してepochをadvanceする。他GameTestへglobal変更を残さず、runAfterDelay中に変更を保持しない。
- [x] callback再入の設定／Gem／材料編集・remove・変換を拒否するテストを追加する。別バッグは許可、通常slot変更後またはsave callbackの例外で旧snapshotへ復元、例外後の次操作成功、64成功後の停止／途中不足時の成功分だけ保持をassertする。32KiB preview超過と最大slot／候補budgetでは無変更を確認する。
- [x] `check :neoforge:runGameTestServer` と結果parserが成功後コミットする。reload listenerのapply APIとRecipeManager.replaceRecipesの対象版資料を検証記録へ残す。

## Task 4: 中身入り下位の装着中変換

**Files:** Create K`upgrade/GemCaseConversionItem.kt`／K`integration/GemCaseConversion.kt`、G`GemConversionGameTests.kt`。Modify K`ModItems.kt`／K`integration/GemCaseBridge.kt`。Javaの既存StorageContainerMenuMixinはhandleClick経由で足りるため新Mixinを増やさない。

**Interfaces:** `GemCaseConversion.tryHandle(menu: StorageContainerMenuBase<*>, slotId: Int, button: Int, clickType: ClickType, player: Player): Boolean` は「専用変換cursorでupgrade slot右クリック」の対象を消費する（拒否時もtrue）。他操作はfalse。slotIdをmenu.totalSlotsNumberで範囲確認し、menu.getSlot(slotId)が公開StorageUpgradeSlotであることとupgradeSlotsへの所属を確認する。slot.slotIndexがupgradeHandlerのindexであり、global slotIdをそのままhandlerへ渡さない。clientは保存・cursorを変更せず消費してnative packetをserverへ渡し、serverはplayer.containerMenu === menu／stillValid／現在バッグUUID／slot／下位現物／exclusive／cursorを検証する。

- [x] native menu.clickedの実slot経由で、中身入り下位＋設定＋custom name／外来componentが上位へ同じ内容で継承され、creative以外のcursorが一個だけ減るテストを先に追加する。未接続でREDを確認する。
- [x] 現在下位をcopyして上位ItemStackへ全componentを載せ、上位専用componentが旧下位にあれば拒否する。Gem保存はLocked原文を含め書換えず、上位の新材料は不在→空、補充は不在→OFFとする。
- [x] 上位canAdd／extraInsertConditionsを「対象slotの旧下位だけを置換する」条件で確認し、他slotの重複を拒否する。旧下位canRemove／canSwapを緩めず、一般cursor入替で中身入りを持ち出せないことをassertする。専用変換だけupgradeHandlerの現在slotを直接置換する。
- [x] exclusive内でcopy再読取・装着条件・counts/material codecの状態保持を確認後、upgradeHandler.setStackInSlotで保存／wrapper更新を通し、slotと上位component保持を再照合後にcursorを減らす。失敗・callback例外では旧slotとcursorを復元し、復元失敗を再送出する。処理した右クリックを既存GemCaseBridgeの先頭で判定し、他の投入／Shift経路を維持する。
- [x] future counts／settings raw、上位専用raw付き下位の拒否、無効cursor／左クリック／閉じた旧menu／距離／UUID・slot差替え／exclusive再入、target装着拒否とsave例外の全不変を検査する。成功後に新session、上下合計一個、上位Pickup／Deposit／Restockのfilterと実移動が維持されることもテストする。
- [x] `check :neoforge:runGameTestServer` と結果parserが成功後コミットする。native Stack conversionの既定target生成を流用したとは記録しない。

## Task 5: 材料slot・世代付き加工要求・preview・画面

**Files:** Create K`menu/GemMaterialSlot.kt`／K`menu/GemProcessingActions.kt`、K`network/GemProcessingMessages.kt`／K`network/GemProcessingPreviewPayload.kt`、K`client/GemProcessingCache.kt`／K`client/GemProcessingPanel.kt`／K`client/GemCaseViewState.kt`、G`GemProcessingMenuGameTests.kt`／G`GemProcessingWireGameTests.kt`。Modify K`menu/GemCaseContainer.kt`／K`menu/GemMenuSession.kt`、K`network/GemNetworking.kt`、K`client/GemCaseClient.kt`／K`client/GemCaseTab.kt`／K`client/GemSettingsPanel.kt`、K`data/TemplateDataGenerators.kt`。

**Interfaces:** `GemProcessingAction { REQUEST_PREVIEW, PROCESS_ONE, PROCESS_MANY, SET_BACKPACK_MATERIALS }`。`GemProcessingRequest(menuId: Int, upgradeSlot: Int, sessionId: UUID, revision: Long, action: GemProcessingAction, output: GemKey?, enabled: Boolean?)`。加工／previewはoutputだけ、設定変更はenabledだけを許す。GemIDは256 UTF-8 byte、出力純度は既知範囲、余計field・不正enum・下位要求を拒否する。

`GemProcessingActions.perform(player: ServerPlayer, request: GemProcessingRequest): Boolean` はGemSettingsActions同等の権限／世代／current upgradeを使う。quotaはネットワーク受信境界で既存session.allowRequestへ統合する。

`GemProcessingPreviewPayload(menuId: Int, upgradeSlot: Int, sessionId: UUID, revision: Long, recipeEpoch: Long, preview: GemProcessingPreview)` は32KiBまで、材料表示はGemMaterialCostのitem ID・個数・左右だけをboundedにencodeする。decode前にpayload全体32KiBとリスト件数を検査し、IDはResourceLocation codecで読む。完全な必要量を表現できないpreviewはoversize拒否reasonを返し、加工を許可しない。実消費planはこのpreview検証も通す。

sessionはcounts／transfer settings／materials／processing settings／recipeEpochを一組で保持する。`refresh(wrapper, registries: HolderLookup.Provider)` に既存呼出しを揃え、どれかが変わると同じrevisionを増やす。containerはowner.level().registryAccess()を渡す。clientは現在のoutput、countsとtransfer settingsの完全な同世代、非Lockedの加工previewが揃うまで加工を送らない。

- [x] requestの旧世代／replay／closed／distance／UUID／upgrade差替え／下位／Locked／quota超過、enumと256／257byte、32KiB wire境界の先行GameTestsを追加し、新APIのREDを確認する。
- [x] server containerへ上位だけ6つのGemMaterialSlotを作る。server側はownerのServerLevelとGemPurityRecipes.isMaterial predicateを渡したGemMaterialHandler、client側は永続保存しないItemStackHandlerのmirrorを使う。クリック／ShiftのmayPlace・mayPickup・simulate・最大stackと保存予算を確認し、複合操作を安全に処理できない場合は変更前に拒否する。壊れた材料でも正常なGemの手動操作を維持する。
- [x] menu snapshotとreload epochを統合し、typed requestをregisterする。処理後はnative material slot更新をbroadcastChangesで先に送った後、同じ接続の順序でcounts／settings／加工previewを送る。初回previewもnative6slot同期後だけ発行し、client callback登録はcommonからclient型を参照しない。
- [x] 上位限定の加工viewを別GemProcessingPanelへ置き、既存200×205領域で収納／転送設定／加工を切り替える。種類／出力純度選択、6slot、選択recipeの必要材料と可否、補充ON／OFF、単発／Shift加工を表示する。必要材料と可否はserver previewだけから描く。hidden material slotの位置／isVisible／slotIsNotCoveredAtを一貫させ、隣接する通常slotを覆わない。
- [x] native6slotのクリック／右クリック／Shiftと余り、取り出し、数字キー／drag拒否の全無変更、recipe変更後の同countsでもrevision増加・旧preview拒否・新コストの加工、view切替、counts/settings/previewの世代不一致・途中同期で送信不可を検査する。材料／補充だけの更新でもrevisionが進むことを確認する。
- [x] 翻訳をdatagen入力へ追加し、`:neoforge:classes :neoforge:runData` 後に追跡／未追跡を検査する。別呼出しの `check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer` とparserが成功後コミットする。

## Task 6: 配布・保存・独立レビュー・main統合

**Files:** Modify `neoforge/build.gradle.kts` のverifyModJar requiredEntries、`README.md`／`docs/design.md`／`docs/verification.md`／`docs/client-acceptance.md`、本計画の実行記録。

- [x] 通常JARへcore allocator／plan、material codec／handler、engine／conversion／request／client panel、model／langが含まれる必須entry検査を追加する。fixture・外部MOD・docs・skillsを含めない。
- [x] 最後の本番変更後にroot `check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer` と `node scripts/check-gametest-result.mjs` を実行し、既存73件と新規が正の件数で成功したことを記録する。新しい変更・失敗がなければ同じ広範囲検証を重複しない。
- [x] datagenを再実行し、生成resource全一覧／SHA256／tracked・untracked比較で差分0を確認する。通常JARのhashと固定依存manifestの全hashを確認する。
- [x] `.local/processing-server` で通常JAR＋固定runtime依存だけを新規worldへ導入し、上位itemのGem counts／transfer設定／6材料／補充設定を保存、停止、worldをコピーして再起動し保持を観測する。通常JARの `/reload` も成功を観測する。装着バッグの手動保存検収とは分ける。JEI限定dev clientを起動し、GUI／認証接続／pack／Windowsは実施分だけ検収とする。
- [x] 実装者と別の履歴なしの安価reviewerへ最終diff、承認spec／plan、固定source、実測ログを渡す。Review Focusの5項目・数量・変換のcomponent継承・通常slot復元・通信権限を確認し、Important以上を根拠確認して修正・影響する検証を再実行する。
- [x] 実測結果と未実施、bounded handler／recipe制限、外部callbackの契約違反と復元失敗の扱いを記録・コミットし、ユーザーの既存承認でローカルmainへ統合する。検証済みbranchとmainのtree、clean作業木を確認して工程5へ進む。push／pack変更は含めない。

## 固定API・実行条件・自己確認

作者API／順序は[spec一次資料](../specs/2026-10-04-gem-case-processing-design.md#固定一次資料)を使う。新しいGemUpgradeMatchのpublic公開を仮定しない。UpgradeGroup(String, String)とRecipeManager.replaceRecipes(Iterable<RecipeHolder<?>>)は対象JARへのjavapで確認した。AddReloadListenerEventはNeoForge.EVENT_BUSで発火しaddListener(PreparableReloadListener)を持つことを[NeoForge 21.1.252 sources](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.252/neoforge-21.1.252-sources.jar)で確認した。

Java 21は `/home/osamu/develop/moco-server/server-pack/.local/issue7-gradle/jdks/eclipse_adoptium-21-amd64-linux.2` を読取り利用する。JAVA_HOMEへ指定し、GRADLE_USER_HOME=`$PWD/.local/gradle`、JAVA_TOOL_OPTIONS=`-XX:ActiveProcessorCount=2`を使う。cache／JAR／world／logsは.local／buildに置く。

specの登録と共有収納はTask 2、材料・補充保存はTask 2、recipe／二要求配分／budget／加工確定はTask 1／3、raw継承変換はTask 4、材料slot／世代／reload／画面はTask 5、配布と独立レビューはTask 6へ対応する。plan／preview／commitの型は上記Interfacesで統一した。Review Focusの5入力はそれぞれ担当taskの先行テストまたは境界テストに割り当て、工程3の既存連携を上位でも再検査する。工程4本番・GameTests・生成resourceを実装し、必須140件とroot check／通常JAR内容検査、専用serverのreload・コピーworld保存往復、JEI入りclient起動を確認した。GUI実操作・認証接続・pack／Windowsは未検収で、チェックは実施可能な自動検証と未実施記録の完了を表す。

## 実行記録

Task 1は9845ece、Task 2はc92b16d。Task 3〜5はmenu/session/wrapperとnative slotの結合依存が強いため、結合検証・独立レビュー後に一つの整合した実装として54b75f7へコミットし、ローカルmainへfast-forward統合した。GradleとGitは統括者が専有し、部分コードがbuild不能な状態の中間コミットは作らない。Task 6の結果は[検証記録](../../verification.md)へ集約した。最終実装ログは`.local/verification/processing/final-acceptance-3.log`、独立レビューは同dirのfinal-independent-review.md。通常JAR SHA256は468a4996f9887a4da8297097c43cddd34a0ee4a28d7af8d3c6e0731553960920。

レビューでrecipe予算の再走査、保存後epoch変更時のraw未存在復元、native upgradeタグ不足、まとめ回収による材料slot迂回、全通常移動先の復元、上位にも共有される一括移動の例外と再入を検査・修正した。既存の専用Deposit／RestockはGem側フィルタだけに従うため、nativeフィルタとの二重一致を期待したテストを承認仕様に合わせた。view開閉metadataのquota例外とpanelがnative材料clickを譲る処理を加えた。
