---
name: mod-release
description: 独立MODの配布JAR、依存宣言、クリーン導入、保存互換、リリース準備を扱う時に使う。
---

# mod-release

入力は配布版、変更内容、対象Minecraft／loader、公開範囲。出力は通常JAR、内容／取得可能性／クリーン導入の実測、変更履歴と保存互換の説明。

[開発手順](../mod-implement/references/development.md)と[検証記録](../../../docs/verification.md)を読み、check／verifyModJar／GameTestを確認する。通常JARにはcore・metadata・必要assets/data・LICENSEを含め、外部MOD本体・テスト・skills・資料を含めない。sources JARを通常JARとして扱わない。

通常JARと宣言した実行時依存だけで新規専用サーバーを起動し、client接続と変更したゲーム動作を確認する。保存変更はコピーしたワールドで検証し、破壊的な移行／削除の扱いを説明する。取得URL・版・hash・sideと公開範囲を記録する。公開・pushの既存承認を尊重し、承認なしの公開を追加しない。
