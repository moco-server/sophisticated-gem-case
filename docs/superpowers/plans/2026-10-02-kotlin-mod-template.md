# Kotlin MODテンプレート実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 実行方式はユーザーの選択に従い、選択前にサブエージェントを起動しない。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 独立したKotlin MODを作成・保守でき、規約とcore境界をCIで検査するNeoForge向けGitHubテンプレートを作る。

**Architecture:** pure Kotlin/JVMの`core`を`neoforge`アダプタから利用する。規約をAGENTS.md・自作skills・ktlint・detekt・境界検査へ対応させる。初期化後のクリーンなコピーまで検証し、保守の正を独立repoへ移す。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.252、Java 21、Kotlin/JVM、Gradle Kotlin DSL／Wrapper、ModDevGradle、KotlinForForge NeoForge variant、ktlint、detekt、JUnit、Node 24／pnpm、GitHub Actions。

**Spec:** [承認済み設計書](../../design.md)（`1c68fa7`）。独立repoには`docs/design.md`として移す。

## Global Constraints

- 初版はMinecraft 1.21.1／NeoForge／Java 21。Fabric実装、亜鉛MODの移行、Gemアドオン実装を含めない。
- coreの本番コード・公開型・compile/runtime classpathへMinecraft／loader／KFF／外部MODを持ち込まない。
- アダプタも原則Kotlin。必要なJava併用は理由を記録する。coreはexplicit APIモード、Java／Kotlin JVM targetは21。
- ゲームに関係しない外部アプリ向けのライブラリを追加しない。coroutines等の機能をサンプルに無条件で使わない。
- コメント・KDoc・開発文書は日本語、識別子は英語、ゲーム翻訳はja_jp／en_us。
- 外部skillsはrepo内`.agents/skills/`のみ。固定commitは`b8e1133597d08eb41ac0d00d5d9555af8c1ba895`、初期セットはneoforge-modding／modrinth-api。
- 外部JAR、ワールド、ログ、キャッシュ、取得したskills本体をGit管理しない。Gradle Wrapper JARはビルド用の例外として管理する。
- Nodeは開発script用。通常のMODビルド・品質検査をNode／skills導入に依存させない。
- 対話的クライアント検証ではEMIまたはJEIを開発環境に用意し、取得版・URL・hash・導入側を記録する。
- ローカル独立Gitは`.local/mod-development/minecraft-mod-template/`へ置く。GitHubは`moco-server/minecraft-mod-template`、非公開、MIT、template設定ありを承認済み仕様の初期案として使用する。
- 各taskの検証後に独立repoへ小さくcommitする。親packにMODソースを追加しない。push・repo作成は最終の公開準備taskで行う。

## Review Focus

1. Kotlin実行環境と解析compilerの版違いで、正常なコードまでlint不能になる条件 → Task 1で本物の型解決付き解析と起動を確認。
2. 推移依存・完全修飾名・クラスsignatureからcoreへゲーム型が漏れる条件 → Task 3で禁止fixtureの失敗を確認。
3. 初期化入力に空白・日本語・escape・不正IDが混ざる条件 → Task 5で無変更の拒否と妥当な文字列の保存を確認。
4. skills導入先の既存内容・symlink・不正manifestによる上書き条件 → Task 6で保護と不正入力拒否を確認。
5. dev起動は成功するが配布JARにcore／resourcesがない条件 → Task 4・8で内容検査と通常JARのクリーン起動を確認。

---

## ファイルとtaskの対応

独立repoルートからの相対パスを使う。

| 所有task | 主なファイル | 責務 |
| --- | --- | --- |
| 1 | Wrapper、settings/build.gradle.kts、catalog、core/neoforge/build.gradle.kts、docs/compatibility.md | 依存版、KotlinとNeoForgeの最小動作 |
| 2 | .editorconfig、config/detekt/detekt.yml、docs/coding-conventions.md | 規約と品質task |
| 3 | buildSrc/src/main/kotlin/CoreBoundaryTask.kt、buildSrc/src/test/kotlin/CoreBoundaryTaskTest.kt | 本番依存・bytecode参照の境界 |
| 4 | core／neoforgeのサンプル、datagen、GameTest、VerifyModJarTask.kt | coreを利用する実ゲーム動作とJAR契約 |
| 5 | scripts/init-template.mjs、scripts/test/init-template.test.mjs | 新MODへの一貫したrename |
| 6 | scripts/install-skills.sh、scripts/skills.tsv、scripts/test/install-skills.test.mjs | 固定skillsのローカル導入 |
| 7 | AGENTS.md、.agents/skills/mod-*/SKILL.md、docs/各手順 | エージェント作業・検証・保守契約 |
| 8 | .github/workflows/verify.yml、各テンプレート、検証記録 | CI、クリーンな取得と使用 |
| 9 | 独立repo metadata、template-version.txt、親packの参照記録 | GitHub template化と保守移管 |

