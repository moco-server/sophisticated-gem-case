# Sophisticated Gem Caseの設計

## 目的と現在の状態

[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)を仕様の起点とし、ApotheosisのGem専用収納・管理・純度強化をSophisticated Backpacksへ組み込む。目標は探索中の回収から種類／純度別の整理、拠点との転送、携帯加工をつなぐこと。

このrepoで独立して保守する。Kotlin主体とし、MixinなどJavaとの接続に必要な箇所のみJavaを許容する。下位Gem Caseアップグレード、専用保存、画面操作を実装した。Pickup／Deposit／Restock連携と専用の転送設定・フィルタも実装した。上位加工・専用材料枠・装着中の上位化も実装し、自動検証と起動・保存往復を確認した。GUIの操作は一部を検証用の入力イベントで確認した。手動検収と認証接続の状況は[検証記録](verification.md)を参照。

MOD IDは`sophisticated_gem_case`、表示名は`Sophisticated Gem Case`、packageは`org.mocoserver.sophisticatedgemcase`、Maven groupは`org.mocoserver`、作者は`samunohito`。ライセンスはMITを維持する。

## 合意済みの機能と不変条件

- 下位はGem専用収納、種類／純度別一覧、投入・取出、通常収納からの一括移動、既存アップグレードとの連携を提供する。上位はこれに対象版の通常Gem Caseの全機能を加える。
- 下位と上位はバッグ一つにつき合わせて一個まで。容量は種類×純度ごとに32,767個。通常スロットと別枠で、Stackアップグレードやバッグtierの倍率を受けない。
- バッグへのGem受け入れは専用収納を優先する。超過分は通常収納へ送り、さらに入らない分は投入元へ残す／返す。通常収納からの一括移動も入る分だけ移す。
- 自動回収はPickupとの併用時だけ。一括収納はDeposit、一括取出はRestockとの併用時だけ提供する。
- 専用収納のDeposit／Restockは独立したON／OFFと、独立して保存する種類／純度の対象・除外条件を持つ。既存フィルタとの二重一致を要求しない。通常収納に対する既存操作は既存フィルタに従う。
- 上位の加工は対象版のGem Caseのレシピと必要量を使う。専用材料枠6枠を優先し、設定を有効にした場合だけ不足分を同じバッグの通常収納から補う。プレイヤー、別バッグ、外部収納は使わない。
- 加工・転送・保存更新はサーバーでアクセス権・入力・出力・個数を検証して確定する。不成立時に素材・Gem・出力を部分変更しない。
- Gemまたは専用材料枠に中身がある間は取り外せない。装着中の下位版へ変換アイテムを使って上位化し、個数・フィルタ・連携設定を継承する。条件確認と継承成功後に置換と一個の消費を確定し、失敗時は元の全状態を維持する。
- 再起動、バッグの設置と回収、バッグtier変更で保存データを維持する。Ender Gem Caseの共有・大容量機能は通常Gem Case相当の要件に含めない。

## 機能別の仕様

- [Gem専用収納](gem-storage.md)は、標準Gemの判定、投入・取出、装着と取り外し、数量保存のschemaと互換性を定める。
- [回収・転送](gem-transfer.md)は、Pickup／Deposit／Restockの接続、フィルタ、外部収納との数量確定と設定保存を定める。
- [上位Gem Caseの加工・上位化](gem-processing.md)は、専用素材枠、レシピの選択と加工の確定、装着中の変換と保存の継承を定める。

## 入手レシピ

通常クラフトは次の3×3 shaped recipe二つで、各1個を出力する。Gは両方ともアイテムタグ `c:glass_blocks`（3枠）。

| 出力ID | 上段／中段／下段 | G以外の素材 |
| --- | --- | --- |
| `sophisticated_gem_case:gem_case_upgrade` | `GGG / DBD / DCD` | D = `apotheosis:gem_dust` ×4、B = `sophisticatedbackpacks:upgrade_base`、C = `minecraft:chest` |
| `sophisticated_gem_case:gem_case_upgrade_conversion` | `GGG / BEB / BNU` | B = `minecraft:basalt` ×3、E = `apotheosis:gem_cutting_table`、N = `minecraft:ender_chest`、U = `sophisticatedbackpacks:upgrade_base` |

