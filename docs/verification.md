# Sophisticated Gem Caseの検証

最新の下位収納実装の検証は末尾の「工程2」を参照する。以下の基盤検証は当時の記録として保持する。

## 対象と構成

2026-10-02の最初の開発区切り。初期化、依存設定、サンプルの維持、対象公開JARに対する調査用GameTestを検証した。**本アドオンのGemアップグレード・保存・画面・転送・加工・上位化は未実装**。下記はそれらの検収結果ではない。

Minecraft 1.21.1／NeoForge 21.1.252／Eclipse Adoptium Java 21.0.12.1／Kotlin 2.0.21／KFF 5.5.0。MODと関連依存は[対応構成](compatibility.md)・[依存manifest](dependency-manifest.json)に固定した。ホスト既定のJava 25はビルドに使っていない。

使用したJava 21は親packの既存JDKを読み取り利用。Gradle cacheは既存cacheをコピーしてこのrepoの`.local/gradle`へ置いた。専用サーバー用loader librariesと受諾済みEULAは既存検証環境からコピーし、mods・ワールド・設定・ログはこのrepoの新規環境へ用意した。クリーンなネットワーク取得やWindows CIの再検証とは扱わない。

生成元の検証実績は[template-verification.md](template-verification.md)へ由来付きで分離した。このMODで実行していない項目を引き継いで成功扱いしない。

## コマンドと結果

ローカルではJAVA_HOMEをJava 21へ、GRADLE_USER_HOMEを`$PWD/.local/gradle`へ指定し、JAVA_TOOL_OPTIONSに`-XX:ActiveProcessorCount=2`を使った。

| 検証 | コマンド／条件 | 観測結果 |
| --- | --- | --- |
| 初期化script | 承認された6項目でdry-run後に実行 | package・namespace・properties・metadata入力を初期化。作者samunohito |
| 初期化後の品質 | `./gradlew check` | BUILD SUCCESSFUL。Gem関連依存追加前の基準 |
| script | `node --test scripts/test/*.test.mjs` | 29件成功、失敗／skip 0。初期化前後を確認 |
| 品質・単体・境界 | `./gradlew check` | ktlint、detekt、core・buildSrc単体テスト、core境界検査が成功 |
| datagen | `./gradlew :neoforge:runData` | 専用dataDevelopment構成で成功 |
| datagen再生成 | 別呼び出しで再実行、生成5ファイルの一覧とSHA256を比較 | 追加・削除・内容差分0、written: 0。追跡／未追跡一覧も確認 |
| 通常JAR | datagenとは別呼び出しの`:neoforge:build :neoforge:verifyModJar` | core・namespace・metadata・必要resourceを含み、外部MOD・テスト・開発資料を含まない |
| GameTest | `:neoforge:runGameTestServer`と`node scripts/check-gametest-result.mjs` | 必須8件成功。ゼロ件成功ではない |
| 開発サーバー | `:neoforge:runServer`、新規.local/server、127.0.0.1:25580 | Done (12.536s)を確認。JEI／Gatewaysなし。確認後停止 |
| 通常JAR専用サーバー | Java 21と21.1.252のunix_args、通常JAR＋配布依存、127.0.0.1:25579 | Done (13.815s)。mods 10 JAR、GameTest fixture／JEI／Gatewaysなし。stopで保存終了、exit 0 |
| 開発クライアント | `:neoforge:runClient`、新規.local/client、WSLg表示環境 | NeoForge版JEI 19.51.0.418をMod Listで確認、JEI GUI atlasとゲームresource生成まで到達。確認後停止 |
| 取得物照合 | Modrinth APIのSHA512と実物を照合、GradleのModrinth Maven JARのSHA256も比較 | 開発用10成果物が配布manifestと一致。KFF配布JARもAPIと照合 |

## 公開JARのGameTestプローブ

既存サンプル2件（登録／数量、実recipe）と、IntegrationContractGameTestsの6件を実行した。

- Gem Case: 容量32,767、simulate無変更、上限超過時に余りを返さない契約、材料6枠と自動搬送からの非公開。
- Gem Case加工: 素材不足時にGemを消費せず、cracked 2個＋gem_dust 1個＋mysterious_scrap_metal 3個でchipped 1個になる。
- Pickup: 通常収納満杯時の残量返却、simulate時の無変更、実投入。
- 投入前フック: 通常収納満杯で既存と異なるアイテムの場合、InventoryHandlerSlotTrackerがbeforeInsertより前で戻る。
- Deposit／Restock: 装着済みwrapperの公開インターフェースでの発見と、個別転送メソッドによる4個の往復。
- Stack変換: targetは新規ItemStackであり、sourceの保存componentを継承しない。

上限超過とデータ非継承は元MODの契約を確認するためのプローブであり、本アドオンがその挙動を採用してよいという意味ではない。[調査結果](integration-research.md)に回避方針を記録した。

## 発見した問題と修正

