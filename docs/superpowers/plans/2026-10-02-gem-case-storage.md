# 下位Gem Case収納 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:executing-plans`でtask順に実装する。統括者が実装し、最終差分を独立した安価なサブエージェントがレビューする。

**Goal:** 保存を失わない下位アップグレードを装着し、Gemの一覧、手動投入／取出、一括移動、公開capabilityからの投入を利用できるようにする。

**Architecture:** pure Kotlinの個数計算をcore、標準Gem判定と保存変換・確定処理をNeoForgeへ置く。公開upgrade APIを使い、外部公開handlerと手動menu操作だけを限定Mixinで接続する。永続データとclient表示を分け、通信要求は開いているserver menuで検証する。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.252、Java 21、Kotlin 2.0.21、既存の固定catalog、Apotheosis 8.9.0／Backpacks 3.26.6.2174／Core 1.5.2.2343。

**Spec:** [承認済み設計](../specs/2026-10-02-gem-case-storage-design.md)。開始commitは`24b0c42`。実装方法はこれまでの合意を継続する。

## 共通制約

- 種類×純度ごとに32,767個。schema 1は4,096 entry、ID／純度はUTF-8各256 byte、payload 1 MiB、深さ8まで。0個のentryを保存しない。
- 独自componentは`sophisticated_gem_case:gem_storage`、itemは`sophisticated_gem_case:gem_case_upgrade`。標準Gemだけを専用収納へ受け入れ、追加component・無効Gemは通常収納の既存経路へ渡す。
- 未解決ID／純度は保持。破損・将来schema・上限超過は原文を保持してロックする。失敗を空収納へ変換しない。
- 中身あり、または読めないpayloadの取り外し／交換は禁止。バッグに専用upgradeは一個まで。
- serverで検証し確定する。simulateと拒否は無変更。通常収納へ入らない残量を元へ返す。coreにはゲーム型を入れない。
- client専用クラスはclient package。JEIは既存clientDevelopmentのみ。外部JAR・テスト・開発資料を通常JARへ入れない。
- 加工、材料、上位化、Pickup／Deposit／Restock追加連携、サバイバルrecipe・最終textureは後続工程。sample_tokenは保持する。
- `.local`の新規ワールドを使う。既存ワールドを上書きしない。起動・GameTest・対話的実操作・未実施を区別する。

## レビュー重点

- 未解決Gemが他のキーの保存更新で消えないこと（Task 2）。
- 通常収納満杯の早期returnや拒否slotからの迂回で投入契約が変わらないこと（Task 3）。
- 同一item交換、古いmenu、upgrade差し替えによる消失／複製を拒否すること（Task 2・4）。
- 永続componentの標準同期で巨大payloadを送らず、client要約をserver保存として信用しないこと（Task 2・4）。
- 新規UUID、tier変更、設置／回収、client class読み込みを実際のゲームAPIで検証すること（Task 2・5）。

## ファイル構成

以下のsource pathは`core/src/{main,test}/kotlin/org/mocoserver/sophisticatedgemcase/core/`、`neoforge/src/main/kotlin/org/mocoserver/sophisticatedgemcase/`を基準とする。GameTestは`neoforge/src/gametest/kotlin/org/mocoserver/sophisticatedgemcase/tests/`、Java Mixinは`neoforge/src/main/java/org/mocoserver/sophisticatedgemcase/mixin/`へ置く。

