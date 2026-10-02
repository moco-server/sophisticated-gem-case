# Kotlin MOD開発テンプレート設計

## 目的と合意した方針

オリジナルMODをそれぞれ独立リポジトリで開発・保守するため、Kotlin中心のGitHubテンプレートを用意する。Lunaを含む実装担当エージェントが、規約・依存境界・検証手順を再現して作業できることを重視する。

2026-10-02に、次の方針をユーザーと合意した。

- 初版はMinecraft 1.21.1／NeoForge／Java 21で動作する構成にする。
- ゲームから独立したcore実装とloader向けアダプタを分け、境界をCIで検査する。
- Kotlin、Gradle Kotlin DSL、ktlint、detektを標準とする。
- AGENTS.md・コーディング規約・自作skillsをGit管理する。外部skillsは取得元コミットを固定したscriptでrepo内へ導入する。
- 初版ではFabricを実装せず、すべてのMinecraft APIを独自APIで包む基盤も作らない。
- 既存の亜鉛MODやGem連携MODの実装・移行は、テンプレート作成とは別の作業とする。

GitHubは `moco-server/minecraft-mod-template`、初版は非公開templateとして作成済み。名前・公開範囲は承認済み実装計画に従い、一般公開は別の判断とする。テンプレートと生成先のソースライセンスはMITを既定とし、外部資料・素材・skillsの権利は個別に記録する。

この設計書・実装計画はテンプレートrepoへ移管済み。今後のテンプレートの仕様・課題・検証・保守はこのrepoで行う。親packには採用したMODの版・取得元・ハッシュ・設定・pack共存検証を残す。

## 構成

```text
AGENTS.md
README.md
LICENSE
.editorconfig
.gitignore
.github/workflows/verify.yml
.github/ISSUE_TEMPLATE/
.github/pull_request_template.md
.agents/skills/
  mod-design/SKILL.md
  mod-kotlin/SKILL.md
  mod-kotlin/references/coding-conventions.md
  mod-implement/SKILL.md
  mod-implement/references/development.md
  mod-debug/SKILL.md
  mod-verify/SKILL.md
  mod-release/SKILL.md
  # 外部skillsの導入先もここ。外部本体はGit管理しない
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/
gradlew / gradlew.bat
config/detekt/detekt.yml
core/
  src/main/kotlin/
  src/test/kotlin/
neoforge/
  src/main/kotlin/
  src/main/resources/
  src/main/templates/
  src/generated/resources/
  src/gametest/kotlin/
scripts/
  init-template.mjs
  install-skills.sh
  skills.tsv
  # 初期化・境界検査・配布JAR検査に必要な小さな検証ツール
docs/
  design.md
  compatibility.md
  verification.md
  external-skills.md
  template-updates.md
template-version.txt
package.json / pnpm-lock.yaml / .npmrc
.local/                  # 起動環境、ワールド、ログ、キャッシュ。Git除外
```

`package.json`は初期化・skills導入等の開発用ツールに限定する。NodeやpnpmをMODの実行時依存にしない。MODのビルドと品質検査はGradle Wrapperで実行できるようにする。Bash製skills導入scriptのWindows実行はGit BashまたはWSLを案内する。

## coreとアダプタの境界

`core`はKotlin/JVMのライブラリで、JDK・Kotlin標準ライブラリと承認した汎用ライブラリだけを使う。Minecraft、NeoForge、Forge、Fabric、KotlinForForge、連携対象MODのクラス・型を公開APIにも内部実装にも持ち込まない。

ID文字列、数量、加工条件、保存モデル、計算など、ゲームに依存せず意味を持つ処理を置く。必要な外部操作だけを小さなインターフェースにし、実際に境界を越える用途が生じた時に追加する。将来使うかもしれないサービス探索、全レジストリの抽象化、汎用イベントバスなどは置かない。

`neoforge`はcoreを参照し、登録、イベント、設定、保存形式との変換、パケット、画面、datagen、外部MOD連携を担当する。ゲームから読み取った値をcoreへ渡し、結果をゲーム側へ反映する。逆方向の依存は禁止する。client専用クラスは専用パッケージへ分け、専用サーバーが読み込むクラスから直接参照しない。

CIではcoreの本番compile/runtime classpathと本番クラスの参照を検査する。禁止対象にはMinecraft・loader・KFFのパッケージと、各MODで追加設定する連携MODのパッケージを含める。単純なimport文字列検査だけで、完全修飾参照や推移依存の混入を見逃さない構成とする。禁止依存を入れた検証用コピーでチェック失敗も確認する。

共通の画像・翻訳等は必要に応じて共通resourcesに置ける。recipe・tag等はMinecraft版に依存するため、loader／対象版のdatagenで生成し、pure coreのロジックと同一扱いしない。

