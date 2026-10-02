# 独立Kotlin MOD開発

日本語で対話する。目的により適した案があれば理由とトレードオフを提案する。現在の設計はdocs/design.md、固定版はGradle properties／catalog、検証はdocs/verification.mdで管理する。生成先のMODは自身のrepoで保守する。

## 必須ルール

- [規約](.agents/skills/mod-kotlin/references/coding-conventions.md)に従う。valを既定とし、core公開APIにはpublicと型を明示する。通常コードの!!を避ける。コメント・KDocは日本語で理由と契約を書く。
- coreはpure Kotlin/JVM。Minecraft／loader／KFF／連携MODの型は内部にも公開APIにも入れない。ゲームへの操作はneoforgeへ置き、必要な境界だけ小さく定義する。coreの禁止prefixへ連携MODのpackageを追加する。
- client専用コードはclient packageへ隔離し、専用サーバーが読み込むクラスから参照しない。加工・素材消費・保存変更は適切なサーバースレッドで検証して確定する。失敗時に素材や出力を部分変更しない。
- 版固有APIは[対応構成](docs/compatibility.md)と公式資料／作者ソースを照合し、URLと対象版を残す。最新の別版APIを推測で流用しない。
- [開発手順](.agents/skills/mod-implement/references/development.md)のcheck、変更に応じた単体テスト／datagen／GameTest／起動を実行する。ビルド・起動・実動作・未実施を区別し、構成・手順・観測結果を記録する。
- 生成resourceを手編集せず、datagen後は追跡・未追跡差分を確認する。通常JARにcoreと必要resourceを含め、外部MOD本体・テスト・skills・開発資料を同梱しない。
- .local／buildへ起動環境と生成物を置く。外部JAR、キャッシュ、ワールド、ログ、認証情報をGitへ追加しない。保存済みワールドは上書きせず、新規／コピーで検証する。
- 対話的クライアントには対象版のEMI／JEIを開発限定で用意し、版・取得元・hash・sideを記録する。本体の必須依存にしない。
- 外部skillsは[固定manifestと導入手順](docs/external-skills.md)を使い、repoの.agents/skillsだけへ導入する。グローバル導入・既存内容の無断上書きはしない。通常ビルドはskills／Nodeへ依存させない。

## 自作skills

該当作業に必要なSKILL.mdだけ読む。skill固有の補助資料はそのskillのreferences/、実行補助はscripts/へ置き、SKILL.mdから用途を示して参照する。docs/は対象MODの設計・対応構成・検証記録などのプロジェクト資料に使う。

| 作業 | skill |
| --- | --- |
| 設計・依存境界・保存／失敗条件 | [.agents/skills/mod-design/SKILL.md](.agents/skills/mod-design/SKILL.md) |
| Kotlin・Java相互運用・規約修正 | [.agents/skills/mod-kotlin/SKILL.md](.agents/skills/mod-kotlin/SKILL.md) |
| 登録・加工・datagen等の実装 | [.agents/skills/mod-implement/SKILL.md](.agents/skills/mod-implement/SKILL.md) |
| 起動失敗・不具合・ログ調査 | [.agents/skills/mod-debug/SKILL.md](.agents/skills/mod-debug/SKILL.md) |
| 検証・受け入れ・結果記録 | [.agents/skills/mod-verify/SKILL.md](.agents/skills/mod-verify/SKILL.md) |
| 配布JAR・依存・保存互換・リリース | [.agents/skills/mod-release/SKILL.md](.agents/skills/mod-release/SKILL.md) |

委任時は目的・対象ファイル・受け入れ条件・必須task・保存／side制約を渡す。モデル名と常時並列は規約に固定せず、統括者はdiffと実測結果から検収する。