下位はEnder Chestが不要で、探索でGem Dustを得た段階で開放する。変換はApotheosis 8.9.0のGem CaseレシピのBasalt一つをUpgrade Baseに置き換え、作者のGem Caseと近い段階で携帯加工を開放する。どちらのレシピも既存の収納アイテムを材料に含めない。上位のサバイバルでの入手は[装着中の上位化](gem-processing.md#装着中の上位化)で行う。[Apotheosis 8.9.0のGem Caseレシピ](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/generated/resources/data/apotheosis/recipe/gem_case.json)

datagenでレシピと解除用advancementを生成する。解除条件は下位がGem Dust、変換がGem Cutting Tableの入手。実クラフト・JEI表示の手順は[クライアント検収](client-acceptance.md#gem-case工程5の試遊手順)を参照。

## 中央Gem Case画面

右側の21×24 GUI pxのアイコンから、既存バッグmenu上へ232×230 GUI pxの中央表示を開く。バッグの幅と独立した寸法とし、320×240のGUI座標に収める。入口には展開パネルがなく、保存済みの `open_tab_id` は保持したまま旧タブの展開を復元しない。固定Coreの `StorageScreenBase.setModalOverlay` に接続する。[Sophisticated Core 1.5.2.2343のStorageScreenBase](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase.java)

### 一覧・取出・加工

- 種類の翻訳名とIDで検索する。右クリックで検索欄を消去できる。検索編集中のインベントリキーは背面のmenuへ渡さない。
- 登録された種類を6列×3行のスクロール一覧に表示する。在庫ありを先にし、在庫の有無ごとにID順。種類を選ぶと純度6段階と保存数を表示する。最低純度より下の段階を無効にし、在庫0は暗く表示する。未解決データは警告表示からIDと数量を確認できる。
- 純度欄の取出とカーソル投入、一括移動は[収納仕様](gem-storage.md#画面とサーバー確定)に従う。上位だけに素材2列×3行、各出力純度に対応する強化ボタン5個、通常収納からの素材補充設定を表示する。下部にはプレイヤーinventoryとhotbarを置く。
- 強化の有効状態と必要素材はサーバーが計算する。Gem不足、素材不足、出力容量、同期中、保存ロックを区別して扱う。通常クリックで1回、Shiftクリックで最大64回処理する。

### 同期と入力の契約

同じ種類の最大5出力のpreviewは、menu・slot・種類・選択連番・session・revisionに紐づける。レシピのreloadでrevisionを更新する。一覧のpreview全体で加工の探索予算と32 KiBの通信予算を共有する。加工要求は出力純度を指定し、対応するpreviewと現在のレシピ、数量、素材をサーバーで再検証する。保存を伴う加工・補充設定は既存の16要求／tickの上限を共有する。client／serverは通信protocol 2の同じ本体版を使う。

素材編集はGem選択や強化可否から独立した許可とし、無選択でも正常に同期した素材を操作できる。左右クリックとShift移動は既存の実slotを論理indexで操作し、ネイティブslotの座標は動かさない。素材へのdrag、数字キー、double clickは拒否する。通常player slotのdragはrelease時に確定する。

設定ページでは素材操作を停止し、収納ページへ戻ると検索・選択・スクロールを維持する。戻る・Escape・外側クリックは中央表示を閉じ、バッグmenuとカーソルを維持する。外側クリックとそのreleaseを背面へ渡さない。閉じる操作、resizeによる再初期化、アップグレード差替え、menuの無効化・切断で素材の表示許可を解除する。描画中に無効化を検出した場合は描画後に閉じる。起動・操作の確認状況は[検証記録](verification.md)を参照。

## 依存境界と接続方針

coreはpure Kotlin/JVMとし、ID文字列・個数・条件・計算などゲームから独立する判断だけを置く。Minecraft、NeoForge、KFF、Apotheosis、Sophisticated系、関連MODの型は内部にも公開APIにも入れない。Gemの種類／純度キー、上限付き不変個数モデル、転送フィルタと設定、加工の数量計画と材料配分を持つ。ゲーム依存の保存変換や外部操作の抽象APIはcoreへ置かない。

neoforgeはGemの識別とItemStack変換、登録、保存変換、実インベントリ操作、通信、画面、datagenを担当する。client専用コードはclient packageへ隔離する。公開APIを優先し、必要性を確認できた箇所だけ最小のMixinを採用する。連携MOD本体は通常JARへ同梱しない。

[連携調査](integration-research.md)には対象版の機能一覧、公開APIの限界と接続候補、実測と未確認を記録する。GemCaseTileの収納は上限超過分を返さないため、そのまま専用収納の実装に使わない。IInsertResponseUpgradeだけで全ての投入経路を捕捉できるとも扱わない。

## 転送設定の表示と操作

### 在庫表示・転送設定の操作部品

在庫0の種類と純度はアイテムの手前へ暗い半透明の覆いを描き、種類選択は引き続き可能とする。右側メニューはホバーで名前とShiftの案内を表示し、Shift中は操作内容・対象・前提・具体例を、必要な長さの段落で説明する。カーソルにアイテムを持っていてもメニュー説明を表示する。

転送設定では、見出しと項目名を通常文字、戻る・方向・ON/OFF・方式・選択軸・一覧の選択をMinecraft標準ボタン、検索を枠付きEditBoxに統一する。方向は日本語では搬出／補充とし、ホバーでDeposit／Restockとの関係を説明する。検索と一覧スクロールは表示だけに作用する。同期前やロック中は保存変更ボタンを無効にし、サーバーで再検証する既存の設定変更経路を使う。

画面上の用語は「リュック」に揃え、マウスで持ったジェムは「ジェムをしまう」、中身からの移動は「リュックのジェムを移動」と表記する。Shiftの案内は青文字・斜体にする。復元不能な保存項目は「読み込めないジェム」と呼び、種類・ランクの記録と個数を保持していることをホバーで伝える。

### 転送設定のアイコン選択

転送設定に入った間だけoverlayを横300×縦230へ広げ、戻ると収納画面の横232へ戻して中央に配置する。高さは維持し、320×240のGUIにも収める。転送方向は搬出／補充、方式は「すべてのジェム」「選んだジェムだけ」「選んだジェムを除く」。下段の見出しに方向と方式の組合せを表示する。

下段は種類／ランクのタブを置き、種類は10列×2行のアイコンボタン、ランクは名前を添えた6列×1行のボタン。金枠とチェックが保存済み選択を示す。在庫数は条件設定に影響させない。方式ALLでは選択を保持したままボタンを無効化し、下の選択が適用されない旨を明示する。種類は選択によって並び替えず、ランクは低→高に並べる。ランクのアイコンは共通のジェムを見本に使い、すべての種類に適用される旨を説明する。保存された未知の項目もバリアアイコンで残し、選択解除できる。

検索と矢印・ホイールは表示だけを変える。各操作とアイコンには青文字・斜体のShift案内を付け、Shift中は方向、既存アップグレードの必要性、両軸の組合せ、空選択の扱いを説明する。転送判定・保存・通信は既存仕様を維持する。

詳細説明は`GemHelpTranslations.kt`の日本語・英語の段落として管理し、タイトル＋固定2行の構造を廃止する。画面幅に合わせて最大300pxで折り返し、段落や文字書式を保って末尾まで描画する。転送条件の組合せ・空選択・検索やページ外の選択の保持・手動収納への非適用、素材の参照先と消費時点を説明する。

## 結晶マークを使うアップグレードのアイコン

採用した幅広い縦長六角形を、三itemの共通マークにする。下位は濃茶の単色シルエットだけ。中心は(7.5,7.5)、bboxは(4,3)〜(11,12)で、上下左右に対称な原稿をそのまま使用する。上位には既存の上位アップグレードに合わせた金色の四隅、変換itemには右下の小さな上向き印を付ける。結晶の画素は三種とも共通に保つ。

item/generatedのlayer0から元MODの`sophisticatedbackpacks:item/upgrade_base`を参照し、layer1へ自作の透過PNGを重ねる。基板画像は本アドオンへ複製しない。元MODのresource packを変更した場合も基板は同じ参照先を使う。datagenは`--existing-mod sophisticatedbackpacks`で依存MODのresourceも参照検証する。
