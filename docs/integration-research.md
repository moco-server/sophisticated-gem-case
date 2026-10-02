# 対象版の連携調査

## 対象と確認方法

[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)の最初の区切りとして、作者ソースと公開JARを確認した。作者ソースの調査は設計根拠、GameTestは公開JARの一部の実動作確認であり、本アドオンのGem機能の完成を示すものではない。

| MOD | 公開JAR | 固定ソースcommit |
| --- | --- | --- |
| Apotheosis | 1.21.1-8.9.0 | `7dd7cec26830074f1745529eb8f1f533092733d0` |
| Sophisticated Backpacks | 1.21.1-3.26.6.2174 | `fd0e91bb6cd294a778c4bea770b64d71ef09d488` |
| Sophisticated Core | 1.21.1-1.5.2.2343 | `f5ec6fb7636a867f87e37cc388077be20221abfe` |

取得版・hash・sideは[対応構成](compatibility.md)、実行結果は[検証記録](verification.md)を参照。公開JARの公開メソッドはjavapとGameTestでも照合した。固定ソース全体と配布JARが完全一致するとは断定しない。

## 通常Gem Caseの機能一覧

上位版の「フル機能」の比較対象は通常Gem Case。Ender Gem Caseの共有収納・Integer.MAX_VALUE容量や、Gem Cutting Table／Salvaging Table固有の別加工を含めない。

- Gem種類一覧、名前検索、装備カテゴリに適合するGemの絞り込み、スクロールと種類の選択。
- 選択した種類の6純度の個数表示・取出。表示用ItemStackは最大64個で、保存数とは別。存在するGemを先にし、種類ID順で並べる。
- 入力枠からの投入、プレイヤーからのShift移動、Gemの取出とプレイヤー側へのShift移動。
- 種類×純度ごとに32,767個を個数保存。Gem種類はApotheosisのDynamicHolderのID、純度はPurityのserialized nameで識別する。
- 永続化する加工材料6枠、対応材料だけの受け入れ。自動搬送には材料枠を公開しない。
- 5段階の純度強化、実際に選ばれる材料と必要数の表示、素材不足／Gem不足時のボタン無効化。
- 通常クリックで1回、Shiftクリックで最大64回の強化。毎回レシピを再確認し、不成立になれば停止する。強化先の容量上限も確認する。
- 保存、再設置時の復元、開いているmenuへの同期。アニメーション・効果音はゲーム側の表示表現で、収納ロジックの再利用根拠とは分ける。

資料: [GemCaseTile](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseTile.java)、[GemCaseMenu](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseMenu.java)、[GemCaseScreen](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseScreen.java)、[GemCaseSlot](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseSlot.java)。

### Gem識別と加工

UnsocketedGem.ofはGemItemのcomponentから種類と純度を読み、種類が解決済みなら最低純度へ補正する。isValidは種類の解決を確認する。アドオンの入力では実Gemアイテムであることも確認する。未解決IDを読み込み時に破棄する元実装は、そのまま保存方式として流用しない。

GemUpgradeMatchはpackage-privateで、アドオンから直接呼び出せない。公開PurityUpgradeRecipe／GemCuttingMenu.getRecipesを使って条件を読み、独自の消費計画を作る。Gemの種類は同一、入力Gemは同一純度2個、出力は次の純度1個。登録レシピのデータパック変更を尊重し、以下の既定値を本番コードへ固定しない。

| 入力 → 出力 | gem_dust | 右材料の候補（いずれか一つ） |
| --- | --- | --- |
| cracked → chipped | 1 | mysterious_scrap_metal×3 / timeworn_fabric×1 |
| chipped → flawed | 3 | mysterious_scrap_metal×9 / timeworn_fabric×3 / luminous_crystal_shard×1 |
| flawed → normal | 5 | mysterious_scrap_metal×27 / timeworn_fabric×9 / luminous_crystal_shard×3 / arcane_sands×1 |
| normal → flawless | 7 | timeworn_fabric×27 / luminous_crystal_shard×9 / arcane_sands×3 / godforged_pearl×1 |
| flawless → perfect | 9 | luminous_crystal_shard×27 / arcane_sands×9 / godforged_pearl×3 |

材料IDのnamespaceは全てapotheosis。表は公開JARの`data/apotheosis/recipe/gem_cutting/*.json`から確認した。

元の選択はgetRecipesの返す順でレシピを調べ、材料枠を0から走査する。左材料が一致した枠はその回の右判定に使わず、左右に別枠が必要。SizedIngredientの候補はリスト順の最初の一致を採る。独自のID順ソートを追加しない。通常収納から不足分を補う際の候補・個数配分は上位版設計でこの挙動と照合して決める。

資料: [UnsocketedGem](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/UnsocketedGem.java)、[GemUpgradeMatch](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemUpgradeMatch.java)、[PurityUpgradeRecipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/cutting/PurityUpgradeRecipe.java)。

## 接続方法と制約

### 収納・Pickup

元のGemCaseTile.depositGemは上限へ丸め、insertItemは有効Gemの余りを返さない。公開JARのプローブでも32,766個へ2個を投入すると32,767個になり、余りはEMPTYだった。専用収納は個数管理と受入可能数を独自実装し、通常収納へ渡す残量を正しく返す。

