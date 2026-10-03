# Gem Case回収・転送連携 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 下位収納を既存Pickup／Deposit／Restockへ接続し、独立した転送設定と種類・純度フィルタを提供する。

**Architecture:** 個数schema 1を維持し、設定を別componentへ保存する。Pickupは公開inventory wrapperで専用優先にし、Deposit／Restockは個別メソッドへ限定したMixinで通常転送と専用転送を分ける。バッグ単位の操作文脈で二重simulate、通常Restockの迂回、再入を防ぎ、既存menu sessionの世代検証で設定を編集する。

**Tech Stack:** Minecraft 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0／Sophisticated Core 1.5.2.2343／Backpacks 3.26.6.2174／Apotheosis 8.9.0。

**Spec:** [承認済み連携設計](../specs/2026-10-03-gem-case-integration-design.md)。開始点はmain `213818f`、設計commit `b99ac15`。

## Global Constraints

- 種類×純度ごと32,767個。標準Gemだけを専用収納へ入れ、通常slot・extract・tracked stacksを仮想的に増やさない。
- 対応する既存upgradeの装着／有効状態を必要とする。Gem単独にはPickup／Deposit／Restockの能力を付けない。
- Deposit／Restockは初期OFF、フィルタ初期方式はすべて。指定／除外は種類と純度のAND、空集合はその軸を制限しない。両集合が空の指定は対象なし、除外は全対象。
- 設定schema 1、各文字列256 UTF-8 byte、二方向の集合要素合計4,096件、NBT 1MiB／深さ8。通常slot networkはlocked booleanだけ、詳細は64 entry／32KiBのページ。
- 設定component不在は正常な初期状態。未知／破損はraw保持、自動転送と設定上書きを停止。正常な個数保存の手動操作は維持する。
- server threadで検証と確定。通常転送→専用転送の順、部分転送の未移動分は元へ保持し、通知リストへ実移動だけ加える。外部handlerの契約違反まで全体rollbackを保証しない。
- coreはpure Kotlin/JVM。規約のpublic／型明示・所有権・日本語KDocを守り、外部型をcoreに持ち込まない。client型はclient packageへ隔離する。
- 依存版・通常入手recipe・上位加工・最終texture・packを変更しない。生成resourceはdatagenから作る。
- 実装は統括者中心、独立可能なテスト等だけ安価なagentへ委譲する。最後に実装担当と別の安価なreviewerを履歴なしで呼ぶ。成功済みの広範囲検証を手順だけの理由で繰り返さない。

## Review Focus

1. 公開IOからprocessing wrapperへ重なるsimulate: 残容量1／入力3でも専用受入は一回、実投入と同じ余り（Task 2）。
2. 同じバッグへ再入するexternal callback: 保存の再変更を拒否し、別バッグと後続操作には文脈を漏らさない（Task 3）。
3. handlerの部分受入／部分extract: simulate量を消費量とみなさず、実移動量と全保存の合計を一致させる（Task 3）。
4. 最大の正常保存・巨大slot数: 一操作の仕事量を制限し、未処理Gemを残したまま再操作できる（Task 3）。
5. 設定だけの変更・同期途中の編集: countsが同じでもrevisionを更新し、古いページ・要求を混ぜない（Task 4）。

## ファイルと依存

以下の相対パスは`K = neoforge/src/main/kotlin/org/mocoserver/sophisticatedgemcase/`、`J = neoforge/src/main/java/org/mocoserver/sophisticatedgemcase/`、`G = neoforge/src/gametest/kotlin/org/mocoserver/sophisticatedgemcase/tests/`を先頭へ展開する。coreは`core/src/{main,test}/kotlin/org/mocoserver/sophisticatedgemcase/core/`。

Task 1（不変フィルタと保存）→Task 2（投入文脈と回収）→Task 3（専用転送）→Task 4（設定通信と画面）→Task 5（配布検収とレビュー）。共有するwrapperと操作文脈は同時に別agentへ編集させない。

## Task 1: フィルタモデルと設定の永続契約

