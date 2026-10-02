# 工程3: Pickup／Deposit／Restock連携の設計案

状態: ユーザー承認済み（2026-10-03）。工程2はmainの213818fへ統合済み。本書の本番コードは未実装。[実装計画](../plans/2026-10-03-gem-case-integration.md)を作成した。

## 目的と範囲

[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)の「探索中の回収と拠点への転送」を、工程2の下位収納へ接続する。Gemアップグレード単独には自動回収・Deposit・Restockの能力を付けず、対応する既存アップグレードの装着と有効状態を必要とする。種類×純度ごと32,767個、標準Gemだけを専用収納へ入れる契約を維持する。

上位加工、専用材料枠、上位化、recipeと最終textureは後続工程とする。通常JARの依存版は変更しない。

## 採用案と代案

公開IInventoryWrapperUpgradeで処理用inventoryの投入だけを包み、Pickupからも専用→通常→余りへ振り分ける。Deposit／Restockは固定版wrapperの個別メソッドへ限定したMixinで専用収納の転送を加える。

専用収納を仮想的な通常slotとして列挙する案は採らない。通常の並べ替え・抽出・フィルタ・Stack倍率へ専用保存を巻き込み、既存フィルタとの二重一致も起きるためである。Gem側に独自の回収能力を付ける案も、対応アップグレードの併用という要件に合わない。限定Mixinには対象版の追随が必要だが、実際の操作境界と通常転送を維持できる。

## 回収

Pickupが既存フィルタで受け入れた標準Gemを、処理用handlerでも専用収納へ優先する。専用満杯なら通常収納へ、両方満杯ならItemEntity／投入元へ余りを残す。simulateでは数量・保存callback・entityを変更しない。

Gem単独ではPickupインターフェースを実装しない。通常slotやtracked stackの列挙・extractは増やさず、加工系の入力／出力にも仮想Gem slotを見せない。工程2の公開IOは同じ専用受入処理を再利用する。公開IOの一操作では、そのバッグの「専用へ配分済み」文脈を下流の処理用handlerにも引き継ぎ、残量を二度専用へ振り分けない。simulateにも同じ文脈を使う。例えば残容量1／入力3で、保存を変えないouterとinnerがそれぞれ1個ずつ受入予定とする過大計算を防ぐ。文脈はバッグ単位に限定し、finallyで解除する。

## 専用転送の設定とフィルタ

Deposit対象／Restock対象の二つを独立して保存する。**初期値は両方OFFを提案する**。既存バッグへ装着しただけで専用Gemが外部へ出たり、外部収納から移ったりすることを避けるためである。ONを選べばその場で利用できる。初期ONなら初回操作が少ない反面、既存の一括操作が新たなGemにも及ぶ。

各方向のフィルタは「すべて／指定を対象／指定を除外」の三方式。種類ID集合と純度集合を別々に持ち、空の集合はその軸を制限しない。両集合が空の場合、すべては全対象、指定を対象は対象なし、指定を除外は全対象とする。両軸を指定した場合は種類と純度の両方に一致するGemが指定条件に当たる。初期方式はすべて。この方式では指定集合を判定に使わず、方式を切り替えても選択済みの集合は保持する。

種類IDは登録済みGemから検索・選択できる。保存済みだが未解決のID／純度も表示・保持し、勝手に削除しない。これらからstackを復元して転送することはしない。フィルタは専用保存の全種類に適用でき、通常のアイテムフィルタ枠へGemを一種類ずつ登録する必要を作らない。

種類・純度のAND条件と、複数の「種類×純度」ルールをORで列挙する方式には表現力の差がある。初期実装は前者を提案し、個別ペアの列挙やタグ・属性条件は追加しない。

## DepositとRestock

専用Gemの転送条件はGem側だけで判定する。既存Deposit／Restockのアイテムフィルタを追加で要求しない。通常収納の既存転送は従来のフィルタに従う。

Restockの通常転送中は、そのバッグに限って専用への自動振り分けを抑止する。専用対象OFF／除外でも通常Restock経由で専用へ入る抜け道を防ぐためである。操作文脈は例外時もfinallyで解除し、他バッグや次の操作へ漏らさない。その後、専用対象ONの場合だけ独立したGem転送を行う。

処理順は通常転送、専用転送。専用分は種類ID・純度の固定順で小さなstack単位に処理し、移動したstackだけを既存の返却リストへ含める。途中で満杯になった場合、移動できた分は残し、未移動分を元へ保持する。バッチ全体を外部handlerから巻き戻す方式は採らない。

## 保存・通信・失敗条件

