# Kotlin MOD開発テンプレート

Minecraft 1.21.1／NeoForge 21.1.252／Java 21向けの独立MODテンプレート。coreはゲームに依存しないKotlin/JVM、neoforgeは登録・イベント・datagen等のアダプタ。Fabric移植の実証は未実施。

[moco-server/minecraft-mod-template](https://github.com/moco-server/minecraft-mod-template)のUse this templateで独立repoを作り、Java 21をJAVA_HOMEへ指定する。Node 24／pnpm 12.8.1は初期化・skills導入用で、通常ビルドには不要。

```bash
node scripts/init-template.mjs --mod-id example_gem --name '宝石ケース' \
  --package org.example.gem --group org.example.mods \
  --author '作者' --repo-url https://github.com/example/gem --dry-run
```

変更対象を確認したら--dry-runを外して実行する。記録ファイル.template-initialized.jsonとtemplate-version.txtを管理する。再初期化は拒否する。

```bash
./gradlew :neoforge:runData
./gradlew check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer
```

通常JARはneoforge/build/libs、起動環境は.local。配布時には対象版のKotlinForForge配布JARも必要（Mavenの開発用aggregateと区別する）。サンプルはmod_template:sample_token、鉄インゴット1個＋紙1個から1個作れる。

開発エージェントは[AGENTS.md](AGENTS.md)、人は[開発手順](.agents/skills/mod-implement/references/development.md)と[規約](.agents/skills/mod-kotlin/references/coding-conventions.md)を読む。[構成と互換性](docs/compatibility.md)、[検証記録・手順](docs/verification.md)、[外部skills](docs/external-skills.md)、[更新・サンプル削除](docs/template-updates.md)も参照。

初期化後はdocs/design.mdを対象MODの現在の設計へ更新する。各MODの課題と保守は各repoで行い、pack側には導入版・取得元・ハッシュ・共存検証を残す。

初版のクライアント検収状況は[検収手順](docs/client-acceptance.md)と検証記録を参照。公開範囲は非公開、ソースライセンスはMIT。

初版の判断・変更理由は[実装判断](docs/implementation-decisions.md)、実測と未検収項目は[検証記録](docs/verification.md)を参照。