**Files:** Create core `GemFilter.kt`／`GemTransferSettings.kt` とtest `GemFilterTest.kt`、K`storage/GemSettingsCodec.kt`／`storage/GemSettingsState.kt`、G`GemSettingsGameTests.kt`。Modify K`ModDataComponents.kt`／`upgrade/GemCaseUpgradeWrapper.kt`。

**Interfaces:** coreへpublic `GemFilterMode { ALL, INCLUDE, EXCLUDE }`、`GemTransferDirection { DEPOSIT, RESTOCK }`を置く。`GemFilter.of(mode: GemFilterMode, gemIds: Set<String>, purities: Set<String>): GemFilter`は集合をコピーして所有し、`matches(key: GemKey): Boolean`、`mode`／`gemIds`／`purities`を読み取り専用で公開する。`GemTransferRule(enabled: Boolean, filter: GemFilter)`、`GemTransferSettings(deposit: GemTransferRule, restock: GemTransferRule)`は不変、`DEFAULT`は両OFF／ALL／空集合。

loader側はinternal sealed `GemSettingsState.Ready(settings: GemTransferSettings)`／`Locked(reason: String)`／`ClientSummary(locked: Boolean)`。`GemSettingsCodec.read(data: CustomData?): GemSettingsState`／`write(settings: GemTransferSettings): CustomData`／`canWrite(settings: GemTransferSettings): Boolean`。wrapperへ`settingsSnapshot(): GemSettingsState`／`replaceSettings(expected: GemTransferSettings, updated: GemTransferSettings): Boolean`を追加する。Gem個数のsnapshot／replaceは維持する。

- [x] `GemFilterTest`を先に追加する。`ALL.matches(anyKey)==true`、INCLUDE両空=false、EXCLUDE両空=true、IDのみ／純度のみ／両軸AND、入力集合の後変更と取得集合の変更で結果が変わらないことをassertする。`:core:test`で未定義APIの失敗を確認する。
- [x] 上記core APIを実装し、`:core:test :core:check`で成功とcore境界を確認する。
- [x] `GemSettingsGameTests`へabsent→DEFAULT、設定往復、未知ID／純度の保持、同じrawの不明schema／不正型／余計field／重複／4,097要素／257byte／深さ9／1MiB超過→Locked、通常wire実測1byte、client要約のserver上書き拒否を追加し、実装後のGameTestで成功を確認する。loader側は実装先行であり先行REDは未実施（下記実行記録参照）。
- [x] `gem_transfer_settings`をCustomDataのpersistent codec＋独立したboolean-only network codecで登録する。NBTは`schema_version: Int=1`、`deposit`／`restock`各compoundに`enabled: byte(0/1)`、`mode: "all"|"include"|"exclude"`、`gem_ids`／`purities`のstring listだけを許す。重複は不正、正規ResourceLocation・小文字純度を256byteで検証し、登録の有無は保存拒否理由にしない。固定順保存とcopy前のbounded非変更viewを使う。ClientSummaryは保存sourceにしない。
- [x] wrapperの設定更新をserver thread・expected一致・canWriteで確定する。破損設定では個数の手動取出が可能、empty時の取り外しでは設定componentがitemに残ることもテストする。`check :neoforge:runGameTestServer`と結果parserが成功後、この単位をコミットする。

## Task 2: 操作文脈・処理用wrapper・Pickup

**Files:** Create K`integration/GemOperationContext.kt`／`storage/GemProcessingHandler.kt`、G`GemPickupGameTests.kt`。Modify K`storage/GemInputOutputHandler.kt`／`storage/GemTransfers.kt`／`upgrade/GemCaseUpgradeWrapper.kt`。

**Interfaces:** `GemOperationContext.routingSuppressed(storage: IStorageWrapper): Boolean`、`withoutRouting<T>(storage: IStorageWrapper, action: () -> T): T`。同じUUIDを持つwrapperは同じバッグとして扱い、UUIDなしの場合のみwrapper identityを使う。文脈はthread-local stackとして入れ子を保持し、finallyで元へ戻す。

