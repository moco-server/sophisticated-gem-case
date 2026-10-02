---
name: mod-verify
description: 独立MODの変更を検収する、ビルド・起動・実動作を確認する、検証結果を記録する時に使う。
---

# mod-verify

入力は変更diffと受け入れ条件、実行可能な構成。出力は[verification](../../../docs/verification.md)へ記す版・手順・観測結果と未実施項目。

[開発手順](../mod-implement/references/development.md)のroot checkを基本に、変更に応じてdatagen差分、通常JAR内容検査、GameTest、専用サーバー起動・接続、実動作を確認する。テストを実行した結果を読み、未実行の成功を推測しない。GameTestが0件で終了した場合を検証済みとしない。

クライアント試遊では対象版のEMI／JEIを開発限定で導入し、取得版・URL・hash・sideを記録する。保存済みワールドを上書きしない。加工には成功だけでなく素材不足・出力満杯・権限不足時の無変更も確認する。GUI／認証／Java等の不足があれば実施済みチェックと未検証を区別する。
