# 外部skills

`bash scripts/install-skills.sh --list`で取得先、`--dry-run`で無変更の確認、引数なし（core）またはallで導入する。WindowsはGit Bash／WSLを使う。Git Bashではnpm導入のpnpm、Corepack、pnpm.exeに対応し、.cmd shim自体をshellへ渡さずJS本体をNodeで起動する。Node 24、pnpm 12.8.1、git、ネットワークが必要。通常のMODビルドには不要。

固定manifestはscripts/skills.tsv。初期セットはsiredvin/minecraft-modding-skillsのneoforge-moddingとmodrinth-api。commitはb8e1133597d08eb41ac0d00d5d9555af8c1ba895、取得元ライセンスはMIT。

[NeoForge skill](https://github.com/siredvin/minecraft-modding-skills/tree/b8e1133597d08eb41ac0d00d5d9555af8c1ba895/skills/neoforge-modding)、[Modrinth skill](https://github.com/siredvin/minecraft-modding-skills/tree/b8e1133597d08eb41ac0d00d5d9555af8c1ba895/skills/modrinth-api)、[取得元LICENSE](https://github.com/siredvin/minecraft-modding-skills/blob/b8e1133597d08eb41ac0d00d5d9555af8c1ba895/LICENSE)。利用実績の多さを保証とは扱わず、対象版の公式資料でAPIを照合する。Kotlin配置・core境界・taskは本repoの規約を優先する。

manifest全体の検証後、gitで固定SHAを取得しHEADを確認する。固定skills CLI 1.7.0へ検証済みのローカルsourceを渡し、--agent codex --copy --yesでrepo内へ導入する。SKILL.mdだけでなく同梱referencesも取得する。stdinを継承せず、同梱scriptやhookを実行しない。pnpmのcache/store/stateはCLIで.localへ指定する。pnpm 12ではdlxがworkspaceのcache設定を利用しないため、明示指定が必要。

取得先は.agents/skills。外部本体・skills-lock.jsonはGit除外、自作mod-* skillsはGit管理する。checkoutと固定版記録は.local/skills-sourcesと.local/skills-installation.json。既存skillは変更せず、記録なしならunknown revision、不一致なら固定版との差を表示する。skills-lock.jsonとpnpmのcache/store/stateも事前検査し、symlink経由の書込みは拒否する。

更新時は新commitの内容・参照資料・ライセンスを確認してmanifestを変更する。既存skillを通常導入で上書きしない。必要な自分の変更を退避し、対象ディレクトリの削除／置換を明示的に許可してから再導入する。初期化・通常ビルド・CIで外部skillsの導入を必須にしない。

Windowsの.cmd実行制約は[Node 24公式資料](https://nodejs.org/docs/latest-v24.x/api/child_process.html#spawning-bat-and-cmd-files-on-windows)に従う。任意のworkflow_dispatch入力verify-skillsで、Git Bash、repoパスに空白、ローカル導入pnpm 12.8.1の構成で実際のskills導入を検証する。通常のpush／PR品質検査は外部skillsの導入に依存しない。