`GemProcessingHandler(storage: IStorageWrapper, original: ITrackedContentsItemHandler): ITrackedContentsItemHandler`はdelegateでinsertの二種とisInsertBlockedだけを扱う。wrapperがBackpacksの`net.p3pp3rf1y.sophisticatedbackpacks.api.IInventoryWrapperUpgrade`を実装し、`wrapInventory(original: ITrackedContentsItemHandler): ITrackedContentsItemHandler`を返す。IPickupResponseUpgradeは実装しない。

- [x] 有効IPickupResponseUpgradeのnative列挙経路を使い、Pickup装着・非装着／無効、既存filter拒否、通常満杯でも専用へ、両方満杯の余り、追加componentは通常経路を`GemPickupGameTests`へ追加し、未接続状態で専用受入テストが失敗することを確認する。既存IntegrationContractGameTestsのwrapper実例と実バッグfixtureを使う。Gem単独では列挙が空、無効Pickupも列挙に現れないことをassertし、無効wrapperのpickupへ直接呼んでnativeの装着判定を省略しない。
- [x] 公開IOの外側で専用配分後、delegateへ渡す区間をwithoutRoutingで包む。抑止中は内側processing handlerが通常経路だけを呼び、IOも再配分しない。simulateも同じ文脈を通す。外側のslot／input filter拒否は維持する。
- [x] processing handlerを実装し、投入ごとに現在のGem wrapperを再取得する。native slotの有効性とdelegate契約を守り、専用へ空きがある場合だけ容量によるinsertBlockedを緩める。公開IOとprocessingの両方で、設定Locked／ClientSummaryでは自動投入を専用へ流さず通常経路へ返す。手動クリックとタブ取出のGemTransfersは正常な個数保存を引き続き使える。Pickupの既存フィルタを改変しない。
- [x] 残容量1／入力3／通常満杯の公開IOに対しsimulate余り2、実投入余り2、元stack3、保存増分1をassertする。文脈の入れ子、例外解除、別バッグが抑止されないことも確認する。Task 1と既存31件を含む`check :neoforge:runGameTestServer`・parser成功後コミットする。

## Task 3: 専用転送と固定メソッドへの接続

**Files:** Create K`integration/GemTransferBridge.kt`／`storage/GemExternalTransfers.kt`、J`mixin/GemUpgradeStorageAccessor.java`／`mixin/DepositUpgradeMixin.java`／`mixin/RestockUpgradeMixin.java`、G`GemExternalTransferGameTests.kt`。Modify K`integration/GemOperationContext.kt`／`upgrade/GemCaseUpgradeWrapper.kt`、`neoforge/src/main/resources/sophisticated_gem_case.mixins.json`。

**Interfaces:** `GemExternalTransfers.deposit(storage: IStorageWrapper, target: IItemHandler): List<ItemStack>`／`restock(storage: IStorageWrapper, source: IItemHandler): List<ItemStack>`。`GemTransferBridge.deposit(storage: IStorageWrapper, nativeUpgrade: IUpgradeWrapper, target: IItemHandler, transferred: List<ItemStack>): List<ItemStack>`／`restock(storage: IStorageWrapper, nativeUpgrade: IUpgradeWrapper, source: IItemHandler, transferred: List<ItemStack>): List<ItemStack>`はJavaから@JvmStaticで呼べるようにする。

再入防止は`GemOperationContext.exclusive<T>(storage: IStorageWrapper, action: (GemTransferPermit) -> T): T?`（再入時null）、`mayMutate(storage: IStorageWrapper, permit: GemTransferPermit?): Boolean`。permitは発行した文脈とのidentity一致で検証し、他バッグ・失効済みtokenは拒否する。wrapperの`replace(expected: GemCounts, updated: GemCounts, permit: GemTransferPermit? = null): Boolean`へ既存二引数Kotlin呼出しを保って拡張し、設定更新もexclusive中の通常呼出しを拒否する。