PickupUpgradeWrapperは既存のPickupフィルタを確認してからgetInventoryForUpgradeProcessing().insertItemを呼ぶ。**Gemアドオン自体をIPickupResponseUpgradeにして自動回収能力を持たせない**。IInventoryWrapperUpgrade.wrapInventoryは、この処理用インベントリへ独自の受入層を追加できる公開APIである。専用収納を仮想通常スロットとして列挙せず、insertの受入と残量だけを扱う案を採る。

IInsertResponseUpgradeは補助フックにはなるが、単独採用しない。InventoryHandlerSlotTrackerの全体挿入は「空き枠なし、入力のアイテム種が既存収納にない」場合にbeforeInsertより前で戻る。公開JARのプローブでこの早期returnを確認した。またGUIのmergeItemStackはSlot.setで移すためinsertItem経路を通らない。手動投入はStorageContainerMenuBase.clicked／quickMoveStack／mergeItemStackの対象操作に限定したMixin候補を用意する。

通常の入出力用getInventoryForInputOutputは処理用インベントリと別経路である。IInventoryWrapperUpgradeが全入出力へ適用されるとは扱わず、下位版の実装時に通常capabilityの投入も同じ受入処理へ接続する。全体／slot別挿入、満杯、simulate、無効Gem、他アップグレードとの併用を検証する。

資料: [PickupUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/upgrades/pickup/PickupUpgradeWrapper.java)、[InventoryModificationHandler](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/backpack/wrapper/InventoryModificationHandler.java)、[InventoryHandlerSlotTracker](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/inventory/InventoryHandlerSlotTracker.java)。

### Deposit・Restock

IItemHandlerInteractionUpgradeにより同じ収納先との操作へ独自wrapperも参加できるが、InventoryInteractionHelperは該当wrapperをまとめて呼ぶ。単にGem wrapperを追加しても、既存操作内のフィルタや挿入先、実行順は制御できない。

Depositは処理用インベントリから、既存Depositフィルタ付きの転送先へ移す。Restockは既存Restockフィルタ付きの処理用インベントリへ入れる。専用収納をこの通常経路へ足すだけでは、Gem専用フィルタとの二重一致になってしまう。

採用候補はDepositUpgradeWrapper.depositToHandler／RestockUpgradeWrapper.restockFromHandlerへの限定Mixin。既存の通常収納操作を維持し、対応wrapperの操作時だけ専用収納の独立した転送を加える。Gem専用転送の結果も返却リストへ含め、通知数の整合を取る。Restockの通常操作中は専用収納への自動振分けを抑止する必要がある。Gem設定OFFやGemフィルタ除外でも通常Restockの投入が専用収納へ流れないよう、bagと操作に限定した文脈を例外時にも解除する。

公開JARのプローブでは、装着したDeposit／Restockが共通インターフェースで発見され、個別の転送メソッドで4個を往復できることを確認した。GemアドオンのMixin、Gemフィルタ、重複防止は未実装。既存と専用に同じGemがある場合、双方の装着、ON／OFF、転送先満杯、途中で受入拒否された場合を連携工程で検証する。

資料: [InventoryInteractionHelper](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/util/InventoryInteractionHelper.java)、[DepositUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/upgrades/deposit/DepositUpgradeWrapper.java)、[RestockUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/upgrades/restock/RestockUpgradeWrapper.java)。

### 装着・取り外し・上位化

IUpgradeItemのcanAddUpgradeTo／canRemoveUpgradeFrom／canSwapUpgradeForで装着・取り外し・交換を検証できる。下位と上位の共通グループだけで設定依存の制限にせず、個別条件で合わせて一個を強制する。通常の交換でも中身を持ち出せないようにする。

装着中の変換はStorageContainerMenuBase.clickedにStackUpgradeConversionItem専用分岐があり、新しいtarget ItemStackを作る。公開JARのプローブでもtargetにsourceの保存componentはなかった。通常クラフト用のUpgradeNextTierRecipeのcomponentコピーとは別であり、そのまま流用しない。

Gem専用変換アイテムと、このアイテムのアップグレード枠への右クリックに限定したMixinを候補とする。サーバーでsource・target・装着条件・アクセスを検証し、データをコピーしたtargetを準備してから置換と一個の消費を確定する。特殊な変換を通常取り外し禁止と区別し、失敗時の状態維持を上位版のGameTestと実操作で検収する。

資料: [IUpgradeItem](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/upgrades/IUpgradeItem.java)、[StorageContainerMenuBase](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase.java)、[StackUpgradeConversionItem](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/upgrades/stack/StackUpgradeConversionItem.java)、[UpgradeNextTierRecipe](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/crafting/UpgradeNextTierRecipe.java)。

## 下位版設計への引き継ぎ

公開APIの存在と上限・加工・simulate・転送・変換の一部の契約を確認できた。公開APIだけで全操作を満たせるとは扱わず、手動投入・転送の文脈・装着中変換に限定Mixinを使う方向で設計する。通常capabilityの投入経路も受入処理へ集約する。

次に決めるのは個数保存の形式と未解決IDの保持、画面／操作、フィルタ条件と初期値。これらは新しい設計判断であり、この調査で本番APIや保存componentを先回りして追加していない。Mixinは対象の固定版・メソッド・適用理由・解除条件を実装時にも残す。
