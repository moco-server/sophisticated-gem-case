# 検証

使用したMinecraft・loader・Java・Kotlin・依存MODの版、コマンド、手順、観測結果を記録する。ビルド成功、起動成功、実ゲーム動作は別々に扱う。未実施の項目を成功として扱わない。

2026-10-02、Linux／Java 21でcoreの数量テスト、型解決付きdetekt、ktlint、core境界検査を実行した。違反fixture（不要var、nullableへの!!、公開型省略、禁止依存JAR）はそれぞれ失敗し、修正後は成功した。最小専用サーバーはNeoForge 21.1.252／KFF 5.5.0で起動完了した。

サンプルは鉄インゴット1個＋紙1個からsample_token 1個を生成する。鉄だけではバニラの鉄塊レシピと競合することをGameTestで検出したため、材料を区別した。GameTestは本番から別package・source setに分離する。

生成resourceのキャッシュはGitと配布JARから除外する。datagenを実行した後は、別のGradle呼出しでbuild／GameTestを実行する。初回datagen時点でprocessResourcesが既に実行済みの場合があるため。

認証済みクライアントでの表示・接続・クラフト検収は後続のクリーン導入検証で記録する。

サンプルのdatagen、root check、通常JAR内容検査、GameTest（登録／数量と実recipeの2件）は成功。datagen再実行後の追跡・未追跡resource差分は0だった。

新namespace test_gem／package org.mocoserver.testgemの独立コピーでdatagen、check、build、verifyModJar、GameTest 2件が成功した。日本語と引用符を含むMOD名・作者は生成metadataと起動時に保存された。初期化scriptの9テスト（変更対象、dry-run、入力拒否、再実行、symlink、事前検査）は成功。

外部skillsは別コピーに実導入し、2 skillsのSKILL.mdと参照資料（各2ファイル）が固定sourceと内容一致した。再導入は固定SHAを表示し上書きしなかった。fake transportを含むNode 16テストが成功。skillsなしの初期化コピーでGradle check／build／GameTestが成功済み。

自作skillsの6件はskill-creatorのvalidatorを通過。指示なしbaselineの仮想加工案はval・素材保護・side分離を既に守ったが、core公開APIの明示的可視性を省略した。AGENTSとskillsを読んだ別agentの同一課題ではpublic／型付きのpure core案、サーバーでの検証・一括確定、root check、版固有API未確認の区別を確認した。この評価は読取りと設計案のみであり、Lunaでの実装試行やゲーム動作の評価とは扱わない。不要var、coreへのItemStack、client参照、別版API、素材不足時の消費を同じ課題に含めた。

Linux CI相当の最終コピーはexample_gem／org.example.gemへ初期化し、datagen、check、通常JAR内容検査、GameTest 2件が成功した。source・build出力は新規、Gradle依存cacheは既存の検証用cacheを再利用した。GitHub CIの結果は下記に記録する。

通常配布JAR＋作者のKotlinForForge配布5.5.0の2 MODだけで、NeoForge 21.1.252／Java 21の新規専用サーバーが12:33 UTCに起動完了（Done 3.534s）した。loaderの既存取得済みlibrariesを再利用したが、mods・ワールドは新規でGameTest fixtureを含めない。検証後に停止した。Maven開発aggregate単体ではlanguage provider不足になることも確認し、配布JARとの区別をcompatibilityへ記録した。

