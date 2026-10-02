---
name: mod-kotlin
description: この独立MODでKotlinソース、Gradle Kotlin DSL、Java相互運用、var・null・可視性・KDocの問題を扱う時に使う。
---

# mod-kotlin

Kotlinの変更時に[規約](references/coding-conventions.md)を読む。

入力は変更するKotlinコードと対象module。出力は規約に従うdiffと解析結果。

valを既定とし、実際に再代入する局所状態だけvarにする。valのmutable collectionは不変ではないため所有権を確認する。Java platform typeを公開する場合は明示的な非null／nullable型と境界検証を付ける。!!で契約を隠さない。

coreはexplicit APIモード。公開class／interface／関数／propertyにはpublicと必要な型を明記する。ゲーム型は内部にも入れない。公開APIの日本語KDocには役割・失敗条件・変更／スレッド契約を該当時に書く。

[開発手順](../mod-implement/references/development.md)のktlintFormatで明示整形し、root checkで型解決付きdetekt・compiler warnings・境界を検査する。違反を一括抑制／baselineで隠さず修正する。Java併用が必要なら理由を記録する。