初版のサンプルは一つの簡単なitem登録・coreの数量検証・datagen・GameTestに絞る。recipeはバニラとの競合を避け、鉄インゴット1個＋紙1個からsample_token 1個。Create、Mekanism、Apotheosis、Sophisticated系をテンプレートの必須依存にしない。Fabricへの実移植を未実施のため、移植性の実証済みとは記載しない。

## Kotlinとビルド環境

- Kotlin/JVMとGradle Kotlin DSLを使う。アダプタも原則Kotlinとし、MixinやJava APIとの接続上必要なJavaは理由を記録して許容する。
- Java toolchainとKotlin JVM targetを21に揃える。coreには明示的APIモードを適用し、公開境界の可視性・型を省略しない。
- NeoForge初期版はpackの21.1.252を検証基準とする。Gradle Wrapper、ModDevGradle、Kotlin Gradle Plugin、ktlint本体／plugin、detekt、KFF、テスト基盤は互換性を確認した組合せに固定する。最新版を一括採用しない。
- NeoForge側のKotlin実行環境はKotlinForForgeのNeoForge用variantを基本案とする。対象Minecraft版・内包するKotlinライブラリ・language loader宣言を一次資料と配布物で確認する。coreはKFFに依存しない。初版では独自のstdlib shadingや新しいlanguage providerを作らない。
- ツールの版はversion catalog等の一か所で管理し、MODの識別情報はGradle propertiesからmetadataへ反映する。Kotlin pluginと実行時stdlib、detekt解析系、ktlint解析系を区別し、全configurationへ一括でKotlin版を強制しない。
- ktlintはKotlinソースとGradle Kotlin scriptの整形を担当する。detektは不必要な可変宣言・複雑さ・危険な記述等を担当する。detektのformatting拡張は併用せず、整形規則を二重管理しない。
- compiler warningsは原則エラーとする。例外が必要なら狭い範囲で理由を残し、全体の警告無効化や大量baselineで解決しない。
- Wrapperのdistribution checksumを記録する。宣言依存は固定し、推移依存のlockingとverificationは対象ModDevGradleが解決・生成するartifactを確認したうえで適用範囲を定め、未検証のchecksumを手書きしない。
- 生成resourcesはGit管理し、再生成後の追跡・未追跡ファイル双方の差分を検査する。metadata展開後やbuild出力を直接編集しない。
- 通常JARにはcoreを含め、外部MOD本体・テストfixture・skills・開発資料を同梱しない。

版の具体値の選定は実装計画の最初の互換性確認タスクとする。この仕様書は組合せの動作確認を済ませた版一覧ではない。KFF作者例がKotlin 2.2.20を使う一方、detekt 1.23.8の互換表はKotlin 2.0.21でのビルドを示しており、両資料だけからtype resolutionの互換性を断定しない。

## コーディング規約

Kotlin公式規約を基本に、次をプロジェクト規約として明文化する。

| 対象 | 規約 | 強制／確認方法 |
| --- | --- | --- |
| 再代入 | `val`を既定とし、再代入が必要な時だけ`var` | detekt `VarCouldBeVal`を有効化し型解決付きtaskで検査。違反fixtureで検出を確認 |
| コレクション | 外部には読み取り専用型を公開し、可変実装・所有権を局所化 | レビュー。`val`／read-only型だけでは深い不変性にならないことを明記 |
| 可視性 | 公開APIを最小化し、core公開APIの型を明示 | explicit APIモードとレビュー |
| null | nullableを型で表し、`!!`を通常コードで使用しない | detektの該当規則とレビュー。Java境界では明示的な契約検証 |
| 型 | 不正状態を表しにくい値型・enum等を使う。曖昧なboolean列を避ける | レビュー。全てのID・数量を無条件に型で包まない |
| 例外 | 回復可能な失敗と契約違反を区別し、例外を握り潰さない | 静的解析＋失敗条件テスト |
| 非同期 | ゲーム状態の更新は対象loaderの正しいスレッドで実行する | GameTest／サーバー検証。coroutinesは必要な機能でのみ導入 |
| 記述 | 読みやすさを優先し、scope関数・拡張関数・長いチェーンを増やしすぎない | ktlint／detekt＋レビュー |
| 警告抑制 | 最小範囲の抑制と具体的理由。ファイル全体／全taskの無効化を避ける | レビュー |
| Java併用 | 追加理由を記録し、再代入しないローカル変数は`final`を基本とする | レビュー。Kotlinのval/varとJavaの型推論varを混同しない |

命名・indent・改行・末尾comma等の整形は`.editorconfig`とktlintに集約する。Kotlin規約の文書とツール設定が食い違う場合、調査して両方を揃える。