- [x] native装着wrapperのdepositToHandler／restockFromHandlerを使い、両方向ON／OFF、native無効、既存filterとGem filterが逆、normalと専用に同じGem、外部満杯・一個だけ受入・部分extract・extract拒否のテストを追加する。専用転送がまだ起きない失敗を確認する。
- [x] accessorは固定UpgradeWrapperBaseのprotected storageWrapperだけを取得する。DepositメソッドRETURNでnative返却リストへ専用の実移動分を追加する。Restockのoriginal呼出し全体をwithoutRoutingで包み、finally後に専用転送を追加する。単純HEAD/RETURNの組合せで文脈を残さず、使用可能なMixinExtrasのWrapMethodでoriginalをtry/finallyする。nativeのonHandlerInteract入口・個別メソッドdescriptor・isEnabledを対象JARで照合する。bridgeへ渡したnativeUpgradeが現在の装着wrapperと同一で、有効かつ対応Deposit／Restock型であることを確認する。
- [x] 専用転送を固定GemKey順・native最大stack以下で実装する。Depositはexclusive内で必要数を予約減算し、targetの実insert後の余りを予約へ戻して、実受入数だけを通知する。予約／復元のreplaceだけpermitを渡す。受入0は個数を元へ戻す。例外時は文脈を必ず解除し、回復可能な無変更失敗と外部契約違反を区別して握り潰さない。
- [x] Restockはsourceの標準stack・simulate可否・専用のcanWriteと空きを確認し、exclusive内で実extractの個数だけを専用へ加える。EMPTY拒否では専用を変えない。同じkeyでもsourceの追加componentを数値化しない。未知entryはスキップし、元のnormal転送にはGem側filterを掛けない。
- [x] 一操作の専用転送に最大512成功stack／16,384 handler slot訪問のbudgetを置く。上限到達では残りを元へ保持する。再入callbackの手動取出・公開IO・設定変更の拒否、別バッグへの正当な投入、途中の外部拒否、callback例外後の次操作、最大保存と巨大slot数でbudget内に終わることを検査する。正常handlerの場合の専用＋normal＋external＋余りの合計を必ず比較する。
- [x] OFF／Gem filter除外の通常Restockが専用へ流れないこと、移動stackリストの個数と実移動量、native設定INVENTORYモードで専用へ既存filterを二重に掛けないことを確認する。`check :neoforge:runGameTestServer`・parser成功後コミットする。

## Task 4: 世代付き設定通信と編集画面

**Files:** Create K`network/GemSettingsMessages.kt`／`network/GemSettingsPages.kt`／`menu/GemSettingsActions.kt`／`client/GemSettingsPanel.kt`／`client/GemSettingsCache.kt`、G`GemSettingsMenuGameTests.kt`／`GemSettingsWireGameTests.kt`。Modify K`menu/GemMenuSession.kt`／`menu/GemCaseContainer.kt`／`network/GemNetworking.kt`／`client/GemCaseClient.kt`／`client/GemCaseTab.kt`／`data/TemplateDataGenerators.kt`。

**Interfaces:** enum `GemSettingsAction { REQUEST_PAGE, SET_ENABLED, SET_MODE, SET_GEM_ID, SET_PURITY }`。`GemSettingsRequest(menuId: Int, upgradeSlot: Int, sessionId: UUID, revision: Long, action: GemSettingsAction, direction: GemTransferDirection, enabled: Boolean?, mode: GemFilterMode?, value: String?, selected: Boolean?, page: Int)`。actionに必要なfieldだけを許し、余計なfieldは拒否する。

`GemSettingsPage(menuId: Int, upgradeSlot: Int, sessionId: UUID, revision: Long, page: Int, totalPages: Int, locked: Boolean, depositEnabled: Boolean, restockEnabled: Boolean, depositMode: GemFilterMode, restockMode: GemFilterMode, entries: List<GemSettingsEntry>)`。`GemSettingsEntry(direction: GemTransferDirection, axis: GemFilterAxis, value: String)`、axisはGEM_ID／PURITY。`GemSettingsPages.partition(state: GemSettingsState): List<List<GemSettingsEntry>>`、`GemSettingsActions.perform(player: ServerPlayer, request: GemSettingsRequest): Boolean`。clientは同じsession／revisionの設定全ページだけで編集する。