buildSrcのKotlinもlint対象にする。buildSrcにはGradleのtaskと必要な解析ツールだけを置き、MODの実行時依存と分離する。

### Task 1: 互換性を確認したKotlin／NeoForge最小構成

**Files:** Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `core/build.gradle.kts`, `neoforge/build.gradle.kts`, `neoforge/src/main/templates/META-INF/neoforge.mods.toml`, `neoforge/src/main/kotlin/org/mocoserver/modtemplate/ModTemplate.kt`, `docs/compatibility.md`, `.gitignore`, `LICENSE`。

**Interfaces:** Produces: projects `:core`, `:neoforge`、MOD ID `mod_template`、package `org.mocoserver.modtemplate`、version `0.1.0-dev`、tasks `:core:test`, `:neoforge:runClient`, `:neoforge:runServer`, `:neoforge:runData`, `:neoforge:runGameTestServer`。起動先は`.local/client|server|data|gametest`。

- [ ] 承認済み仕様を独立repoの`docs/design.md`へコピーし、作業用Gitを初期化する。既存の同名パスを上書きせず状態を確認する。
- [ ] 対象版の公式MDKとKFF配布物を調査する。detekt安定版のcompiler互換表と同じKotlinを最初の候補にし、そのKotlinと整合するKFFの公開版を確認する。ModDevGradleは既存亜鉛MODの`2.0.148`を最初の候補とし、Wrapperは候補Kotlin・detekt・ModDevGradleの共通対応範囲から選ぶ。候補版・URL・hashを記録する。
- [ ] 一つのKotlinエントリポイントとcore単体テストを持つ最小構成を作る。KFFのlanguage loaderと依存metadataを一致させ、通常JARにcoreを含める。
- [ ] 型解決付きdetektで実際のKotlinコードを解析し、`VarCouldBeVal`を検出できる組合せを確認する。compiler metadataチェックを無効化して通さない。互換性が不足したら安定版の別候補を比較し、版の更新理由を記録する。
- [ ] Run: `./gradlew :core:test :neoforge:build`、続けて最小構成の専用サーバー起動。Expected: テスト成功、KFF／MODの読込成功、serverの起動完了。各task名も`tasks --all`で確認する。
- [ ] 固定版とWrapper checksum、必要なlocking／verificationの適用範囲を確定し、`docs/compatibility.md`へ検証結果を記録してcommitする。

### Task 2: Kotlin規約を実際に強制する品質task

**Files:** Create: `.editorconfig`, `config/detekt/detekt.yml`, `docs/coding-conventions.md`; Modify: root/core/neoforge/build.gradle.kts、catalog。

**Interfaces:** Produces: root `check`からktlintCheck、型解決付きdetekt、両projectの単体テストを実行。formatは`ktlintFormat`の明示操作。

- [ ] 一時コピーにfixtureを置く。`fun fixture(): Int { var count = 1; return count }`は`VarCouldBeVal`、`fun length(value: String?): Int = value!!.length`は非null断言規則で失敗することを期待する。core公開関数で可視性／戻り型を省くfixtureはexplicit APIで失敗することを期待する。
- [ ] ktlintとdetektを固定して設定する。detekt formatting拡張は導入しない。4 spaces／LF／UTF-8を基本に公式Kotlin規約を適用し、ソースと`.gradle.kts`を対象にする。
- [ ] compiler warningsをエラーにし、coreにexplicit APIモードを設定する。型解決付きtaskを`check`へ接続する。Task 1で使った解析設定をここへ集約する。
- [ ] fixtureの違反→修正を順に検証する。Expected: 違反時は該当規則名付きで非0、修正後の`./gradlew check`は成功。fixtureは本番ソースへ残さない。
- [ ] specの規約表とコメント・KDocルールを文書化し、機械検査とレビューの担当を明記してcommitする。

