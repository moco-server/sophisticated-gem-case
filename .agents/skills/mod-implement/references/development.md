# 開発手順

JAVA_HOMEはJava 21のJDKへ指定する。Gradle Wrapper 8.12.1を使い、ホストの別版JavaやGradleを無条件に流用しない。必要ならGRADLE_USER_HOMEをrepo内.local/gradleへ指定する。

初期化はREADMEのscriptで対象ファイルを確認して実行する。ID／package／group／作者／repo URLを明示し、生成namespaceとmetadataを揃える。docs/design.mdはそのMODの現在の設計へ更新する。coreへゲーム型を入れず、必要な外部操作だけインターフェース化する。

| 目的 | コマンド |
| --- | --- |
| 整形を修正 | ./gradlew ktlintFormat、および ./gradlew -p buildSrc ktlintFormat |
| 品質・単体テスト・core境界・buildSrc | ./gradlew check |
| データ生成 | ./gradlew :neoforge:runData |
| 通常JAR・内容検査 | ./gradlew :neoforge:build :neoforge:verifyModJar |
| ゲーム内の自動テスト | ./gradlew :neoforge:runGameTestServer |
| 開発クライアント／サーバー | ./gradlew :neoforge:runClient ／ :neoforge:runServer |
| scriptテスト | node --test scripts/test/*.test.mjs |

WindowsのGradleはgradlew.batを使う。Bashのskills導入はGit Bash／WSLで行う。GameTestはgametest source set、通常JARには含めない。別MOD扱いのfixtureと本番で同じpackageを公開するとJPMSが衝突するため、テスト専用packageを使う。

datagen後は別Gradle呼出しでbuildを実行する。neoforge/src/generated/resourcesはGit管理し、git status --porcelain --untracked-files=all -- neoforge/src/generated/resourcesで追跡・未追跡双方の差分を確認する。生成物やmetadata展開結果を手編集しない。

外部skillsは任意の開発準備。依存の導入は次を使う。

```bash
pnpm --config.stateDir=.local/pnpm-state install --frozen-lockfile --ignore-scripts
bash scripts/install-skills.sh --dry-run
bash scripts/install-skills.sh
```

pnpm 12のcache/store設定はpnpm-workspace.yaml、stateDirはCLIで.localへ指定する。skills CLIも同じ場所を明示する。グローバルskillsや共通設定は変更しない。

対話的クライアントには対象版のEMI／JEIを開発環境限定で用意し、版・取得元・hash・導入側を記録する。MOD本体やヘッドレスGameTestの必須依存にはしない。保存済みワールドを使わず、.localの新規ワールド／コピーを使う。通常JARのクリーン起動と実クラフトを開発起動とは別に確認する。

委任する場合は目的・対象ファイル・受け入れ条件・必須task・保存／side制約を渡す。モデル名や常時並列を指定せず、統括者はdiffと検証結果から検収する。