Gem個数のschema 1は変えず、設定はupgrade上の別componentへ保存する。`gem_transfer_settings`のschema 1は、versionとDeposit／Restockそれぞれのenabled、mode、種類ID集合、純度集合を持つ。種類と純度の文字列は各256 UTF-8 byte、二方向の集合要素は合計4,096件、NBTは1MiB・深さ8を上限とする。固定順で保存し、工程2と同じboundedな読取りを使う。componentがない新規／従来upgradeは正常な初期状態（両方向OFF、modeすべて、空集合）として読む。不明schema／破損設定は原文を保持し、自動転送と設定上書きを停止する。正常な個数保存の手動操作は維持する。

永続codecは原文を保持するが、通常ItemStackのnetwork codecはロック状態のboolean要約だけを送る。persistent codecの既定流用は使わず、ID／純度集合やraw NBTを通常slot同期に載せない。client要約をserverで受信しても設定の保存sourceにせず、変更不可として扱う。実際の設定値は専用ページだけから表示する。

設定編集も現在menu、バッグUUID、装着slot、server発行sessionとrevisionを確認する。raw NBTや完成ItemStackを要求に含めない。設定の表示も64 entry／32KiB以内のページに区切る。編集は一つのON／OFF・方式・種類ID・純度を変更する型付き要求とし、巨大な設定全体をclientから送り返さない。設定変更も工程2の一覧世代に反映する。client型はclient packageから出さない。

確定はserver threadで行う。転送前に対応upgradeの装着／有効状態、元個数、標準stack、フィルタ、handlerの可否を確認する。simulateだけで外部との原子性を保証したとは扱わず、実insert／extractの受入量と余りから数量を確定する。再入するcallbackから同じ専用保存を変更しないよう、バッグと操作に限定した再入防止を設ける。

未知・ロック保存、未装着／無効upgrade、除外、受入0、古い要求では専用数量を変更しない。部分転送では専用・通常・外部・余りの合計を維持する。外部handler自体が契約に反して実状態を変更してから例外を投げる場合まで、完全に巻き戻せるとは約束しない。対象APIの実測と検証可能な契約を実装計画に残す。

## 受け入れ条件

- Gem単独・Pickup未装着／無効では自動回収しない。Pickup既存フィルタの拒否を迂回せず、専用に空きがあれば通常収納満杯でも回収する。simulateと容量超過の合計数量を検査する。公開IOと処理用wrapperの両方を通る場合も、専用残容量1／入力3のsimulateと実投入が同じ余りになることを確認する。
- Deposit／Restockの装着・有効状態と二つのON／OFFを全組合せで確認する。専用と通常に同じGemがある場合も重複しない。
- Gemの対象・除外と既存アイテムフィルタを逆にしたケースで、専用と通常に別々の判定が掛かることを確認する。
- 外部満杯・部分受入・extract拒否・再入callback・設定ロック・途中の状態変更で、数量保存と文脈解除を検査する。合計数量を変更前後で比較する。
- 設定の永続往復、初期値、未知ID／純度の保持、破損と将来schema、旧revision／再送／閉鎖／装着差替えを検査する。
- root check、datagen再生成差分、通常JAR内容、既存31件と新GameTest、通常JAR専用server・JEI client起動を確認し、対話実操作は実施と未実施を分けて記録する。
- 独立した安価なサブエージェントのレビューを挟み、検証後にコミットとmainへのローカル統合を行う。

## 固定APIの根拠

[既存調査](../../integration-research.md)の公開JARプローブと、Minecraft 1.21.1／Core 1.5.2.2343／Backpacks 3.26.6.2174のsource・javapを起点にする。登録済み調査用GameTestでPickup満杯経路とDeposit／Restock個別メソッドの往復は確認済みだが、本工程の専用転送は未実装。

- [PickupUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/upgrades/pickup/PickupUpgradeWrapper.java)／[InventoryModificationHandler](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/backpack/wrapper/InventoryModificationHandler.java)。
- [DepositUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/upgrades/deposit/DepositUpgradeWrapper.java)／[RestockUpgradeWrapper](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/upgrades/restock/RestockUpgradeWrapper.java)。対象sourceでは両メソッドはList<ItemStack>を返し、通常転送のinventoryに既存FilterLogicを掛ける。

公開APIだけで保持できる契約を優先する。Mixinのdescriptorと対象JARの同一signatureを実装前に再確認し、必要な接続だけを追加する。安価な委任とまとめた検証を使い、同じ成功済みビルドを手順上の都合だけで繰り返さない。


## 独立設計レビュー

2026-10-03、実装担当とは別のgpt-6-lunaへ本書、Issueの合意、固定sourceと既存調査を渡した。公開IO／処理用wrapperの二重simulateを防ぐ文脈とテスト条件を確認した。設定の通常slot同期へのraw流出と、component不在時の初期状態についてImportantの補足指摘があり、上記にnetwork boolean要約の分離とabsent時の正常初期値を追記した。同担当が追記を再確認し、両指摘の解消を確認した。本工程のコード・起動・実操作は未実施である。