- core: `GemKey.kt`、`GemCounts.kt`、`GemCountsTest.kt`。値と容量計算。
- 保存: `storage/StandardGem.kt`、`storage/GemStorageCodec.kt`、`storage/GemStorageState.kt`、`ModDataComponents.kt`。Gem判定とraw保存境界。
- 装着: `upgrade/GemCaseUpgradeItem.kt`、`upgrade/GemCaseUpgradeWrapper.kt`。登録・装着制限・保存callback。
- 投入: `storage/GemInputOutputHandler.kt`、`storage/GemTransfers.kt`、`integration/GemCaseBridge.kt`。既存slot制限、残量返却、Javaとの小さな接続。
- Mixin: `BackpackWrapperMixin.java`、`StorageContainerMenuMixin.java`、`neoforge/src/main/resources/sophisticated_gem_case.mixins.json`。
- menu: `menu/GemCaseContainer.kt`、`menu/GemMenuActions.kt`、`menu/GemMenuSession.kt`。serverの操作と表示revision。
- 通信: `network/GemRequests.kt`、`network/GemPages.kt`、`network/GemNetworking.kt`。型付き要求と上限付き応答。
- client: `client/GemCaseClient.kt`、`client/GemCaseTab.kt`、`client/GemPageCache.kt`。タブ登録、操作、検索と表示。
- 変更: `ModItems.kt`、`ModTemplate.kt`、`data/TemplateDataGenerators.kt`、`neoforge/build.gradle.kts`、metadata template、README、design／verification／client-acceptance。

## Task 1: 個数モデル

**Interfaces:** `public data class GemKey(val gemId: String, val purity: String)`。`GemCounts.of(entries: Map<GemKey, Int>): GemCounts`は防御的コピー。`count(key): Int`、`entries(): Map<GemKey, Int>`、`insert(key, amount): GemCountChange`、`extract(key, amount): GemCountChange`。結果は`state: GemCounts`、`moved: Int`、`remainder: Int`を持つ公開値。入力map／以前のstateへ変更を漏らさない。

- [x] `GemCountsTest`へ境界と所有権の失敗テストを書く。32,766へ2個投入はmoved=1、remainder=1、元count=32,766。別キーは独立。32,767へInt.MAX_VALUE投入はmoved=0、remainder=Int.MAX_VALUE。1個から2個取出はmoved=1、残数0。負数は契約違反。外部mapと返したmapの変更でstateが変わらない。
- [x] Java 21で`./gradlew :core:test`を実行し、未定義APIによる失敗を確認する。
- [x] 上記ファイルへモデルを実装する。capacityとの差を先に計算し、加算overflowを避ける。保存・表示順はここへ入れない。
- [x] `./gradlew :core:test :core:check`成功後に`feat: add bounded gem count model`でコミットする。

## Task 2: 永続化と装着

**Interfaces:** `StandardGem.keyOf(stack: ItemStack): GemKey?`、`stackOf(key: GemKey): ItemStack?`。`GemStorageCodec.read(data: CustomData?): GemStorageState`、`write(counts: GemCounts): CustomData`。状態は`Ready(counts)`／`Locked(reason)`／`ClientSummary(hasContents, locked)`。`GemCaseUpgradeWrapper.snapshot(): GemStorageState`、`replace(expected: GemCounts, updated: GemCounts): Boolean`、`hasContentsOrLocked(): Boolean`。Itemは`UpgradeItemBase<GemCaseUpgradeWrapper>`、wrapperは`UpgradeWrapperBase<GemCaseUpgradeWrapper, GemCaseUpgradeItem>`を使う。