### Task 3: core依存境界の検査

**Files:** Create: `buildSrc/build.gradle.kts`, `buildSrc/src/main/kotlin/CoreBoundaryTask.kt`, `buildSrc/src/test/kotlin/CoreBoundaryTaskTest.kt`; Modify: `core/build.gradle.kts`, root build、catalog、品質設定。

**Interfaces:** `CoreBoundaryTask`: Gradle input `forbiddenPackagePrefixes: ListProperty<String>`, `productionClasspath: ConfigurableFileCollection`, `productionClasses: ConfigurableFileCollection`。Produces: `:core:checkCoreBoundary`をcore/root checkへ接続。

- [ ] JVM境界検査のテストを先に書く。Allowed JDK／Kotlin fixtureは成功、禁止クラスを含む推移依存JAR・公開signature・完全修飾参照fixtureは失敗し、原因のartifact／class名を示すことをassertする。
- [ ] テストを実行し、未実装で失敗することを確認する。
- [ ] classpathのJARエントリとcompiled classの型参照を調べるtaskを実装する。bytecode解析には固定したASMをbuildSrc限定で利用し、supertype／descriptor／signature／annotation／命令のowner等を検査する。禁止prefixは`net/minecraft/`, `net/neoforged/`, `net/minecraftforge/`, `net/fabricmc/`, `thedarkcolour/kotlinforforge/`とし、各アドオンで連携MOD分を追加できるようにする。
- [ ] Run: `./gradlew -p buildSrc test`とroot `check`。Expected: 検査テスト成功、coreの正常classを許可。検証用コピーへ禁止fixtureを入れたroot checkは失敗する。
- [ ] Gradleだけで検査できることを確認し、規約・development文書へ追加してcommitする。

### Task 4: coreを利用する最小サンプルと配布JAR契約

**Files:** Create: `core/src/main/kotlin/org/mocoserver/modtemplate/core/QuantityRules.kt`, 同test、`neoforge/src/main/kotlin/org/mocoserver/modtemplate/ModItems.kt`, `data/TemplateDataGenerators.kt`, `neoforge/src/gametest/kotlin/org/mocoserver/modtemplate/TemplateGameTests.kt`, test用metadata、`buildSrc/src/main/kotlin/VerifyModJarTask.kt`と同test; Modify: エントリポイント、neoforge build。

**Interfaces:** core `public fun checkedQuantity(value: Int): Int`は0以上を返し、負値をIllegalArgumentExceptionで拒否。item IDは`mod_template:sample_token`。`runData`は翻訳／モデル／鉄インゴット1個→sample_token 1個のrecipeを生成。`verifyModJar`は通常JAR内容を検査する。

- [ ] coreテストで`0→0`, `1→1`, `-1→IllegalArgumentException`をassertし、未実装の失敗を確認する。JAR検査テストはcore class欠落・metadata欠落・test／外部MOD class同梱を拒否することをassertする。
- [ ] core関数を実装し、item登録とdatagenで数量の検証に使う。サンプル画像はMinecraft内蔵textureをモデルから参照し、外部MOD画像をコピーしない。
- [ ] gametest source setを通常JARから分離する。GameTestは登録されたsample_tokenとcoreの数量契約、鉄1個のrecipe結果がsample_token 1個であることを検査する。対象版のAPIを照合して実装する。
- [ ] VerifyModJarTaskへ通常JARのcore／item class、assets/data、metadata、LICENSEの存在検査と、test namespace・skills・docs・外部MOD classの非同梱検査を実装する。sources JARを通常JARと誤認しない。
- [ ] Run: `./gradlew check :neoforge:runData :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`。Expected: 各検査成功。datagenを再実行し、追跡／未追跡差分が0であることを確認してcommitする。

### Task 5: 新MODへ初期化するscript

**Files:** Create: `scripts/init-template.mjs`, `scripts/test/init-template.test.mjs`, `package.json`, `pnpm-lock.yaml`, `.npmrc`, `template-version.txt`; Modify: README／metadata inputs／サンプル文字列。

