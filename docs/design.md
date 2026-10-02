# Sophisticated Gem Caseの設計

## 目的と現在の状態

[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)を仕様の起点とし、ApotheosisのGem専用収納・管理・純度強化をSophisticated Backpacksへ組み込む。探索中の回収から種類／純度別の整理、拠点との転送、携帯加工をつなぐ。

このrepoで独立して保守する。Kotlin主体とし、MixinなどJavaとの接続に必要な箇所のみJavaを許容する。現在の本番機能は初期化済みのテンプレートサンプルだけで、Gemアップグレード・画面・保存・連携・加工は未実装。実行構成と対象JARの調査用GameTestを整備した。

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

## 依存境界と接続方針

coreはpure Kotlin/JVMとし、ID文字列・個数・条件・計算などゲームから独立する判断だけを置く。Minecraft、NeoForge、KFF、Apotheosis、Sophisticated系、関連MODの型は内部にも公開APIにも入れない。現在は数量サンプルだけで、保存モデルや外部操作の抽象APIを先回りして追加しない。

neoforgeはGemの識別とItemStack変換、登録、保存変換、実インベントリ操作、通信、画面、datagenを担当する。client専用コードはclient packageへ隔離する。公開APIを優先し、必要性を確認できた箇所だけ最小のMixinを採用する。連携MOD本体は通常JARへ同梱しない。

[連携調査](integration-research.md)には対象版の機能一覧、公開APIの限界と接続候補、実測と未確認を記録する。GemCaseTileの収納は上限超過分を返さないため、そのまま専用収納の実装に使わない。IInsertResponseUpgradeだけで全ての投入経路を捕捉できるとも扱わない。

## 工程と後続の設計事項

1. 初期化・公開依存固定・起動確認・連携調査。
2. 下位の収納、保存、一覧、一括移動、装着／取り外し制限。
3. Pickup／Deposit／Restockと専用設定・フィルタ。
4. 上位の加工、材料補充、装着中の上位化。
5. 素材・レシピ・見た目の仕上げ、通常JARとクライアント／専用サーバー、pack共存の検収。

最初の区切りの[実装計画](superpowers/plans/2026-10-02-gem-case-foundation.md)と[検証記録](verification.md)を参照する。依存版の正はcatalog／Gradle properties、公開JARの取得記録は[対応構成](compatibility.md)とする。

後続設計で保存形式・未解決Gem IDの保持と互換方針、フィルタ条件の組み合わせと初期値、画面、アップグレード／変換アイテムの名前・見た目・レシピを決める。通常クラフトによる上位化の併設可否と、材料候補・競合レシピの選択順もその段階で決める。packへの採用は合意済みだが、今回packを変更・導入しない。
