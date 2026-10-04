# Sophisticated Gem Caseの検証

各工程の実装・最終検証と未検収項目は、対応する工程の節を参照する。以下の基盤検証は当時の記録として保持する。

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


## 工程3: Pickup／Deposit／Restock連携と転送設定（2026-10-03〜04）

[承認済み仕様](superpowers/specs/2026-10-03-gem-case-integration-design.md)に従い、処理用inventory wrapperによるPickup接続、native通常転送後の専用Deposit／Restock、二方向のON／OFFと種類・純度フィルタ、世代付き通信とタブ内設定画面を追加した。初期設定は両方向OFF／ALL。上位加工・材料枠・装着中の上位化・通常入手recipe・最終textureは後続工程。構成はMC 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0と固定依存manifestを維持する。

### 保存・転送の契約

個数schema 1は維持し、設定を独立した`gem_transfer_settings` componentのschema 1で保存する。設定不在はDEFAULT、未知形式や不正型はrawを保持して自動転送と設定上書きを止める。正常な個数の手動操作は維持する。設定詳細の通常ItemStack wireは実測1byteのlocked booleanで、全設定NBTを送らない。64 entry／32KiBの独立ページを使い、countsとsettingsのsession／revisionが揃うまでclientの編集を禁止する。

公開IOの専用配分後は同バッグの内側processing wrapperの配分を抑止し、simulateでも容量を二重に使わない。専用転送の外部callback中はバッグUUID単位のexclusive文脈と短命permitで数量・設定の通常変更と取り外しを止め、別バッグの操作は許可する。finallyで文脈を解除する。native Restockのoriginal全体はrouting抑止下で実行し、OFF／除外でも通常Restockが専用へ入り込まない。専用判定はGem側フィルタだけ、通常判定はnative側フィルタだけを使う。

専用転送は最大512成功stack／16,384 handler slot訪問で止まり、残りを元へ残す。Restockの候補走査は先頭8,192slotまでとし、残りの予算をsimulate／実extract／必要な復元に確保する。巨大handlerの後半slotを全走査する保証や走査cursorはない。Depositは予約減算後に実insertの余りを戻し、前のslotが部分受入した後に次のslotが無変更で例外を投げても、未受入分だけを復元する。Restockは実extract量だけを加える。契約に従うhandlerの部分受入／部分extract／EMPTY拒否では数量を保存する。外部handlerが変更後に例外を投げる、異なるstackを返す、復元を拒否する等の契約違反まで全体rollbackを保証せず、失敗を握り潰さない。

### 固定loaderの起動修正

