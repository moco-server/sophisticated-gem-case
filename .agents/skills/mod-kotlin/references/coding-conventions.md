# コーディング規約

[Kotlin公式規約](https://kotlinlang.org/docs/coding-conventions.html)を基本とする。コメント・KDoc・開発文書は日本語、識別子は英語、ゲーム翻訳はja_jp／en_us。

| 対象 | 規約 | 検査 |
| --- | --- | --- |
| 再代入 | valを既定とし、再代入する時だけvar | 型解決付きdetektのVarCouldBeVal |
| null | nullableを型で表現し、通常コードで!!を使わない。Java境界はrequireNotNull等で契約を確認 | UnsafeCallOnNullableType、レビュー |
| 公開API | 最小の可視性、core公開APIの可視性・戻り型を明示 | explicit API、レビュー |
| 整形 | UTF-8、LF、4 spaces、公式Kotlinスタイル | .editorconfig、ktlint |
| 警告 | compiler warningsをエラーにする | Kotlin compiler |
| コレクション | 読み取り専用型を公開し、可変実装と所有権を局所化する | レビュー。valやread-only型だけで深い不変性は保証しない |
| 型 | 不正状態を減らす値型・enumを必要に応じて使う。boolean列や無目的なラッパーを増やさない | レビュー |
| 例外 | 回復可能な失敗と契約違反を区別し、握り潰さない | detekt、失敗条件テスト |
| スレッド | ゲーム状態は対象loaderの正しいスレッドで変更する | 対象版API、GameTest、サーバー検証 |
| 表現 | scope関数・拡張・長いチェーンより読みやすさを優先する | detekt、レビュー |
| 抑制 | 最小範囲へ具体的な理由を添える。全体の警告無効化やbaselineで隠さない | レビュー |
| Java | 必要な理由を残す。再代入しないローカル変数はfinalを基本とする | レビュー |

コメントは理由・制約・不変条件を書く。処理を言い直すコメントや自明なprivate関数の形式的KDocは不要。公開APIのKDocには役割と契約を書き、該当する場合は失敗条件・状態変更・スレッド・単位を明記する。版固有の回避策には対象版・一次資料URL・解除条件を残す。TODOにはIssueか具体的な解消条件を結び付ける。生成物は入力と生成手順を記録し、手編集しない。

`./gradlew check`は整形検査・型解決付き解析・テストを実行する。整形修正は明示的に`./gradlew ktlintFormat`を実行する。detektのformatting拡張は使用しない。警告や違反を修正後、再度checkを実行する。

解析の対象版は[互換性記録](../../../../docs/compatibility.md)。[detekt 1.23.8のnull規則](https://detekt.dev/docs/1.23.8/rules/potential-bugs/#unsafecallonnullabletype)を使用する。

`checkCoreBoundary`はcoreの本番compile/runtime classpathのJAR・ディレクトリと本番bytecodeを検査する。推移依存・signature・annotation・命令参照も対象。連携先の禁止prefixはcoreのtaskへ追加する。解析用ASMはbuildSrc限定。buildSrcの整形・単体テストもroot checkのcheckBuildLogicで実行する。
