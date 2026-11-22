# Sophisticated Gem Case

ApotheosisのGem CaseをSophisticated Backpacksへ組み込むKotlinアドオン。Minecraft 1.21.1／NeoForge 21.1.252／Java 21向けに開発する。作者: samunohito、ソースライセンス: MIT。

[Issue #1](https://github.com/moco-server/sophisticated-gem-case/issues/1)に基づき開発中。Gem Case下位アップグレードとGem専用収納を実装し、創造モードから入手して操作する下位収納と、Pickup／Deposit／Restock連携・転送設定を実装した。専用Deposit／Restockは初期OFFで、対応する既存アップグレードとの併用が必要。上位版の加工・6材料枠・通常収納からの材料補充と、変換アイテムによる装着中の上位化も実装した。自動検証と起動・保存往復は確認済みで、GUIの一部操作は検証用の実イベント入力で確認し、認証接続と全条件での手動検収は未実施。下位と変換アイテムの通常クラフト、三種の自作アイコンも実装した。Gem Caseの右側タブから中央の専用表示を開き、アイコン一覧・純度別の所持数・素材6枠・強化を一画面で操作できる。通常JARとクライアントでの検収状況は[検証記録](docs/verification.md)を参照する。

## 開発と検証

Java 21のJDKをJAVA_HOMEへ指定し、Gradle Wrapperを使う。ホストの別版Javaをそのまま使わない。キャッシュをrepo内へ置く場合はGRADLE_USER_HOMEを`.local/gradle`へ指定する。

```bash
node --test scripts/test/*.test.mjs
./gradlew check
./gradlew :neoforge:runData
# datagen後は別のGradle呼び出しでbuildする。
./gradlew :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
```

初期化は完了済み。Node 24／pnpmは開発用スクリプトとリリース管理で使う。外部skillsの導入は任意。

`runClient`は開発限定のNeoForge版JEIを含み、`runData`はApotheosisのデータ生成に必要なGatewaysを含む。`runServer`・GameTestにはこの二つを入れない。起動環境は`.local`、通常JARは`neoforge/build/libs`。新規の専用サーバーでは初回起動時のEULA確認・受諾が別途必要。

通常JARの実行にはKFFの配布JARと対象MOD・関連依存が必要。KFFのMaven開発用aggregate単体は配布JARの代わりにしない。固定版・取得元・hash・導入側は[対応構成](docs/compatibility.md)と[依存manifest](docs/dependency-manifest.json)を参照する。クライアント試遊は[検収手順](docs/client-acceptance.md)を参照。

## リリース

1. 配布する変更のPRで `pnpm --config.stateDir=.local/pnpm-state install --frozen-lockfile --ignore-scripts` と `pnpm exec changeset` を実行する。対象に `sophisticated_gem_case-tools` を選び、バージョンの上げ幅と変更内容を記録して、生成された `.changeset/*.md` をコミットする。
2. PRが `main` に入ると、Changesetsが `chore: version MOD` PRを作成または更新する。そのPRで `package.json`、`gradle.properties`、`CHANGELOG.md` の更新を確認してマージする。
3. マージしたコミットのVerifyが成功すると、Releaseワークフローが通常JARをGitHub Packagesの `org.mocoserver:sophisticated-gem-case:<version>` に登録し、同じJARと変更履歴を付けてGitHub Release `v<version>` を公開する。自動公開の対象は `0.1.0` のような安定版。公開が途中で失敗した場合は同じワークフロー実行を再実行する。PackagesのJARとPOMの片方だけが存在する場合は公開を中断し、HTTPステータスをログに出す。

Version PRを自動作成するには、リポジトリの **Settings → Actions → General → Allow GitHub Actions to create and approve pull requests** を有効にする。Actionsが作成したPRの検証が承認待ちの場合は、PR上で **Approve workflows to run** を選ぶ。公開にはワークフローの `GITHUB_TOKEN` を使う。 [Changesets Action v1.9.0](https://github.com/changesets/action/tree/v1.9.0) · [GitHub Actionsのトリガー](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow) · [GitHub Packages Gradleレジストリ](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)

## 設計と開発資料

- [現在の設計](docs/design.md)、[連携調査](docs/integration-research.md)、[検証結果](docs/verification.md)
- [AGENTS.md](AGENTS.md)、[開発手順](.agents/skills/mod-implement/references/development.md)、[規約](.agents/skills/mod-kotlin/references/coding-conventions.md)
- [テンプレート更新](docs/template-updates.md)、[外部skills](docs/external-skills.md)

生成元は[minecraft-mod-template](https://github.com/moco-server/minecraft-mod-template) 0.1.0。元の[検証履歴](docs/template-verification.md)・[実装判断](docs/implementation-decisions.md)・旧テンプレート計画は上流の参考資料で、このMODの検収済み機能を示すものではない。