- [x] 旧revision・再送・閉鎖・距離・UUID／upgrade差替え・余計field・不正enum・256byte／257byte・設定ロックを要求し、Gem counts・設定・全stackの無変更をassertする。設定だけの変更でも既存Gem一覧と設定のrevisionが更新されるテストを先に作り、未定義APIで失敗を確認する。
- [x] GemMenuSessionにcountsとsettingsの一組のsnapshotを保持させ、どちらの変更でも同じrevisionを増加する。通常Gem要求と設定要求を同じnonce・revision・quota16/tick・isCurrentで検証する。設定page0も初回／更新時にbroadcastする。callback登録はcommonからclient型を参照しない既存方式を使う。
- [x] requestは一要素更新、pageは64 entry／32KiBの小さい方で分割する。空／ロックにもpage0を返す。未知保存ID／純度は保持したまま表示・解除可能にする。新規追加は登録済みGem ID／既知6純度から選び、既存未知値を消す操作では再登録を要求しない。byte測定、4,096件、page順序／重複／世代変更、片方同期途中の編集拒否をテストする。
- [x] 既存Gemタブに「収納／転送設定」のview切替を設け、設定画面を別Widgetへ分ける。方向選択、対象ON／OFF、方式三種、登録Gemの翻訳名検索＋選択一覧、6純度選択、未知値一覧を表示する。GemRegistryの登録Gem全体から候補を作り、未保管Gemも選択できる。未同期／ロック時は編集不可、設定ロックでも正常Gemの手動入出力は使える。大きい画面の追加・client importのcommon流入を避ける。
- [x] 翻訳をdatagen入力へ追加し、`:neoforge:classes :neoforge:runData`後に追跡／未追跡を確認する。別呼出しの`check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`・parser成功後コミットする。

## Task 5: 配布検収・独立レビュー・main統合

**Files:** Modify `README.md`／`docs/design.md`／`docs/verification.md`／`docs/client-acceptance.md`、この計画の実行記録。必要な新規JAR entriesを`neoforge/build.gradle.kts`のverifyModJarへ追加する。

- [x] datagenを再実行し、生成resourceの全ファイル一覧／SHA256／追跡と未追跡を比較して意図しない差分0を確認する。設定codec・processing handler・transfer bridge・Mixin設定・client resourceを通常JARで検査する。外部MOD・fixture・skills・docsを含めない。
- [x] 最後の本番変更後に`check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`と`node scripts/check-gametest-result.mjs`を実行する。既存31件と新規が正の件数で成功、ktlint／detekt／単体／core境界も成功と実測する。変更後の失敗・追加テスト以外の理由では同じ広範囲検証を重複しない。
- [x] `.local/integration-server`の新規worldへ通常JAR＋固定依存だけを用意して専用server起動・保存・停止を確認する。保存worldはコピーから再起動し、countsと設定の保持を調べる。JEI限定導入のclientも起動する。画面実操作／認証接続を実施できない場合は未検収に分け、GameTestをその代わりとしない。
- [x] 版・コマンド・ログ・件数・hash・実施／未実施・外部handler契約の限界を検証記録へ残す。通常／専用に同じGem、filter逆条件、ON／OFF、満杯、次操作の回復をclient-acceptanceへ追加する。
- [x] 履歴なしの独立した安価なreviewerへ最終diff、spec、本計画、固定source、実測ログを渡す。数量保存、再入／文脈解除、通常Restock迂回、settings raw同期、権限と世代を重点確認する。Importantを根拠確認後に修正し、影響する検証を再実行する。
- [x] 記録をコミットし、ユーザーの既存指示に従いローカルmainへ統合する。新しい工程へ進む前にtreeと作業木を確認する。push／pack導入はこの計画に含めない。

## 固定APIと実行条件

BackpacksのIInventoryWrapperUpgrade.wrapInventoryはITrackedContentsItemHandler→ITrackedContentsItemHandlerで、Coreではなく`net.p3pp3rf1y.sophisticatedbackpacks.api`に存在する。InventoryModificationHandlerの公開wrapper列挙とinitializeWrappedInventoryを固定sourceで確認した。Pickup.pickup(Level, ItemStack, boolean): ItemStackは既存filter判定後にprocessing handlerへ投入する。