- [x] `GemStorageGameTests.kt`でcomponent往復、名前変更拒否、最低純度未満拒否、破損／将来schemaのraw保持、未知ID／純度を含む別キー更新、限界byte／深さ／entryを試す。4,097件の入力はschema上限超過として無変更。初回は`./gradlew :neoforge:runGameTestServer`で新しいcomponent／itemの未定義または未登録による失敗を確認する。
- [x] `ModDataComponents`を登録する。値は可変NBTを外部へ公開しない`CustomData`を使い、永続codecはその原文を保持する。network codecはhasContentsとlockedの二つのbooleanだけを送り、client要約を作る。serverで要約を受け取った場合はLockedとして書換え禁止にする。rawを通常slot同期へ出さない。永続codec往復とwire要約の非対称性を明示的にテストする。
- [x] `GemStorageCodec`へ上限付き走査、schema検証、固定順の保存を実装する。NBTの未知fieldも既知schemaに無条件で上書きせず、認識できない構造としてロックする。保存のsourceはserverのReady状態だけ。原文copy／equalsの量も上限以内に抑える。
- [x] `StandardGem`はGemItem、GemItem.getGem/getPurity、Gem.toStackで標準stackを照合する。UnsocketedGemによる純度補正結果だけで受入可としない。
- [x] Item／wrapperを登録し、装着先をBackpackに限定する。canAdd、canRemove、canSwapの条件に専用upgradeの重複、中身、破損を追加する。同一item交換も抜け道にしない。replaceは現在のReady状態とexpectedの一致を確認してからcomponent更新とsaveを行う。
- [x] GameTestで二個目拒否、空の取外し許可、中身あり／未知／破損／同一item交換拒否、UUID付き実バッグのwrapper再取得、設置／回収、native tier recipeを通した保存維持を検証する。client要約は表示用判定だけに用い、server確定へ渡さない。
- [x] datagenへ仮modelとja_jp／en_us表示名・理由を追加する。creative登録で入手可能にする。recipeは追加しない。
- [x] `runData`後の別呼出しで`check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`、結果parserを実行し成功後にコミットする。

## Task 3: 公開投入と手動移動

**Interfaces:** `GemInputOutputHandler`は`ITrackedContentsItemHandler`をdelegateし、slot別／全体insertだけを追加処理する。`GemTransfers.insertDedicated(wrapper, stack, limit, simulate): ItemStack`は元stackを変更しない。`moveFromNormal(wrapper): Int`はそのバッグだけをslot順に移す。`GemCaseBridge.wrapInputOutput(backpack: BackpackWrapper, original: ITrackedContentsItemHandler): ITrackedContentsItemHandler`、`handleClick(menu: StorageContainerMenuBase<*>, slotId: Int, button: Int, clickType: ClickType, player: Player): Boolean`。Javaから使うbridgeには@JvmStaticと型を明示する。

- [x] `GemInsertionGameTests.kt`へ専用／通常双方の空き組合せ、残量、simulate保存callback未呼出、追加component保存、範囲外／拒否slot、IOフィルタ拒否、アドオン未装着を追加し、`./gradlew :neoforge:runGameTestServer`で未接続経路のテストが失敗することを確認する。
- [x] 公開IOだけを包む。BackpackWrapper.getInventoryForInputOutputのRETURNへ`remap=false`のMixinを置く。登録時だけwrapperを捕まえず、投入ごとに現在のupgradeを再取得する。delegateのtracked stacks／extract／slot数は変えない。全体挿入はアクセス制限と許可slotを確認し、slot別は範囲・isItemValid・isInsertBlockedなど既存制限を守る。容量不足だけで専用収納を拒否しない。
- [x] 専用受入の計画を作り、通常収納の残量と整合させて確定する。ロック時は専用を使わず通常経路へ戻す。一括移動は通常slotのextract制限と現在stackを確認し、受入分だけ減らす。別バッグ／player／処理用inventoryを走査しない。
- [x] Mixin設定をmetadataへ宣言する。BackpackWrapperの内部処理用getInventoryForUpgradeProcessingは変更せず、IInventoryWrapperUpgradeは工程3まで実装しない。
- [x] `StorageContainerMenuMixin`はclicked入口とstack版mergeItemStackへの限定接続を置く。source/targetが通常バッグslot／player slotかを実際のrangeで判定し、ghost slot、upgrade slot、加工出力を捕捉しない。純度別取出はTask 4の操作へ分離する。
- [x] 左／右クリック、Shift投入、ドラッグ、数字キー交換を`GemMenuInsertionGameTests.kt`で確認する。全処理の予定を先に組めない複合操作は変更前に拒否する。右クリックは投入予定一個だけを振り分け、元slotのswap先を失わない。clientでserver確定を先行再現しないようresyncも検証する。
- [x] block capabilityの方向拒否、同tickの失敗cache、UUIDなしitem capability、設置／携帯の実公開capabilityからの投入を確認する。Coreのフィルタだけで拒否される場合も専用へ迂回しない。既存8件も含めGameTestとcheckが成功後にコミットする。