コメント・KDoc・開発文書は日本語を基本とする。識別子は英語で、ゲームの翻訳はja_jpとen_usを用意する。

- コメントには理由、制約、不変条件、互換性上の判断を書く。処理をそのまま言い直すコメントを付けない。
- 公開APIのKDocには役割と必要な契約を記す。状態変更・失敗条件・スレッド・単位等は該当する場合に書く。自明なprivate関数に形式的なKDocを強制しない。
- 対象版固有の回避策には版・一次資料URL・解除条件を記す。
- 残作業をコメントする場合はIssueまたは具体的な解消条件と関連付ける。実装済みの説明に曖昧なTODOを残さない。
- 生成コード・resourcesは入力と生成手順を記録し、生成物を手修正しない。

## AGENTS.mdと自作skills

AGENTS.mdは必須ルールと参照先・実行コマンドを短くまとめる。詳細な設計・規約・検証記録をコピーして二重管理しない。

| 自作skill | 役割 |
| --- | --- |
| mod-design | 目的・依存版・core境界・保存／通信／失敗条件を整理し、独立MODの設計書を更新 |
| mod-kotlin | Kotlin規約、Java相互運用、可変状態、null、KDoc、lintの扱い |
| mod-implement | 一次資料を確認した小さな変更、必要なテスト、datagen、core／loader分離 |
| mod-debug | ログ・再現条件・版・sideから原因を切り分ける |
| mod-verify | 構成・手順・観測結果を記録し、ビルド、起動、実動作を区別する |
| mod-release | JAR内容、依存宣言、クリーン導入、保存互換、変更履歴を確認 |

各skillは具体的なtriggerと入出力を持ち、同じ規約本文を複製せず、所有skill内のreferences/を参照する。Kotlin規約はmod-kotlin、開発手順はmod-implementが所有する。対象MODの設計・対応構成・検証記録はdocs/に置く。pack専用のpackwiz・KubeJS手順は必須セットに入れない。

実装担当への委任では、対象ファイル、目的、受け入れ条件、必須task、保存／sideの制約を渡す。担当モデル名を規約に固定しない。統括者はdiffと検証結果を確認し、委任先の「完了」だけで検収しない。

skill自体も、不要な`var`、coreへのMinecraft依存、専用サーバーへのclient参照、対象版と違うAPI、失敗時の素材消費といった具体的な課題で、指示と検査が噛み合うか評価する。

## 外部skillsの導入

初期の既定セットは次の二つ。内容を確認済みの同コミットを固定する。

| skill | 取得元 | commit | path |
| --- | --- | --- | --- |
| neoforge-modding | siredvin/minecraft-modding-skills | b8e1133597d08eb41ac0d00d5d9555af8c1ba895 | skills/neoforge-modding |
| modrinth-api | siredvin/minecraft-modding-skills | b8e1133597d08eb41ac0d00d5d9555af8c1ba895 | skills/modrinth-api |

NeoForge skillは版に合わせたAPI確認を補い、Kotlinソース配置・pure core境界・task構成はこのテンプレートの指示を優先する。取得元はMIT。利用実績を適合性の保証とは扱わず、公式資料と実際の対象版ソースで照合する。

Kotlin候補のjeffallan/claude-skillsのkotlin-specialistも調査したが、coroutines／Flow／KMPなどの一般アプリ向け指示がMODの最小構成に過剰なため初期導入しない。Kotlin規約は公式資料、自作skill、機械検査で補う。リソースパック等のskillsは実際の必要性が生じたとき追加し、その場で内容と版適合を確認する。

導入はpackの方式を参考に、`scripts/skills.tsv`へ取得元・40桁commit・path・グループを記録する。scriptは自分の位置からrepo rootを解決し、対象文字列を検証してTSVをコードとして実行しない。固定版skills CLIをpnpmで実行し、global導入を禁止する。

- `--list`と`--dry-run`は変更なしで確認できる。
- 外部本体と生成skills-lock.jsonはGit除外し、自作skillはGit管理する。
- 既存パスを無断上書きしない。既存版が固定commitと一致しない場合は状態を報告し、更新を通常導入と区別する。
- キャッシュ等はrepoの`.local/`へ置き、グローバルskillsやユーザー共通設定を変更しない。
- 導入後に必要なSKILL.mdと参照ファイルが揃うか検査する。skillsの同梱scriptやhookを導入時に自動実行しない。
- 外部skillsを未導入でもビルド・CIが成立する。ネットワーク取得は開発環境の明示的な準備手順とする。

## 初期化とテンプレートの更新

GitHubの「Use this template」で作ったrepoは独立したソース管理単位とする。初期化scriptへMOD ID、表示名、Java package、Maven group、作者、repo URLを明示的に渡し、properties・metadata・package宣言／ディレクトリ・assets/data namespace・文書を一貫して変更する。値は検証し、無差別な全ファイル置換やshellへの未escape補間を使わない。