**Interfaces:** CLI `node scripts/init-template.mjs --mod-id ID --name NAME --package PACKAGE --group GROUP --author AUTHOR --repo-url URL [--dry-run]`。Node built-inだけを使う。初期化記録は`.template-initialized.json`としてGit管理する。

- [ ] Nodeテストを先に作る。別コピーの`test_gem`／`org.mocoserver.testgem`へのrenameでpackage・namespace・metadata・文書が一致すること、日本語名とquoteを正しく保存すること、dry-run／不正ID／path traversal／再初期化／対象symlinkの拒否で変更がないことをassertする。
- [ ] Run: `node --test scripts/test/init-template.test.mjs`。Expected: 未実装で失敗。
- [ ] 置換対象のallowlistと型に合ったescapeを使う初期化を実装する。全対象と競合を確認してから書き込む。ユーザー入力をshell codeとして実行しない。ModTemplate等の固定class名は維持し、packageとID等を一貫して変更する。
- [ ] packageManagerはpackと同じ`pnpm@12.8.1`、Nodeは`>=24 <25`。skills CLIは`1.7.0`に固定する。cache／storeは`.local/`へ設定し、installはfrozen-lockfile／ignore-scriptsで実施する。
- [ ] テスト成功後、クリーンなコピーを初期化し、`./gradlew check :neoforge:runData :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`が新namespaceで成功することを確認してcommitする。

### Task 6: 外部skillsの固定導入

**Files:** Create: `scripts/install-skills.sh`, `scripts/skills.tsv`, `scripts/test/install-skills.test.mjs`, `docs/external-skills.md`; Modify: `.gitignore`、package scripts。

**Interfaces:** `bash scripts/install-skills.sh [--list|--dry-run] [core|all]`。既定coreはspecの2 skills。既存skillの固定版記録を`.local/skills-installation.json`に保持し、不一致を報告して無断更新しない。

- [ ] fake pnpm/gitを使うテストを作る。別cwdからのrepo内導入、dry-run無変更、既存skill保持、symlink拒否、不正40桁commit／path拒否、CLIが0終了でもSKILL.md欠落なら失敗、global flagがないことをassertする。
- [ ] Run: `node --test scripts/test/install-skills.test.mjs`。Expected: 未実装で失敗。
- [ ] packのscriptを参考に導入を実装する。manifest全体を処理開始前に検証し、同梱の参照資料も取得する。外部本体のignoreと自作skillの追跡を分け、scriptの標準入力にmanifest残行を渡さない。
- [ ] fakeテストを通し、クリーンなコピーで実導入する。Expected: 指定2 skillsと必要な参照資料が揃う。再導入は上書きせず、追跡した初回取得commitを示す。
- [ ] skills未導入の別コピーでもGradle check／buildが成功することを確認し、外部ソース・ライセンス・更新方法を記録してcommitする。

### Task 7: AGENTS.mdと自作skills／作業手順

**Files:** Create: `AGENTS.md`, `.agents/skills/mod-{design,kotlin,implement,debug,verify,release}/SKILL.md`, `docs/development.md`, `docs/verification.md`, `docs/template-updates.md`, README。

**Interfaces:** AGENTS.mdは規約文書と6 skillsを作業種別から参照。品質コマンドはroot check、datagen／GameTest／JAR検査はTask 4のtask。委任時は目的・対象ファイル・受け入れ条件・必須taskを渡す。

- [ ] skill-creator／writing-skillsの該当手順を読んで、自作skillsの作成・評価を行う。各skillはtrigger・入力・出力・検証を持ち、規約本文を複製しない。
- [ ] AGENTS.mdへ日本語対話、代案提案、一次資料／版確認、pure core境界、client/server分離、保存済みワールド保護、Git除外、検証結果の区別を記す。model名や常時並列委任を強制しない。
- [ ] mod-kotlinへval／可変状態／null／KDoc、mod-verifyへ構成・手順・観測結果、mod-releaseへ配布JAR・保存互換・取得可能性を定義する。mod-designは対象MOD独立repoのdesign.mdを更新する。
- [ ] 不要なvar、coreへMinecraft import、client参照、別版API、材料不足なのに素材消費する課題で指示の適合性を評価する。実際のエージェント試行を行う場合は選択した実行方式の範囲で行い、未実施を隠さない。
- [ ] 記載パスの存在とGradle task名を確認し、手順が実際に通ることを確認する。初期化後に文書の旧MOD IDが残らないこともTask 5のallowlist／テストへ追加してcommitする。