1. Apotheosis 8.9.0のGatherDataEventがGatewaysの型を参照し、当初のdatagenはGatewayRegistryのNoClassDefFoundErrorになった。Gateways 5.1.0をdatagen限定へ追加した。
2. 実行別のadditionalRuntimeClasspathではGatewaysがMODとして検出されず、PlaceboのDynamicRegistryを解決できなかった。ModDevGradle 2.0.148のソースに基づき専用source setのruntimeへ移した。そのsource setにはmainのMinecraft runtimeも必要で、継承後にdatagenが成功した。
3. 調査用バッグはUUID未付与だと空のUpgradeHandlerだった。最初のGameTestは8件中2件が「Slot 0 not in valid range」で失敗。新規UUIDを持つ実バッグを用意し、満杯フックのプローブもslots > 0を前提として検査するよう修正した。再実行は8件成功。
4. JEIを共通版番号で取得するとFabric成果物が選ばれ、最初のclient起動でスキップされた。NeoForge版ID ufHUqt9bへ固定し、実JARのhash、Mod List、GUI atlas生成を確認した。

外部MODの任意連携先クラス欠落のWARN、clientの字幕・音声関連ログがある。これらをGemアドオンの不具合修正や画面・音声の検収済みとして扱っていない。client停止と開発サーバー停止は手動終了のため、Gradleの正常終了を成功基準に使っていない。

## 成果物と未検収

通常JAR: `neoforge/build/libs/sophisticated_gem_case-0.1.0-dev.jar`。この区切りのSHA256は`ea9bbc907450dbfae8e5ec04ce1b9bf9fad70266cef775b9fecfe7ebf173087f`。

試遊用の通常JAR＋依存は`.local/client-trial/mods`、手順は[client-acceptance.md](client-acceptance.md)。外部JAR、ログ、cache、ワールドはGitへ含めない。

未検収: 認証済みclient接続、見た目・日本語表示・JEI検索・実クラフト、Gemアドオン本体の全機能、pack共存、Windowsでのこの変更の実行。開発環境と通常JARの専用サーバー起動は確認できたが、完全な製品検収ではない。

## 独立レビュー

2026-10-02、実装担当とは別の`gpt-6-luna`サブエージェントへ、会話履歴を渡さず開始commit `d63bb18`からのstaged diff、計画、受け入れ条件、既存ログを渡した。変更全体、依存と実行別source set、metadata版、core境界、配布JAR検査、調査用GameTestと記録の整合をレビューした。Critical／Important／Minorの指摘なし。追加の大規模ビルドは行わず、既存の実測ログを確認した。

レビュー後は本記録と計画の完了チェックだけを更新した。最終`check :neoforge:build :neoforge:verifyModJar`もBUILD SUCCESSFUL、通常JARのSHA256は上記と一致。認証済みclient・画面操作・本機能・pack共存・Windowsの未検収はレビューによって解消したとは扱わない。


## 工程2: 下位Gem Case保存・投入・取出（2026-10-03）

2026-10-02承認の[下位収納仕様](superpowers/specs/2026-10-02-gem-case-storage-design.md)に従い、専用upgrade、種類×純度別32,767個の保存、公開IOと手動投入、一覧ページ通信、取出タブを実装した。構成とJava／cacheの指定は基盤検証と同じ。Pickup／Deposit／Restock連携、専用設定・フィルタ、上位加工・6材料枠・変換、通常入手recipeと最終textureは未実装。

### 実測

ログはGit管理しない`.local/verification/storage/`へ保存した。コマンドはJava 21、repo内Gradle cache、`-XX:ActiveProcessorCount=2`で実行した。