## Task 4: menu・ページ通信・タブ

**Interfaces:** `GemCaseContainer(Player, Int, GemCaseUpgradeWrapper, UpgradeContainerType<GemCaseUpgradeWrapper, GemCaseContainer>)`をUpgradeContainerRegistryへ登録する。`GemMenuSession`はmenuごとにserver生成session UUIDと単調増加Long revisionを持つ。`GemRequest(menuId, upgradeSlot, sessionId, revision, action, key?, page)`、actionはINSERT_CURSOR／WITHDRAW_ONE／WITHDRAW_STACK／WITHDRAW_TO_PLAYER／MOVE_NORMAL／REQUEST_PAGE。`GemPage(menuId, upgradeSlot, sessionId, revision, page, totalPages, entries, locked)`。`GemMenuActions.perform(player: ServerPlayer, request: GemRequest): Boolean`。応答entryはGemKey、count、解決状態のみ。

- [x] `GemMenuActionGameTests.kt`へカーソル一個／最大stack／playerへのShift取出、取出先満杯、旧revision、要求再送、menu閉鎖、バッグとupgrade差替え、不正キーを作る。要求前後の全stackと保存状態を比較し、拒否で無変更を確認する。`./gradlew :neoforge:runGameTestServer`で新しい操作API未実装の失敗を確認してから実装する。
- [x] containerとsessionを作る。現在menu、stillValid、現在upgrade wrapper、バッグUUIDと装着slotを検証し、session開始以降の差替えを拒否する。player/normal/専用の変更予定を確認して確定する。取出先へ入る数だけ減らし、一操作の再送はrevisionで拒否する。
- [x] 1.21.1のRegisterPayloadHandlersEventで型付きC2S/S2C payloadを登録する。handlerはserver threadで上記actionを呼ぶ。client handler登録はclient packageへ隔離する。要求にrawNBT／完成stackを含めない。ページ番号は0以上totalPages未満、GemKeyは各256 byte、空一覧のtotalPagesは1としてpage 0に空entryを返す。action不一致の余計な入力は拒否する。
- [x] menuのbroadcastChangesで現payload／装着が変わったかを見てrevisionを更新する。capabilityからの変更でも更新する。wrapper再生成では状態が同じならrevisionを戻さない。ページはrevisionごとのsnapshotから作り、64 entryかつ32 KiB。巨大rawはページ化せずロック理由だけ返す。異なるrevisionのページを混ぜない。
- [x] `GemPageGameTests.kt`で65 entryの分割、256 byte境界、32 KiB以下のwire実測、負のpage、不正action、旧session、途中更新による旧page破棄を確認する。
- [x] client setupでUpgradeGuiManager.registerTabを呼ぶ。タブはscroll可能な種類一覧、名前検索、6純度と個数、瞬間投入ボタン、一括移動ボタンを持つ。名前はclientの翻訳で解決し、検索はserverが提供した全ページの一覧だけを対象とする。総件数はserver snapshotに従う。未知ID／純度と拒否理由を表示する。
- [x] 各純度表示の左／右／Shift左を上記actionへ接続する。仮描画とja_jp／en_usをdatagenへ追加し、未同期／更新中は操作できないようにする。専用入力slotを永続化しない。
- [x] datagen、check、通常JAR検査、GameTest成功後にコミットする。クライアント起動と実操作の結果はTask 5で記録する。

## Task 5: 検収と独立レビュー

