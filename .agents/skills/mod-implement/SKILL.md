---
name: mod-implement
description: 独立Kotlin MODの登録、加工、保存、通信、datagen、loader連携を実装・変更する時に使う。
---

# mod-implement

実装を始める時に[開発手順](references/development.md)を読む。

入力は受け入れ条件、design、対象版。出力は小さな実装diff、対応するテスト／generated resources、実測結果。

[規約](../mod-kotlin/references/coding-conventions.md)と開発手順に従い、対象版の公式資料／作者ソースを確認する。coreへItemStack等を渡さず、数値・ID・判断結果を渡す。ゲームの読み書きはloader側で行う。

加工はサーバー側で入力種類・個数・アクセス権・出力容量を全て確認してから確定する。素材不足／出力満杯／失敗では素材や保存状態を変更しない。事前確認と確定の間に状態が変わる場合は再検証し、simulateだけで原子性が得られると断定しない。失敗条件と成功条件を実際のテストで確認する。

datagenは入力を修正して再生成し、別Gradle呼出しでcheck／build／verifyModJarを行う。登録・recipe・ゲーム操作はGameTestで、client／専用サーバーの違いは起動・接続で確認する。未実施は[検証記録](../../../docs/verification.md)へ明記する。