クライアント試遊は[検収手順](client-acceptance.md)と親packのdist/mod-template-trial ZIPを準備済み。JEIは[19.51.0.418](https://modrinth.com/mod/jei/version/ufHUqt9b)、Minecraft 1.21.1／NeoForge対応、client限定。取得物SHA512はAPIのa6ca3abc3080f6f41033a77cb955fefe9f9ebae795c33546227f555b2d31e8d4131cc705126d14a3fbabb31097fc0e876dc0bbeb05ad907a1b796f21e57527bfと一致。認証済みclientの表示・専用サーバー接続・実クラフトはユーザー検収待ちで、完全検収済みとは扱わない。

試遊JAR SHA256:

- mod_template-0.1.0-dev.jar: `a60e909b3dd03eec27f46a822da2b502109bba4969b656f8e4ea9bc78659d242`
- jei-1.21.1-neoforge-19.51.0.418.jar: `8bc3936d869e4040a5649c58b7e8be1c78c82a245c0dc07bdd68ab49121a222c`
- kotlinforforge-5.5.0-all.jar: `ab635fdbe07e9c803ce7ceae4b40898595415a387cf03f2ef4d2c6c3fa251435`

最終の読取り専用レビューはCritical 0／Important 5／Minor 0。初期化済みrepoのテストfixture、setup-nodeの不要なpnpm cache、skills-lock.jsonとpnpm出力先のsymlink検査、入力値の連続置換、Windows pnpm.cmd起動を修正した。修正前の初期化済みコピーは9件中3件失敗、追加の入力／symlink回帰は22件中6件失敗、Windows launcher回帰は2件失敗。修正後は元repoと初期化済みGitHub cloneのNode 29件が成功し、root check／build／verifyModJarも成功。Windows Git Bashでの実skills導入はGitHub CIの空白パス検証で確認済み。

pnpmの実cacheには内部symlinkがあるため、cache／store／state内へのリンクを許可し、その3 root外へ向くリンクを拒否する回帰も追加した。再導入は既存skillsを保存する。Windows実測ではnpm版pnpm 12.8.1の入口がbin/pnpm.mjsへ変わっていることを確認し、.mjs／.cjs／Corepackの配置を検査した。

GitHubの非公開template設定を確認し、[CI run 36962374558](https://github.com/moco-server/minecraft-mod-template/actions/runs/36962374558)はcommit 6f707b4でLinux／Windowsとも成功。LinuxはNode 29件、check、datagen差分0、build／verifyModJar、必須GameTest 2件、通常JAR artifactを実行した。Windowsは初期化／launcherテスト、check／build／verifyModJarと任意のGit Bash skills実導入を実行した。空白を含むrepoパスとローカルnpm導入pnpm 12.8.1の構成で2 skillsが導入できた。取得したCI artifactもcore class／metadataの存在とtest／script／docsの非同梱を確認した。

GitHubからcloneしたrepoをgithub_gem／org.mocoserver.githubgemへ初期化し、Node 29件、datagen、check／build／verifyModJar、必須GameTest 2件が成功。GitHub template生成の[minecraft-mod-template-smoke-20261002](https://github.com/moco-server/minecraft-mod-template-smoke-20261002)は非公開で保持する。生成直後の追跡blobとmodeは元templateと一致し、Windows wrapperの改行正規化もGit objectで照合した。生成先も初期化し、[CI run 36962425323](https://github.com/moco-server/minecraft-mod-template-smoke-20261002/actions/runs/36962425323)はcommit c25482fでLinux／Windowsとも成功。元repoと生成先の双方で品質検査・通常buildと、Linuxのdatagen／GameTestを確認できた。

実導入コピーでskills-lock.jsonをrepo外JSONへのsymlinkへ置換した検証は、CLI開始前に失敗し外部JSONを保存した。pnpm 12.8.1でのクリーン実導入と再実行は成功し、既存2 skillsは固定SHAを表示して保持された。判断一覧と見直し範囲は[実装判断](implementation-decisions.md)を参照。

WindowsのNodeテストは13件中12件成功・1件skip（POSIX symlink fixture）。Linuxの29件は全件成功。最終成果物へ追加した設計状態・検証記録・判断一覧・READMEの差分は文書のみで、検証済みコードcommit 6f707b4を維持している。

2026-10-02、skill補助資料の配置を整理した。規約をmod-kotlin/references/coding-conventions.md、開発手順をmod-implement/references/development.mdへ移し、skills／README／AGENTSのリンクと初期化allowlist・fixtureを更新した。6 skillsのvalidator、ローカルMarkdownリンク47件、Node 29件を確認した。MOD本体・Gradle構成は変更しておらず、この配置変更でゲーム起動を再検証してはいない。
