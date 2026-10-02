# Gem Caseアドオン開発基盤・連携調査 実装計画

> 実装担当は`superpowers:executing-plans`で順に実行する。最終差分は独立したサブエージェントがレビューする。

**Goal:** Issue #1の下位版設計へ進むため、識別情報・対象依存・再現可能な検証環境・主要な接続方法を確立する。

**Architecture:** pure KotlinのcoreとNeoForgeアダプタを維持する。サンプルは基盤検証用に残し、連携MODはneoforgeだけへ追加する。公開APIを優先し、実証された不足箇所に限ってMixinを候補とする。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.252、Java 21、既存catalogのKotlin／KFF／Gradle構成。

**Spec:** [現在の設計](../../design.md)、[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)、2026-10-02の対話で承認された最初の着手計画。

## 共通制約

- MOD IDは`sophisticated_gem_case`、表示名は`Sophisticated Gem Case`、packageは`org.mocoserver.sophisticatedgemcase`、groupは`org.mocoserver`、作者は`samunohito`。
- coreへゲーム・loader・連携MODの型を入れない。配布JARへ外部MOD本体・テスト・開発資料を入れない。
- 外部JAR・キャッシュ・起動環境・新規ワールドは`.local`へ置く。既存ワールドを上書きしない。
- 初期化・依存設定・調査が対象。収納や加工の本番機能、packへの導入は後続工程。
- ユーザーのコスト方針を優先し、実装は統括者、独立レビューは安価なモデルの新規contextで一回行う。

## レビュー重点

- 初期化後のnamespace・metadata・配布JARが一致するか。
- 必須依存と開発限定JEIを区別し、client限定依存がサーバーへ入らないか。
- 取得したJARと調査ソースの差異、未検証事項を隠していないか。
- core境界と通常JAR検査が依存追加後も有効か。
- 起動成功、GameTest成功、Gem機能の実操作を混同していないか。

## Task 1: 初期化と対象MOD文書

対象: Gradle properties、初期化scriptの対象ソース／resource、README、docs/design.md。

- [x] 既存scriptテスト29件を確認し、初期化dry-runで変更対象を確認する。
- [x] 承認された識別情報で初期化する。サンプル登録・生成入力・テストは保持する。
- [x] 対象MODの設計と工程を文書化し、テンプレートの検証実績を由来付きで保存する。
- [x] 初期化後のscriptテストと`./gradlew check`を実行する。

## Task 2: 固定依存と起動環境

対象: catalog、neoforge/build.gradle.kts、metadata、core/build.gradle.kts、docs/compatibility.md。

- [x] Apotheosis 8.9.0、Backpacks 3.26.6.2174、Core 1.5.2.2343の公開JARと作者指定の関連依存を取得・hash照合する。
- [x] neoforgeへ固定依存を追加し、coreの禁止prefixを拡張する。JEI 19.51.0.418はclient run専用とする。
- [x] metadataに直接の必須MODと検証した版を宣言する。取得元・SHA256・sideを記録する。
- [x] datagen後に別呼び出しでcheck・build・verifyModJar・GameTestを実行し、再生成差分を確認する。
- [x] 新規環境で開発サーバーと通常JAR専用サーバーの起動を確認する。対話的clientは実施状況を区別して記録する。

## Task 3: 接続方法の調査と独立レビュー

対象: docs/integration-research.md、必要なGameTestプローブ、docs/verification.md。

- [x] Gem Caseの機能・Gem識別・加工コスト・レシピ選択を対象ソースから列挙する。
- [x] 手動投入／Pickup、Deposit／Restock、取り外し／装着中上位化の接続経路を調査する。
- [x] 不明な公開APIの挙動は最小のGameTestで確認する。採用方法・一次資料・制約・未検証を一覧にする。
- [x] 固定構成・コマンド・実測・未実施を検証記録へ残す。
- [x] 独立したサブエージェントへ最終diffと受け入れ条件を渡し、重要指摘を修正・再検証する。

## 実行上の判断

- 作業は新しい`feat/gem-case-foundation`ブランチで行う。生成直後のクリーンな専用repoで、実装担当は一人のため追加worktreeは作らない。
- 名前変更・設定・文書には既存の検証を使い、同じ設定を写すだけのテストは増やさない。挙動を調査するGameTestには実際の外部APIを使う。
- 通常JAR起動は既存の受諾済みEULAとloader librariesを読み取り／コピーして新規環境を作り、既存サーバー・ワールドは変更しない。

## 実測台帳

- 開始commit: `d63bb18`。初期scriptテスト: 29件成功。
- 残りの結果は[検証記録](../../verification.md)に記録する。
