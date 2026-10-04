# Gem Case工程5: 入手経路・見た目・手動検収の設計案

状態: 2026-10-04の「対応願う」で設計承認。工程4は54b75f7でローカルmainへ統合済み。[実装計画](../plans/2026-10-04-gem-case-acquisition.md)を作成し、計画も「ok」で承認。レシピ・見た目・保存改善は実装／自動検証済み。実player操作は未検収、pack導入は別変更。

## 目的と区切り

探索中に下位の収納を入手し、進行後に装着した下位を上位化できる通常入手経路を用意する。既存の数量・フィルタ・材料を継承する変換を使い、保存schema・容量・加工コスト・固定依存は工程4のまま維持する。最終アイコンで下位・上位・変換itemを区別する。

工程5は、既存レビューの軽微な保存・decode改善、通常入手レシピとモデル、実clientでの収納・加工・変換検収、配布準備の順で進める。pack採用は合意済みだが、このrepoの実player検収が済んでからpack側の独立した変更として扱う。

## 入手経路の比較

| 案 | 利点 | 負担・制約 |
| --- | --- | --- |
| A: 下位recipe＋変換item recipe（推奨） | 工程4の装着中変換とraw継承を再利用できる。追加serializer不要 | 上位化には下位をバッグへ装着する |
| B: Aに下位→上位の通常craftも併設 | 未装着でも上位化できる | 入力の全component継承・Locked raw・材料や外来componentを別経路でも検証する必要がある |
| C: 作者のGem Case itemを変換材料にする | 作者itemとの関係が明快 | 中身入りGem Case itemのcomponentを消費して収納を失う危険があり、非空入力の拒否が必要 |

Aを推奨する。上位を新たに通常craftする経路は作らず、創造モード・管理コマンドでの直接入手は維持する。変換はcursorで下位の装着slotを右クリックする既存操作を使う。

## 推奨recipeの具体案

通常3×3 shaped recipe、出力はそれぞれ一個。NBT付きGemやGem Case収納itemを素材にしない。

### 下位 `gem_case_upgrade`

```text
GGG
DBD
DCD
```

- G: `c:glass_blocks` ×3
- D: `apotheosis:gem_dust` ×4
- B: `sophisticatedbackpacks:upgrade_base` ×1
- C: `minecraft:chest` ×1

Ender Chest不要で収納を先に解放する。4個のGem Dustを要求するため、探索で素材を得た後の入手を想定する。初期収納をもっと早く解放したい場合はGem Dust数を減らす調整余地があるが、本案の承認値は四個とし、後から調整する場合はrecipe変更として扱う。

### 変換 `gem_case_upgrade_conversion`

```text
GGG
BEB
BNU
```

- G: `c:glass_blocks` ×3
- B: `minecraft:basalt` ×3
- E: `apotheosis:gem_cutting_table` ×1
- N: `minecraft:ender_chest` ×1
- U: `sophisticatedbackpacks:upgrade_base` ×1

固定版の作者Gem Case recipe `GGG / BEB / BNB` のBasalt一個をUpgrade Baseへ置き換える。作者Gem Caseに近い段階で携帯加工を解放する。作者Gem Case自体を消費しないので、その収納データをcraftで捨てる別問題を増やさない。

実装時はvanillaのrecipe／advancementをdatagenし、同じIDのresourceを手編集しない。JEIで二recipeを表示し、通常craftによるitem入手と既存変換をGameTest・実操作で区別して確認する。

## 見た目と名称

現行の日本語／英語の名称とitem IDを維持する。16×16透過のMinecraft itemアイコン三種を用意する。下位は収納ケース、上位は同じ輪郭に加工工具または留め具、変換itemは強化部品として区別する。色だけに頼らずシルエットでも区別する。既存item/generatedモデルを使い、新しいrendererやclient専用登録を増やさない。

今の仮textureはrecipe確定後に置き換える。見た目の確認用画像は制作時に提示し、視認性を実clientのGUI scale別で検収する。高価な画像生成を先に行わず、小さいitem用の素材制作から進める。

## 既存レビューの軽微な改善

工程4の独立レビューでは未解決Critical／Importantなし。CodeRabbit再レビューで残した既存改善は次の二点。

1. Gem countsのclient summaryについて、NBT byte型と0/1、client_summary=1を厳密に検証する。不正値をReadyへ読み替えず原文保持のLockedとする。
2. transfer settingsの保存callback失敗時に、旧component原文／未存在を復元する既存単component保存境界を使う。通常slot、counts、材料は変更しない。

各変更は先行失敗GameTest後に最小修正し、独立レビューを挟む。view開閉metadataのquota免除は、現在menu・UUID・nonce検証とclose同期を確実にする設計を維持する。加工・補充設定は16要求/tickを維持する。

## 検証と配布条件

- `check`、datagenの追跡／未追跡差分、別Gradle呼出しのbuild／verifyModJar、必須GameTestの正の件数を記録する。生成再実行の一覧・hash一致も確認する。
- 下位・変換のcraft出力、recipe競合、材料数、advancementを確認する。中身入り下位・Locked原文・追加componentの変換保持とcursor消費の既存回帰を維持する。
- 対象版のJEIを開発限定で使い、日本語／英語、六材料slotの配置とnative click、設定から加工への切替、収納・転送・単発／Shift加工・補充・装着中変換を実playerで確認する。
- 通常JARと固定依存だけの専用serverへ認証済みclientを接続し、新規／コピーworldで装着バッグの保存再起動・設置回収・tier変更を確認する。権限・不足・満杯時の無変更も確認する。
- GUI実操作を実行できない環境では、起動やGameTestで合格にせず未検収を残す。現時点では認証済み接続と実player操作の検収は必要なままである。
- pack側への導入時は版・取得元・SHA256・sideをpack側manifestへ記録し、共存・再起動・Windowsを検収する。公開／pushの範囲はその時点のユーザー指示に従う。

## 固定一次資料と確認

Apotheosis 8.9.0配布JARの`data/apotheosis/recipe/gem_case.json`／`gem_cutting_table.json`、Backpacks 3.26.6.2174配布JARの`data/sophisticatedbackpacks/recipe/upgrade_base.json`を照合した。JAR hashは[対応構成](../../compatibility.md)の固定manifestと同じ。

- [作者Gem Case recipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/generated/resources/data/apotheosis/recipe/gem_case.json)
- [作者Gem Cutting Table recipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/generated/resources/data/apotheosis/recipe/gem_cutting_table.json)
- [固定版のUpgrade Base登録](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/init/ModItems.java)

自己確認: 二recipeは3×3の九入力で成立し、既存収納itemを消費しない。coreへゲーム型を持ち込まず、新しい保存形式・serializerは不要。recipeのbalanceと見た目の方針は承認済み、完成アイコンは原寸／整数倍の原稿レビュー済み、実clientのscale別表示と認証接続・pack共存は未検収として区別する。実装・自動検証の結果は検証記録へ残した。

独立書類レビュー: 実装担当とは別の履歴なしgpt-6-luna / highが二recipeの九入力、固定配布JARの材料・hash、componentを失うcraft経路の有無、未承認／未検収の区別を確認し、指摘なし。本番コードの実装レビューとは分ける。