Deposit.depositToHandler(IItemHandler): List<ItemStack>／Restock.restockFromHandler(IItemHandler): List<ItemStack>、UpgradeWrapperBase.getUpgradeStack／isEnabled／protected storageWrapperは固定公開JARとsourceを照合する。対象foreign classのMixinはremap=false、require=1、descriptorを指定する。通常server起動を適用・side確認に使う。根拠URLは[specの固定資料](../specs/2026-10-03-gem-case-integration-design.md#固定apiの根拠)と[対応構成](../../compatibility.md)。

Java 21は`/home/osamu/develop/moco-server/server-pack/.local/issue7-gradle/jdks/eclipse_adoptium-21-amd64-linux.2`を読み取り利用し、GRADLE_USER_HOME=`$PWD/.local/gradle`、JAVA_TOOL_OPTIONS=`-XX:ActiveProcessorCount=2`を指定する。ログ・cache・外部JAR・worldは`.local`／buildへ置き、Gitへ追加しない。

## 自己確認と実行状態

フィルタ／保存はTask 1、回収／二重simulateはTask 2、外部転送／再入／仕事量はTask 3、設定要求／一覧／client編集はTask 4、配布・保存コピー・記録・独立レビューはTask 5で検証する。後続taskの型と署名はInterfacesに定義したものを使う。全spec項目とReview Focusの5条件をtaskへ対応させ、Task 3のnativeUpgrade identity照合とTask 2の無効Pickup列挙も固定APIに合わせた。Task 1／2を3361f7bへコミット済み。Task 3／4の実装を検収し、通常JAR／保存コピー／client起動と独立レビューまで完了。2257de4をローカルmainへfast-forward統合済み。


## 実行記録（2026-10-03）

- Task 1 coreは未定義APIのREDから実装後のGREENを確認。loader側は実装先行となり、先行REDは未実施。後から人工的なREDは作らず、保存／wire／ロック契約を実装後GameTestで検証した。
- KFFの範囲依存がFML loader 4.0.45を選び、NeoForgeの4.0.44とJPMS module衝突した。dependencyInsightで原因を確認し、catalogのstrict版とconstraintで4.0.44へ揃えた。
- Task 2はPickup専用受入の期待失敗を確認後に接続。root check、必須44件、通常JAR内容検査が成功し、Task 1と合わせて3361f7bへコミットした。
- Task 3は必須46件中専用Deposit／Restockの2件だけが期待失敗。Task 4は新しい設定APIの未定義でcompile失敗を確認した。両担当は所有ファイルを分けて並行実装し、Gradleはrootだけが実行した。
- 初回結合GameTestは66件中63件成功。残る3件はmutable session参照／未登録Gem ID／実extractできないhandler fixtureを修正した。途中の部分insert後に例外が起きても未受入数だけ復元するテストは成功した。追加wireと同期境界テストを含む最終検証を進める。

- 2026-10-04再開後、長関数と条件を分割し、残るdetekt／ktlint違反を修正。resume-acceptance-5.logでroot check／通常JAR検査／必須72件が成功。実バッグUUID変更の設定要求テストを追加し、uuid-final.logでcheck／必須73件とparser73が成功。本番の変更なし。
- 最大4,096entry×256byteの全設定ページをencode/decodeし、64entry／32KiB以下と集約完了を確認。不正enum、counts/settings同期途中・旧世代・重複、設定ロック、未知値解除、16要求/tickの上限と次tick回復、設定要求の閉鎖／距離／UUID／upgrade差替えの無変更を検査した。
- datagen再実行は全6ファイルの一覧／SHA256差分0。通常JAR専用serverは新規worldと保存コピー再起動がexit 0、個数と転送設定の保持を観測。JEI clientはresource／GUI atlas生成までで中断。GUI実操作・認証接続・pack・Windowsは未検収。
- 新規の履歴なし独立reviewerが全差分と新規コードを確認し指摘なし、73件成功も追認。検証ログ・成果物hash・外部handler契約・巨大handler走査制限はdocs/verification.mdへ記録。

- 2257de4へTask 3／4と検収記録をコミットし、ローカルmainへfast-forward統合。mainと検収branchのcommit／tree一致、作業木cleanを確認した。push／pack変更は実施していない。統合後の追記は文書だけで、本番と検証済みtreeを変更しない。