### Task 8: CIとクリーン導入・クライアント検収

**Files:** Create: `.github/workflows/verify.yml`, `.github/ISSUE_TEMPLATE/bug_report.yml`, `.github/ISSUE_TEMPLATE/feature_request.yml`, `.github/pull_request_template.md`; Modify: 検証記録、README、compatibility。

**Interfaces:** Linux全検証、Windows初期化・基本build、通常JAR artifact。CIは最小read権限とし、外部skillsインストールを必須にしない。

- [ ] Linux jobで`./gradlew check`、Node scriptテスト、datagen差分、build／verifyModJar、GameTestを実行する。通常JARをversion固定のファイル名に依存せず収集し、0件や複数候補を見逃さない。
- [ ] Windows jobで`gradlew.bat check :neoforge:build`と初期化テストを実行する。skills Bash testsはLinuxで実施。CIのactionsは検証したcommitへ固定する。
- [ ] ネットワーク取得・初期化を含むクリーンなコピーでCI相当taskを実行する。GUIなしのチェック成功と実ゲーム検収を分ける。
- [ ] 通常JARと実行時依存のみの専用サーバーを用意し、起動完了を確認する。test fixtureや他のMODを通常環境へ混ぜない。
- [ ] 対象版のEMIまたはJEIを開発クライアントだけへ用意し、sample_tokenの表示・recipe・専用サーバー接続・実クラフトを確認する。利用可能な認証済み環境がない場合はユーザー検収手順と成果物を用意して未実施項目を記録し、完全完了とはしない。
- [ ] PRテンプレートに変更目的、検証構成、実測／未検証を、bugフォームに版・再現・ログ、featureフォームに目的と代案を用意する。保存済みの実ワールドを使わない。
- [ ] 全検証の版・hash・コマンド・結果を記録し、ソース差分／規約／境界／JARをレビューしてcommitする。

### Task 9: GitHub template化と独立repoへの移管

**Files:** Modify: `README.md`, `template-version.txt`, `docs/template-updates.md`、親packの`docs/pack-design.md`／`docs/pack-decision-history.md`の参照だけ。

**Interfaces:** repo `moco-server/minecraft-mod-template`、非公開、`is_template=true`。テンプレート初版`0.1.0`と各MODへ取り込む更新ガイド。

- [ ] 同名repoの有無とGitHub認証を再確認する。存在すれば内容・権限を調べ、別repoを上書き・置換しない。
- [ ] ローカル成果物・検証結果を揃え、Task 8の未検証を公開範囲に正確に記す。非公開repoを作成し、独立Gitをpushしてtemplate設定を有効にする。
- [ ] remoteの版・template設定・内容・CI結果を確認する。クリーンclone＋初期化を再現し、GitHubのtemplate生成も使えることを確認する。生成テスト用repoを勝手に削除しない。
- [ ] 元テンプレート版、差分取り込み方法、サンプル削除方法、互換性の再検証を記録する。自動同期botは作らない。
- [ ] 親packにはテンプレートrepo／設計・計画の移管先をリンクし、今後の保守先が独立repoであることを記録する。亜鉛MOD・Gem MODのソースや導入状態は変更しない。

## 計画レビューと実行開始

この計画は依存版を検証済みとは扱わない。具体的な版はTask 1で根拠と実測を揃えて固定する。

ユーザーが計画をレビューして実行方式を選んだ後に、実装スキルを使ってTask 1から進める。推奨は統括者がこのセッションで実装する方式。各taskが同じGradle構成・規約・初期化対象に依存するため、実装中の判断を一か所に集めやすい。最終レビューの方法は選択した実行スキルに従う。

## 実行結果

Task 1〜9の実装・移管・自動検証を実施済み。最終レビューのImportant 5件は失敗する回帰テストで再現して修正し、テンプレート元とGitHub生成先でLinux／Windows CIが成功した。Task 8の認証済みクライアントによる表示・接続・実クラフトは検収待ちで、[手順と成果物](../../client-acceptance.md)を用意した。詳細は[検証記録](../../verification.md)と[実装判断](../../implementation-decisions.md)を参照。