初期化はdry-runを持ち、実行前に変更対象を表示する。再実行を検出し、開発済みMODや既存ソースを壊さない。サンプルはrename後にも起動可能とし、サンプル削除手順も文書化する。

`template-version.txt`に元テンプレート版を記録する。templateの更新は移行ガイドに変更理由・対象ファイル・必要な検証を書く。各MODは必要な差分を自repoへ取り込み、CIを通して更新する。初版は自動同期botや外部共有Gradle pluginを作らない。亜鉛MODをKotlinへ一括移行する作業も含めない。

## 検証とCI

通常の品質検査はrootの`./gradlew check`へ集約し、ktlint、型解決付きdetekt、core単体テスト、アダプタ単体テスト、core境界検査を含める。整形修正は明示的なktlintFormat操作とし、CIではソースを書き換えて通さない。

GameTestとdatagenは独立したtaskとしてCIから実行し、通常JARを保存する。Linuxで全検証を実行し、WindowsではWrapper・初期化・基本ビルドを確認する。対話的クライアント試遊には対象版のEMIまたはJEIを開発環境限定で用意し、取得元・版・hash・導入側を記録する。レシピビューアをテンプレートMOD本体やヘッドレスGameTestの必須依存にしない。

受け入れ条件:

1. 別ディレクトリのクリーンなコピーで初期化し、新しいMOD ID・package・namespaceでcheck、datagen、build、GameTestが成功する。
2. 不要な`var`を持つKotlin fixtureでdetektが失敗し、valへ修正すると通る。coreへの禁止依存／型参照でも境界検査が失敗する。
3. datagen再実行に追跡・未追跡差分がなく、通常JARに必要なcoreとassets/data・metadataが入り、テスト・外部MOD本体・開発資料が入らない。
4. 通常JAR＋宣言した実行時依存だけの専用サーバーで起動する。GameTest成功と通常JARでの起動成功を別々に記録する。
5. クライアントでサンプルが見え、専用サーバーへ接続して実際の動作を確認する。GUIや認証等で未実施の場合は未検証を明示し、テンプレートの完全検収済みとは扱わない。
6. skills導入のdry-run・正常導入・既存パス保護・不正manifest拒否を確認し、globalへの変更がない。
7. AGENTS.md、skills、規約文書に記載するパス・taskが実在し、担当エージェントが規約違反を検査・修正できる。
8. GitHubのtemplate設定とクリーンな取得手順を確認する。公開リリースは公開範囲の判断後に行う。

## 一次資料と調査状態

2026-10-02に一次資料と既存亜鉛MODの構成を調査し、承認済み計画に従って初版0.1.0を実装した。具体的な固定版はcatalog／gradle.properties／Wrapperを正とする。

- [Kotlin公式規約](https://kotlinlang.org/docs/coding-conventions.html)、[Gradle設定](https://kotlinlang.org/docs/gradle-configure-project.html)。
- [NeoForge 1.21.1開発環境](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)。Java 21と対象版のMDKを基準にする。
- [KotlinForForge 5.xの作者手順](https://github.com/thedarkcolour/KotlinForForge/blob/0e8e3b579661acdad6ff76b9620dcc06f8885cab/README.md)。NeoForge用artifactと内包ライブラリ、対象版の実装を確認する。
- [ktlint Gradle plugin](https://github.com/JLLeitschuh/ktlint-gradle)。pluginとktlint本体を区別して固定する。
- [detekt 1.23.8互換表](https://detekt.dev/docs/1.23.8/introduction/compatibility/)、[VarCouldBeVal](https://detekt.dev/docs/1.23.8/rules/style/#varcouldbeval)。解析版とcompiler依存の整合を確認する。
- [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)。実装時には採用Wrapper版の資料も照合する。
- [NeoForge skillの固定ソース](https://github.com/siredvin/minecraft-modding-skills/tree/b8e1133597d08eb41ac0d00d5d9555af8c1ba895/skills/neoforge-modding)、[Modrinth skill](https://github.com/siredvin/minecraft-modding-skills/tree/b8e1133597d08eb41ac0d00d5d9555af8c1ba895/skills/modrinth-api)。
- [今回導入しないKotlin skill](https://github.com/jeffallan/claude-skills/blob/882ef55e377dbf9a4dbe496bb41ac6ccd0e555cf/skills/kotlin-specialist/SKILL.md)。

テンプレート元とGitHub生成・初期化先のLinux／Windows CI、通常JARの専用サーバー起動は確認済み。認証済みclientの表示・接続・実クラフトは未検収。実測は[検証記録](verification.md)、実装中の判断は[実装判断](implementation-decisions.md)を参照。
