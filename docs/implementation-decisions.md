# 初版実装の判断と検証範囲

承認済みの設計・計画に従って初版0.1.0を実装した。実装中の判断を以下に残す。今後のMOD保守は生成先repo、テンプレート保守はこのrepoで行う。

| 判断 | 根拠 | 見直す場合の範囲 |
| --- | --- | --- |
| 新規独立Gitのfeature branchで実装し、linked worktreeを作らない | 既存MOD／packソースを変更しない独立配置 | 新規repoの配置を移す |
| core数量関数とテストをTask 4からTask 1へ先行 | 捨てるだけのテストを作らず、最小構成に実際の契約を持たせる | 導入commitの分割を見直す |
| Kotlin 2.0.21、KFF 5.5.0、Gradle 8.12.1、detekt 1.23.8を固定 | metadataの必要版と型解決付き検査を実測 | compiler／runtimeを更新しクリーン検証 |
| サンプルrecipeへ紙1個を追加 | 鉄1個だけではバニラ鉄塊recipeに一致することをGameTestで検出 | recipeとGameTestの入力を変更 |
| datagenの次に別Gradle呼出しでbuildする | 同じ呼出しでprocessResourcesが先に実行される場合がある | 呼出しを1回追加するだけ |
| pnpm-workspace.yamlとCLI stateDirを使う | pnpm 12は旧.npmrc配置を利用しない | 開発cacheの配置を変更 |
| 自作skill評価だけを2つの読取り専用agentへ分ける | writing-skills手順に従いbaseline／適用後の明示的public APIを比較 | 指示を修正して同じ課題を再試行 |
| lockをcore／buildSrcの安定classpathへ限定する | ModDevGradle内部／OS-nativeは別の検証が必要。全artifact checksum verificationは初版対象外 | lock／verificationを広げLinux／Windowsで再検証 |
| 最終レビューとremote検証を並行する | コードが揃った後の独立した読取りと実行 | レビュー後のコード修正は失敗する回帰と全検証で確認 |
| 最終Important 5件を修正する | 初期化済みfixture、Node cache、skills出力保護、文字列置換、Windows launcherの不具合を確認 | fixture／保護／launcherの回帰と両CIを再検証 |

最終レビューは1回、Critical 0／Important 5／Minor 0。Importantは受け入れて修正し、追加の実測でpnpm内部リンクとpnpm 12の.mjs配置も回帰へ加えた。見送ったMinorはない。

pnpm cache／store／stateの内部リンクは使用可能とし、3つの出力root外へ向くリンクを拒否する。Windowsの実skills導入は任意のworkflow_dispatch入力verify-skillsで行い、通常のpush／PR品質検査を外部skills導入に依存させない。

認証済みクライアントでの表示・接続・実クラフトはユーザー検収待ち。[検証記録](verification.md)と[検収手順](client-acceptance.md)を参照。Fabricへの実移植、既存亜鉛MODの移行、Gemアドオンの実装は今回の範囲外。