| 検証 | コマンド／ログ | 観測結果 |
| --- | --- | --- |
| core数量と所有権 | `:core:test :core:check`／core-green.log | 容量、余り、Int.MAX_VALUE、入力map・旧状態の無変更、外部からのmap変更拒否を確認 |
| 保存と装着 | `:neoforge:runGameTestServer`／storage-game-1.log | 必須13件成功。componentの永続往復、未知ID／純度保持、future／duplicate／巨大／深いNBTのロック、二個目拒否、取外し／同一item交換拒否、設置／回収、nativeバッグtier recipeの保存維持 |
| 投入 | `check :neoforge:runGameTestServer`／menu-insertion-2.log | 必須18件成功。専用→通常→余り、simulate無変更、追加component保存、通常slotからの部分移動、native左／右／Shift、ドラッグ・hotbar交換の無変更拒否、実item／block capability経由 |
| 画面の操作・wire | `check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`／menu-final-game.log | BUILD SUCCESSFUL、必須26件成功、結果parserも26件。取出一個／最大stack、player部分空き／満杯、旧revision・再送・閉鎖・upgrade差替え拒否、cursor投入・一括移動、ページ集約／重複／旧revision、65件・256byteキーの分割、wire32KiB以下、257byte／不正action拒否 |
| 最終受入 | `check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`／final-acceptance.log | BUILD SUCCESSFUL、必須31件成功。追加のバッグUUID差替え／距離／未解決entry、UUIDなしcapability、実Coreフィルタ、native失敗cache、方向制限を検査。parserも31件 |
| datagen | `check :neoforge:classes :neoforge:runData`／menu-check-data.log | BUILD SUCCESSFUL。item modelとja_jp／en_usを生成。main classesを先に生成して実行 |
| datagen再実行 | `:neoforge:runData`／datagen-repeat.log | BUILD SUCCESSFUL、written: 0。生成6ファイルの一覧とSHA256比較で差分0、追跡／未追跡を確認 |
| 通常JAR | `:neoforge:verifyModJar` | core個数モデル、storage、menu、Mixinクラス／設定、model／langを含む。外部MOD、fixture、skills、docsを含まない |
| 通常JAR専用server | `.local/storage-server`、127.0.0.1:25579／ordinary-server.log | 通常JAR＋固定依存の10 JAR、JEI／Gateways／fixtureなし。Done (10.674s)、新規storage-verification world、save-all flush／stop、exit 0 |
| 保存と再起動 | 保存worldをstorage-verification-restartへコピー／ordinary-server-restart.log | Done (4.327s)。コマンドで保存したupgrade itemのcomponentが、再起動後も`missing:retained`×`future`の7個を保持。data getで観測後stop、exit 0。実playerが装着したバッグの手動再起動検収とは分ける |
| 開発client | `:neoforge:runClient`／client-startup.log | WSLg環境、NeoForge版JEI 19.51.0.418を検出、ゲームresourceとJEI GUI atlas生成に到達。確認後中断。表示／実操作／認証接続は未確認 |

未実装APIのテスト失敗をcore-red.log／storage-red.log／insertion-red.log／menu-actions-red.logへ記録し、各実装後に成功を確認した。後から追加した境界テストは既存実装の検証であり、すべてのテストを個別にredから作ったとは扱わない。

### 版固有の修正と制約

NeoForge 21.1.252／loader 4.0.44で、KFF 5.5.0のKotlin objectイベント登録はinstance登録である。`@JvmStatic`を付けるとstatic methodをinstanceへ登録するエラーになり、外した。さらにKFFの既定GAME登録がloaderから削除されたBindingsを参照してdatagen起動に失敗するため、client専用subscriberへMOD busを明示した。deprecated enumの抑制はその宣言だけで、固定JARと失敗ログに基づく回避である。修正後datagen・専用server・client起動を確認した。[KFF作者repo](https://github.com/thedarkcolour/KotlinForForge)、[対象版payload資料](https://docs.neoforged.net/docs/1.21.1/networking/payload/)を参照し、別版への更新時は登録方式を再確認する。

raw NBTは通常slotのネットワークcodecへ送らず、中身あり／ロックの二つのbooleanだけを同期する。詳細一覧はserver snapshotのページだけを使う。client要約をserverで受け取っても保存更新に使わない。破損原文はboundedな非変更viewで調べ、予算確認前にcopyしない。client専用型はclient packageに隔離した。

通常Gemのドラッグと数字キー交換は、複合操作を部分変更しないため変更前に拒否する。投入にはクリック／Shiftまたはタブボタンを使う。拒否時に案内を表示する。外部capabilityの同tickのnative失敗cacheについては次tickの再試行を前提とする。方向制限の検証では`.local/gametest/config/sophisticatedbackpacks-server.toml`の`noConnectionBlocks`を`minecraft:stone`へ設定して起動した。共有設定をGameTest中に書き換えず、別起動の入力として渡し、検証後に元の空リストへ復元した。

### 独立レビューと残る検収

実装・テスト追加の担当とも別の`gpt-6-luna`（medium、履歴なし）へ、172a8cdからの全実装diff、仕様、計画、固定版source、実測ログを渡した。raw保持、所有権、余り、slot／filter制限、menuの世代と装着照合、取出先の事前計画をレビューし、Critical／Important／Minorの指摘なし。追加のIO／session境界テストと最終31件成功も同担当が追認した。結果は`.local/verification/storage/review.md`。レビューは実操作を代替しない。

未検収は[client-acceptance.md](client-acceptance.md)の画面の表示・日本語・検索／スクロール・実クリック、認証済み接続、実playerの装着バッグを使う再起動、pack共存、Windows。GUI操作用の自動入力環境は用意していない。GameTestによるmenu操作と起動成功から、これらを成功とは推定しない。

通常JAR: `neoforge/build/libs/sophisticated_gem_case-0.1.0-dev.jar`。SHA256: `aff454e878925b35c4e1cfc5242aa85acb7cbef71039a3b7112a8203251f3bdd`。試遊用`.local/client-trial/mods`もこのJARへ更新した。外部JAR、cache、ワールド、ログはGitへ含めない。
