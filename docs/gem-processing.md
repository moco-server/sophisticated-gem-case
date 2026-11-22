# 上位Gem Caseの加工・上位化仕様

上位Gem Caseは[収納](gem-storage.md)・[回収と転送](gem-transfer.md)に加え、専用の素材6枠と純度強化を提供する。

## 素材と補充設定の保存

各素材slotは通常stack上限（最大64）を使う。現在の実行時レシピの左右いずれかの材料に合うstackを投入できる。レシピの変更で対象外になった既存stackは保持し、取り出せる。操作は中央画面の実slotを通し、公開IOやPickup／Deposit／Restockの搬送先からは隔離する。

| component | schema 1の内容 | 上限・初期値 |
| --- | --- | --- |
| `sophisticated_gem_case:gem_processing_materials` | `schema_version`（Int、値1）、`entries`（List）。各entryは `slot`（Int、0〜5）、`stack`（レジストリ対応ItemStack codecのCompound） | 一意なslotを最大6件。NBTは1 MiB・深さ16、各stackの永続エンコードは32 KiBまで。不在時は空 |
| `sophisticated_gem_case:gem_processing_settings` | `schema_version`（Int、値1）、`use_backpack_materials`（Byte、0または1） | 不在時は補充OFF |

未知schema・不明item・不正型・重複slot・超過などは原文を保持する。素材保存がロックされた場合はその編集と加工を、補充設定がロックされた場合はその編集と加工を止める。上位の素材保存が空と確認できない場合は通常の取り外し・交換を拒否する。正常なGem収納と専用転送への影響は各componentの状態で決まる。転送設定だけのロックは加工の拒否理由にしない。通常ItemStack同期には素材と補充の要約を送り、素材の詳細はmenuの6 slotで同期する。

## レシピと素材の選択

サーバーが `GemCuttingMenu.getRecipes(level)` で取得した `PurityUpgradeRecipe` を使う。加工一回につき専用収納の同じ種類・同じ純度の標準Gemを2個消費し、一段階上の標準Gemを1個、同じ専用収納に入れる。選べる出力純度はCHIPPED〜PERFECTの5段階。無効な種類・純度、入力不足、出力枠の容量不足では加工しない。

1. 補充設定にかかわらず、まず専用6枠だけで成立する案を探す。作者のレシピ順とslot順を使い、左右の材料は異なるslotから選ぶ。
2. 専用だけで成立せず補充がONなら、同じバッグの通常収納も使って再計画する。専用枠から先に取り、残る不足分を通常slotの昇順で補う。左右の要求は複数stackに分割できる。両方に適合するstackは右側の成立に必要な残量を確保し、同じ個数を二重に予約しない。プレイヤー、別バッグ、外部handlerの材料は対象外。
3. 探索予算は専用と補充の両方で共有し、最大512レシピ照会、4,096の左右候補組、16,384のIngredient判定とする。通常slot数が8,192を超えるhandlerでは補充を拒否する。上限内で確定可能な案だけを提示する。

## サーバーでの確定

サーバーはmenuとアクセス権、バッグUUID、装着現物、session・revision、補充設定、レシピ世代、各対象slotのitem・component・個数を確認する。同じバッグの処理に再入する変更を防ぎ、Gem・素材・通常slotの変更案を用意したうえで確定する。保存callback失敗や保存後の前提変更を検出したら、変更前のcomponent原文または未設定状態と、通常slotのsnapshotを復元する。復元に失敗した場合も試行を続け、元の失敗と合わせて報告する。

通常クリックは1回、Shiftクリックは最大64回。各回で再計画し、不成立になった時点で止める。すでに確定した回は保持する。レシピのreloadで古い加工previewを無効化し、現在のレシピを使って更新する。[中央画面の同期契約](design.md#中央gem-case画面)を参照。

## 装着中の上位化

`sophisticated_gem_case:gem_case_upgrade_conversion` をカーソルに持ち、装着済みの下位アップグレードのslotを右クリックする。サーバーはmenu、アクセス権、バッグ、現物、slot、上位の装着条件を確認する。旧itemのcomponentを引き継ぎ、Gemと転送設定はロックされた保存原文も引き継ぐ。下位に上位専用の素材・補充componentが付いている場合は変換を拒否する。

装着slotの置換・保存と整合性の確認に成功した後、変換アイテムを1個消費する。クリエイティブでは消費しない。成功時は上位の素材枠が空、補充がOFFとなり、新しいmenu sessionを使う。失敗時は旧slot・保存データ・カーソルを保つ。通常の入手経路は[入手レシピ](design.md#入手レシピ)を参照。

作者側の固定版レシピAPIは[連携調査](integration-research.md#gem識別と加工)、試験の実測は[検証記録](verification.md)を参照。