- [x] datagenを再実行し、生成resourceの追跡／未追跡一覧とSHA256を比較する。意図した生成追加だけで再実行差分0を確認する。
- [x] Java 21、repo内cacheで`./gradlew check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`を実行する。`node scripts/check-gametest-result.mjs`でゼロ件成功でないことを確認し、件数とcommand／構成／ログをverificationへ残す。
- [x] 配布JARのcore／storage／menu／Mixin設定とresourceを検査する。外部MOD、fixture、skills、docsを含まないことを確認する。
- [x] 新しい`.local/storage-server`で通常JAR＋固定依存だけの専用サーバーを起動・停止し、新規worldで保存を検証する。開発clientはNeoForge版JEI入りで起動し、表示／装着／手動移動／取出を確認する。認証や画面操作を実施できない場合は未検収として分け、成功を捏造しない。
- [x] README、design、client-acceptance、verificationを実際の実装範囲へ更新する。加工と工程3連携は未実装と残す。rawデータとcache／world／JARをGitへ追加しない。
- [x] 独立した安価なサブエージェントへ最終diff、spec、計画、実測ログを渡す。Importantを修正し該当検証を再実行する。レビュー後の最終結果を記録しコミットする。mainへの統合はこの区切りの検収後に扱う。

## 固定APIの根拠と実行条件

- [UpgradeContainerRegistry](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/common/gui/UpgradeContainerRegistry.java)／[UpgradeContainerType](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/common/gui/UpgradeContainerType.java)／[UpgradeGuiManager](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/client/gui/UpgradeGuiManager.java)。sourceと対象JARの公開signatureをjavapで照合済み。StorageContainerMenuBaseのgetNumberOfStorageInventorySlots、getUpgradeContainers、clicked、stack版mergeItemStack、broadcastChangesとBackpackWrapperの両inventory取得も対象JARに存在する。作者source全体との同一性は断定しない。
- [InventoryIOHandler](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/inventory/InventoryIOHandler.java)／[FilteredItemHandler](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/inventory/FilteredItemHandler.java)。filterとslot制約は変えない。
- [Backpacks ModItems](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/init/ModItems.java)のitem capability、[BackpackBlockEntity](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/backpack/BackpackBlockEntity.java)のexternal handler、[CachedFailedInsertInventoryHandler](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/inventory/CachedFailedInsertInventoryHandler.java)。cacheは同tickの同stack参照への拒否を保持する。拒否から同tickに専用容量が空いても次tickまで再試行を保証しない。
- [Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)／[Registering Payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/)は1.21.1資料。Minecraft/NeoForge sourceのBuilderでnetwork codec未指定時の永続codec流用も確認済み。native component型をcoreへ渡さない。
- Mixinのメソッドdescriptorは対象JARをjavapで確認してから指定する。foreign classを対象にするためremap=false。通常JARでMixin適用と専用サーバーsideを検証する。
- ローカル検証時のJava 21は`/home/osamu/develop/moco-server/server-pack/.local/issue7-gradle/jdks/eclipse_adoptium-21-amd64-linux.2`を読み取り利用し、GRADLE_USER_HOME=`$PWD/.local/gradle`、JAVA_TOOL_OPTIONS=`-XX:ActiveProcessorCount=2`を使う。

## 計画の自己確認

容量・所有権はTask 1、保存互換・標準Gem・取り外しはTask 2、投入優先と手動経路はTask 3、一覧・通信・拒否要求はTask 4、datagen／通常JAR／実動作／独立レビューはTask 5が担当する。工程3と上位機能へ範囲を広げない。本計画は承認後に実装し、独立レビューと検証を実施した。結果と未検収はdocs/verification.mdの工程2を参照する。GUI実操作は未実施として残した。


## 実行時の補足

Task 1〜4の本番実装・自動検証を実施し、Task 5の通常JAR起動・保存コピーの再起動、JEI入りclient起動、独立レビューを完了した。Task 5の対話的画面操作／認証接続は未検収であり、上記チェックは実施済みと未実施を検証記録へ分けて記録したことを表す。追加の旧バッグUUID・距離・native filter／cache／UUIDなしcapabilityの境界テストも実施した。

固定KFFのobject subscriberはinstance methodとMOD bus明示を必要とする。永続rawとwire二booleanの非対称性を維持し、client型をcommonから参照しない callbackで受信処理を登録した。通常スロットのMixinは対象JARのdescriptorに限定した。初期recipe／texture、工程3の回収・転送はここへ含めない。