KFF 5.5.0のloader範囲依存`[3,5)`からFML 4.0.45が選ばれ、NeoForge 21.1.252のbootstrap側4.0.44と`fml_loader` module名が衝突した。`dependencyInsight --dependency net.neoforged.fancymodloader:loader --configuration gametestRuntimeClasspath`の実測で確認し、version catalogのstrict 4.0.44とimplementation constraintで揃えた。[KFF 5.5.0のPOM](https://thedarkcolour.github.io/KotlinForForge/thedarkcolour/kfflang-neoforge/5.5.0/kfflang-neoforge-5.5.0.pom)、[NeoForge 21.1.252のPOM](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.252/neoforge-21.1.252.pom)が固定版の根拠。sandbox外実行・新daemonでは解消せず、legacy classpathから4.0.44を単に除く案もbootstrap欠落で失敗したため採用していない。変更後にGameTestが実行された。loader本体はアドオンJARへ同梱しない。

coreフィルタ、Pickup、専用転送、設定APIには実装前のREDを記録した。Task 1 loader保存側は実装先行で、先行REDは未実施。後から追加した境界テストは既存実装の検査として扱う。途中の66件実行では63件が成功し、3件のfixture／assertion誤りを修正して最終検証へ進んだ。


### 最終実測（2026-10-04）

ログは`.local/verification/integration/`、Java 21.0.12.1、`GRADLE_USER_HOME=$PWD/.local/gradle`、`JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`を使用。

| 検証 | コマンド／ログ | 観測結果 |
| --- | --- | --- |
| script | `node --test scripts/test/*.test.mjs`／script-tests.log | 29件成功 |
| 翻訳生成 | `ktlintFormat :neoforge:classes :neoforge:runData`／combined-data-2.log | BUILD SUCCESSFUL。生成6ファイル中ja_jp／en_usだけ変更 |
| 結合検証と通常JAR | `ktlintFormat check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`／resume-acceptance-5.log | BUILD SUCCESSFUL、必須72件成功。ktlint／detekt／単体／core境界／buildSrc／JAR内容検査も成功 |
| UUID境界追加後 | `ktlintFormat check :neoforge:runGameTestServer`／uuid-final.log | BUILD SUCCESSFUL、必須73件成功。`node scripts/check-gametest-result.mjs`も73件。本番は結合検証以後変更なし、実バッグUUID差替えの設定拒否テストだけ追加 |
| datagen再実行 | `:neoforge:runData`／datagen-repeat.log | BUILD SUCCESSFUL、written: 0。全6ファイルの一覧とSHA256比較で差分0。追跡／未追跡を確認、生成lang以外に差分なし |
| 通常JAR専用server | `.local/integration-server`／ordinary-server.log | 通常JAR＋固定依存の10 JAR、JEI／Gateways／fixtureなし。127.0.0.1:25579、新規integration-verification world、Done (10.597s)、save-all flush／stop、exit 0 |
| コピー再起動・保存 | integration-verification-restart／ordinary-server-restart.log | Done (3.593s)。保存したupgrade itemのcounts `missing:retained`×`future`の7個、Deposit ON／INCLUDEの同ID・純度、Restock OFF／ALLをdata getで確認。stop、exit 0。元worldは上書きしない |
| 開発client | `:neoforge:runClient`／client-startup.log | WSLg環境。JEI 19.51.0.418をMod Listで検出し、resourceとJEI GUI atlas生成へ到達。確認後中断（Gradle正常終了として扱わない）。表示／クリック／認証接続は未実施 |

初回serverのsummon直後にまとめて送ったdata getは`No entity was found`で、当時の照会は保存確認に使わない。コピー再起動後、読み込み完了してから単独で送ったdata getにより両componentを観測した。これはupgrade item entityの保存往復であり、実playerが装着したバッグの手動再起動検収を代替しない。外部MODの任意連携先クラス欠落のWARNとclient音声関連ログは残り、今回のGUI検収成功とは扱わない。

通常JARへcoreフィルタ、設定codec、processing handler、専用転送、bridge、三つの新Mixin、設定request／client widget、必要langを含める検査を追加した。外部MOD本体、GameTest fixture、skills、docsは同梱しない。SHA256は`de388e36fea05439b05d6fb5e56ca19e34acb41489bde2d24cc00f4e10624456`。試遊用`.local/client-trial/mods`も更新した。依存JARの一覧とhashは`ordinary-mods.json`へ保存し、固定manifestと照合した。

### 独立レビューと未検収

実装担当とは別の`gpt-6-luna`（high、会話履歴なし）が、45aec1cからの全実装diffと新規未追跡コード、承認spec／計画／規約／固定作者sourceを読んだ。数量保存・再入とpermit・例外解除・通常Restock迂回・raw同期・menu権限／世代・wire予算を確認し、Critical／Important／Minorの指摘なし。CodeRabbit CLIの0件は新規未追跡ファイルがサービス側の対象外だったため、それらをすべて独立担当が手読みに含めた。最後の実バッグUUIDテストと73件成功も追認。結果は`.local/verification/integration/review.md`。

未検収は[client-acceptance.md](client-acceptance.md)のGUI実操作、日本語表示・種類検索・スクロール・連携を実playerで操作すること、認証済み接続、装着バッグの手動再起動、pack共存、Windows。GameTestと起動確認からこれらの成功を推定しない。

工程3の実装・記録は2257de4へコミットし、ローカルmainへfast-forward統合した。検収branchとmainのcommit／tree一致とclean作業木を確認し、同じ本番treeの広範囲検証は重複実行しない。push／packへの導入は行っていない。


## 工程4: 上位加工・専用材料枠・装着中の上位化（2026-10-04、実装・自動検証済み）

[承認済み仕様](superpowers/specs/2026-10-04-gem-case-processing-design.md)と[実装計画](superpowers/plans/2026-10-04-gem-case-processing.md)に従う。構成と依存JARは工程3の固定版を維持する。検証ログは`.local/verification/processing/`へ置く。

| 検証 | コマンド／ログ | 観測 |
| --- | --- | --- |
| core allocator／純度計画 | core-green-2.log／core-negative-slot-red.log | 単体・境界検査成功後、独立レビューで負slot拒否を追加。追加前20件中1件失敗を観測し、修正して成功 |
| 材料保存と上位登録 | material-summary-final.log | root checkと必須84 GameTest成功。Task 1は9845ece、Task 2はc92b16dへコミット |
| 結合の先行テスト | material-click-runtime-red-2.log | compile成功、必須14件失敗。保存例外時の補充設定、exclusive中simulate、変換とnative材料クリックの失敗を再現。これを成功結果として扱わない |
| 保存後照合の追加RED | post-save-raw-red-2.log | 130件中11件失敗。新規テストは保存後epoch変更時に材料component未存在が空componentへ変わることを再現。保存後照合を保存try内部へ移し原文を復元 |
| native装着判定のRED | native-tag-red.log | 両版のnativeタグ判定テストは下位タグ不足で失敗。直接set fixtureが通常装着条件を迂回する点を発見 |
| nativeタグ生成 | `:neoforge:runData`／native-tag-data.log | BUILD SUCCESSFUL、9resource／written 1。作者`sophisticatedbackpacks:upgrade`へ上下両版を追加。生成物を手編集せず追跡／未追跡を確認 |
| nativeまとめ回収のRED | native-pickup-all-red.log／orphan-material-pickup-red-5.log | 通常slotからのPICKUP_ALLで専用材料が回収されることを再現。現在の材料とレシピ消失後の保存材料を保護 |
| 全移動先の復元RED→GREEN | multi-destination-recovery-red.log／multi-destination-recovery-green.log | 133件中1件で復元callback例外時に先行slotが残る不具合を再現。全slotの復元継続・例外集約後、必須133件成功。parserも133件 |
| 加工画面の翻訳生成 | `:neoforge:runData`／processing-data.log | BUILD SUCCESSFUL、8resource中lang 2ファイルwritten。追跡／未追跡差分を確認 |

材料のクリックを通常のSlot.setだけへ委ねると、保存callback失敗とcursor変更が別々になるため、専用確定処理を接続する。加工viewの開閉はmenu内だけの状態とし、typed `SET_PROCESSING_VIEW(enabled)`で同期する。現在menu・装着現物・UUID・session nonceを照合するが、保存変更のない開閉だけは古いrevisionでも受け付ける。閉じる通知を加工quotaで落とさない。加工・補充設定の更新は従来どおり最新revisionとquotaを要求する。

### 最終検証

Java 21.0.12.1／NeoForge 21.1.252と既存の固定runtime依存を使用した。

| 検証 | コマンド／ログ | 観測結果 |
| --- | --- | --- |
| script | `node --test scripts/test/*.test.mjs`／script-tests.log | 29件成功 |
| 例外再使用の確認 | repeated-exception-red.log／engine-bytecode.txt／kotlin-suppressed-bytecode.txt | 追加3件を含む136件成功。このログ名は先行REDを意図したが失敗は再現しなかった。固定Kotlin 2.0.21は同じThrowableのsuppressionをskipする。根拠のない本番変更は追加しない |
| recipe予算のRED | recipe-budget-red.log | 137件中1件失敗。補充の再走査で共有512 recipe予算を無視することを再現し、共通counterを消費するよう修正 |
| 一括移動のRED | bulk-transfer-red.log | 140件中4件失敗。上記予算と、通常保存例外時の全slot復元・Gem保存原文復元・再入拒否を再現。上位にも継承される一括移動をexclusive permitと復元処理へ接続 |
| 整形・生成再現性 | `ktlintFormat :neoforge:runData`／final-format-data-2.log | BUILD SUCCESSFUL、provider 9ファイル、written 0。生成先の全14ファイルの一覧／SHA256比較で差分0。追跡／未追跡を確認 |
| 最終ビルド・結合検証 | `ktlintFormat check :neoforge:build :neoforge:verifyModJar :neoforge:runGameTestServer`／final-acceptance-3.log | BUILD SUCCESSFUL、必須140件成功。parserも140件。ktlint／detekt／単体20件／core境界／buildSrc／JAR内容検査が成功 |
| 通常JAR専用server | ordinary-server.log | 通常JAR＋固定依存の10 JAR。JEI／Gateways／fixtureなし。127.0.0.1:25579、新規processing-verification、Done (9.371s)。reload成功、上位itemの4componentをdata getで観測。save-all flush／stop、exit 0 |
| コピー再起動・保存 | ordinary-server-restart.log | processing-verification-restart、Done (3.909s)。Gem counts `missing:retained`×`future` 7個、Deposit ON／INCLUDE・Restock OFF／ALL、材料slot0 STONE 3個／slot5 DIAMOND 2個、補充ONを再観測。save-all flush／stop、exit 0。元worldを上書きしない |
| 開発client | `:neoforge:runClient`／client-startup.log | WSLg、JEI 19.51.0.418をMod Listで検出し、resource／JEI GUI atlas生成へ到達。起動観測後Ctrl-Cで中断、exit 1をGradle成功とは扱わない。表示・クリック・認証接続は未実施 |

保存往復のprobeは上位upgradeのitem entityであり、実playerが装着したバッグの保存再起動を代替しない。レシピから外れた材料の保存保持も確認するためSTONEとDIAMONDを使った。実操作の未検収は[client-acceptance.md](client-acceptance.md)に分ける。外部MODの任意連携先欠落WARNとclient音声関連ログは起動記録に残る。

通常JARは `468a4996f9887a4da8297097c43cddd34a0ee4a28d7af8d3c6e0731553960920`。必須entryにcore allocator／純度計画、材料codec／handler、engine／conversion／request／client panel／Mixin accessor／上下itemタグを含め、外部MOD本体・fixture・docs・skillsを除外する。`.local/client-trial/mods`も同じJARへ更新した。固定依存9 JARのSHA256は前工程manifestと全件一致し、通常JARを含む10 JARの一覧とhashをordinary-mods.jsonへ記録した。

補充探索は512 recipe照会（専用・補充の両走査で共有）、4,096候補pair、16,384 Ingredient判定、8,192通常slotまで。通常slot数が8,192を超えるhandlerでは補充を拒否する。外部callbackが復元自体も拒否する場合は全slotの復元を試み、元例外へ回復例外を保持して送出する。任意の外部handlerの永続化成功を保証するものではない。操作後に外部callbackが前提を変更した加工は確定せず、自身のrawと通常slotを復元する。

### 最終レビューと未検収

実装担当とは別の履歴なし `gpt-6-luna` / high が、main 85608fcからの全実装diffと新規コード、承認仕様／計画、固定作者ソースと実測ログを確認した。独立レビューの記録はfinal-independent-review.md。recipe予算と上位にも共有される一括移動の問題は先行失敗テスト後に修正した。例外自己suppressionの候補は、Kotlinのbytecodeと136件成功を根拠に撤回した。材料slotのnative click、変換、数量保存、raw復元、再入・UUID・世代境界、client panelが材料clickを消費しないことも再確認した。

CodeRabbit CLI 0.8.2では未追跡コードもstageへ含めて確認した。初回4 Minor（bulk rollback、設定から加工viewへの切替、軸button切替、古い検証案内）は根拠を照合し修正した。再レビューも完了し4 Minor（既存counts summaryの型検証、既存transfer設定の保存例外復元、view開閉metadataのquota、計画書の旧状態文）を確認した。旧状態文は修正した。summaryはGemCountsへ変換せず変更を拒否し、transfer設定の例外は個数・材料・出力を変更しないため、既存2件の改善は後続へ記録する。view metadataは権限／nonceを照合した保存を伴わない表示変更で、closeを確実に同期する既存設計を維持する。独立担当も3件をMinorと評価し、Critical／Importantの未解決なし。CodeRabbitの提案文やコマンドは実行せず、コードとログを根拠に判断した。

未検収はGUI実操作、日本語／英語表示、認証済み専用server接続、装着バッグの手動再起動・設置回収・tier変更、pack共存、Windows。起動やGameTestから成功を推定しない。通常入手レシピと最終見た目は工程5で扱う。

工程4は54b75f7へコミットし、既存のユーザー承認に従ってローカルmainへfast-forward統合した。統合時のmainと検収branchのcommit／tree一致（d19cb45eb50253e4b42af4f06ce06d9fa545b38d）とclean作業木を確認した。以後の記録更新は文書のみで、本番と通常JARは同じ。push・pack導入は含めない。

## 工程5: 通常入手recipe・三アイコン・保存境界改善（2026-10-04）

[承認spec](superpowers/specs/2026-10-04-gem-case-acquisition-design.md)と[実装計画](superpowers/plans/2026-10-04-gem-case-acquisition.md)に従う。固定版とruntime依存を維持し、core・schema・加工コストは変更しない。通常recipeは下位と変換の二つだけで、保存済み収納itemを消費する通常上位craftを追加しない。ログは`.local/verification/acquisition/`。

| 検証 | ログ／手順 | 観測 |
| --- | --- | --- |
| 保存の先行RED | storage-red.log | compile成功、143件中追加3件失敗。不正summary受容、設定raw変更、回復失敗のsuppression欠落を再現 |
| 保存GREEN | storage-green-2.log | ktlintFormat／root check／必須143 GameTest成功、parser143。初回storage-green.logはアイコンproviderの行長で停止し、整形して再実行 |
| recipe先行RED | recipe-red.log | compile成功、147件中追加4件が二recipe未存在を理由に失敗 |
| アイコン生成 | icon-data.log | runData成功、provider 9ファイル／written 3。三モデルを自身のtextureへ切替 |
| recipe生成 | recipe-data.log | ktlintFormat／classes／runData成功、provider 13ファイル／written 4。二recipeと対応二advancement。tracked／untracked双方を確認 |
| 最終結合 | recipe-icons-green.log | ktlintFormat check build verifyModJar runGameTestServer成功。必須147件とparser147。既存140件、追加保存3件・recipe4件。core単体20件とbuildSrc／core境界／解析を含む |
| 生成再現性 | data-repeat.log、resources-before/after-repeat.json | runData成功、written 0。cacheを除く全13配布resourceのpath／SHA256一致、tracked／untracked確認済み |
| 通常JAR | verifyModJar＋zip内容照合 | 三モデルlayer0と三PNGが一致。二recipe／二advancementも必須entry。外部MOD・tests・docs・skills・art原稿は同梱しない |
| 専用server | ordinary-server-console.log | 通常JAR＋固定依存9 JAR、JEI／Gateways／fixtureなし。127.0.0.1:25580、新規acquisition-console-verification、Done 10.429s。reloadとsummon、save-all flush／stop、exit 0 |
| コピー再起動 | ordinary-server-restart.log | 元world停止後のacquisition-verification-restart、Done 3.905s。forceload add 0 0後にprobe四componentを照会。save-all flush／stop、exit 0 |
| dev client | client-startup.log | 開発限定JEI 19.51.0.418、resourceとJEI GUI atlas生成まで到達。Ctrl-Cで観測を終了しexit 1、Gradle成功とは扱わない。GUI操作未実施 |

通常JAR `neoforge/build/libs/sophisticated_gem_case-0.1.0-dev.jar` のSHA256は `fda04d1283cd6858c76b75fc30a9ba156b37c39f93c09fad664075cd7b711853`。固定依存9 JARのhashは工程4のmanifestと一致し、同じ本体JARを.local/acquisition-serverと.local/client-trialへ配置した。10 JAR一覧はordinary-mods.json。

保存probeはGem `missing:retained`／`future` 7個、Deposit ON／INCLUDE、Restock OFF／ALL、材料slot0 STONE 3個／slot5 DIAMOND 2個、補充ON。コピー再起動後に全値を確認した。初回console側の照会はchunk未読込で`No entity was found`、成功証拠には使わない。コピー側で明示的にchunkを読み込んだ後のdata getを使う。item entityの保存往復であり、実player装着バッグの検収を代替しない。

初回ordinary-server.logの起動（Done 10.048s）は標準入力が閉じており検収コマンドを送れなかった。対象cwdとJava PIDを特定してSIGTERM、保存shutdownを観測したがexit 143なので正常stop成功とは記録しない。別の新規worldとTTYコンソールで検収をやり直した。既存／元worldを上書きしない。外部MODの任意連携先欠落WARNとclient音声関連ログは起動記録へ残る。

三PNGは自作の16×16 RGBA透過pixel原稿。原寸と8倍previewを目視し、独立担当もケース／工具付きケース／強化部品のシルエットとresource参照を確認した。制作scriptはPython標準ライブラリのみで、通常buildはPythonにも原稿にも依存しない。材料の型・ID、タグ、shaped builder、criterionは固定MC 1.21.1／NeoForge 21.1.252の実クラスと[固定作者recipe](superpowers/specs/2026-10-04-gem-case-acquisition-design.md#固定一次資料と確認)を照合した。別objectからprotected RecipeProvider.hasは呼ばず、公開InventoryChangeTrigger.TriggerInstance.hasItemsを使う。

### 独立レビューと未検収

Task1とTask2は実装者とは別のgpt-6-sol／medium、画像は別のgpt-6-luna／highが審査し、仕様・品質適合、未解決指摘なし。最終全diffは別履歴のgpt-6-astra／mediumが審査し、Critical／Important／Minor 0件。storage-review.md、recipe-review.md、icon-review.md、final-review.mdへ証拠と限界を記録した。自己suppressionの候補は、固定Kotlin 2.0.21 helperのidentity skipと現wrapper bytecodeを根拠にレビュー担当が撤回した。不要なhelper変更は追加しない。

工程5の実装は579252d／80763a8。recipeとiconは共通provider・JAR必須entryを一緒に検収したため一コミットにまとめた。実行は既存承認済みfeature branchで行い、新規worktree・重複cacheを作らなかった。差分分離とrollbackの単位がrecipe／iconで共通となる点を記録する。

未検収はJEI二recipeの実表示／手動craft、三アイコンのGUI scale 2／3／4・日本語／英語、収納・転送・加工・補充・変換の実操作、認証済み通常JAR接続、装着バッグの手動再起動・設置回収・tier変更、pack共存、Windows。[client-acceptance](client-acceptance.md)の空欄を起動やGameTestで埋めない。次工程への通常JAR・hash・固定依存・手順を[pack引き渡し](pack-handoff.md)へまとめる。

CodeRabbit CLI 0.8.2の追加レビューは完了（coderabbit.log）。READMEの未実装表記というMinor一件のみで、実装済みrecipe／iconと未検収GUI／認証を区別する現在の記述へ修正した。サービスで除外されるPNGは独立画像レビューとJAR照合へ含めた。レビュー提案の命令文は実行せず根拠を照合した。文書追認でのmain統合チェック先取りも未完へ戻し、実際の統合確認後に更新する。

工程5は9af536bへ記録し、承認済みのローカルmainへfast-forward統合した。統合直後のmainと検収branchのcommit／tree一致（b013995bf00ac4d7054acda2b50d97e4e5a7a5c2）とcleanを確認。以後の統合記録更新は文書のみで、147件成功時の本番tree・JARは不変。push／packへの変更は行っていない。
