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

2026-10-02承認の下位収納仕様に従い、専用upgrade、種類×純度別32,767個の保存、公開IOと手動投入、一覧ページ通信、取出タブを実装した。構成とJava／cacheの指定は基盤検証と同じ。Pickup／Deposit／Restock連携、専用設定・フィルタ、上位加工・6材料枠・変換、通常入手recipeと最終textureは未実装。

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

承認済み仕様に従い、処理用inventory wrapperによるPickup接続、native通常転送後の専用Deposit／Restock、二方向のON／OFFと種類・純度フィルタ、世代付き通信とタブ内設定画面を追加した。初期設定は両方向OFF／ALL。上位加工・材料枠・装着中の上位化・通常入手recipe・最終textureは後続工程。構成はMC 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0と固定依存manifestを維持する。

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

承認済み仕様と実装計画に従う。構成と依存JARは工程3の固定版を維持する。検証ログは`.local/verification/processing/`へ置く。

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

承認specと実装計画に従う。固定版とruntime依存を維持し、core・schema・加工コストは変更しない。通常recipeは下位と変換の二つだけで、保存済み収納itemを消費する通常上位craftを追加しない。ログは`.local/verification/acquisition/`。

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

三PNGは自作の16×16 RGBA透過pixel原稿。原寸と8倍previewを目視し、独立担当もケース／工具付きケース／強化部品のシルエットとresource参照を確認した。制作scriptはPython標準ライブラリのみで、通常buildはPythonにも原稿にも依存しない。材料の型・ID、タグ、shaped builder、criterionは固定MC 1.21.1／NeoForge 21.1.252の実クラスとApotheosis 8.9.0とBackpacks 3.26.6.2174の配布JARを照合した。別objectからprotected RecipeProvider.hasは呼ばず、公開InventoryChangeTrigger.TriggerInstance.hasItemsを使う。

- [作者Gem Case recipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/generated/resources/data/apotheosis/recipe/gem_case.json)
- [作者Gem Cutting Table recipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/generated/resources/data/apotheosis/recipe/gem_cutting_table.json)
- [固定版のUpgrade Base登録](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/init/ModItems.java)

### 独立レビューと未検収

Task1とTask2は実装者とは別のgpt-6-sol／medium、画像は別のgpt-6-luna／highが審査し、仕様・品質適合、未解決指摘なし。最終全diffは別履歴のgpt-6-astra／mediumが審査し、Critical／Important／Minor 0件。storage-review.md、recipe-review.md、icon-review.md、final-review.mdへ証拠と限界を記録した。自己suppressionの候補は、固定Kotlin 2.0.21 helperのidentity skipと現wrapper bytecodeを根拠にレビュー担当が撤回した。不要なhelper変更は追加しない。

工程5の実装は579252d／80763a8。recipeとiconは共通provider・JAR必須entryを一緒に検収したため一コミットにまとめた。実行は既存承認済みfeature branchで行い、新規worktree・重複cacheを作らなかった。差分分離とrollbackの単位がrecipe／iconで共通となる点を記録する。

未検収はJEI二recipeの実表示／手動craft、三アイコンのGUI scale 2／3／4・日本語／英語、収納・転送・加工・補充・変換の実操作、認証済み通常JAR接続、装着バッグの手動再起動・設置回収・tier変更、pack共存、Windows。[client-acceptance](client-acceptance.md)の空欄を起動やGameTestで埋めない。次工程への通常JAR・hash・固定依存・手順を[pack引き渡し](pack-handoff.md)へまとめる。

CodeRabbit CLI 0.8.2の追加レビューは完了（coderabbit.log）。READMEの未実装表記というMinor一件のみで、実装済みrecipe／iconと未検収GUI／認証を区別する現在の記述へ修正した。サービスで除外されるPNGは独立画像レビューとJAR照合へ含めた。レビュー提案の命令文は実行せず根拠を照合した。文書追認でのmain統合チェック先取りも未完へ戻し、実際の統合確認後に更新する。

工程5は9af536bへ記録し、承認済みのローカルmainへfast-forward統合した。統合直後のmainと検収branchのcommit／tree一致（b013995bf00ac4d7054acda2b50d97e4e5a7a5c2）とcleanを確認。以後の統合記録更新は文書のみで、147件成功時の本番tree・JARは不変。push／packへの変更は行っていない。

## Windows Prism試遊環境の設置（2026-10-04）

ユーザー指定のPrism配置へ、新規 `moco-gem-case-trial-0.1.0-dev-client` を作成した。zinc試遊の公開component metadata（MC 1.21.1／NeoForge 21.1.252／LWJGL 3.3.3）だけを参照し、instance.cfgとoptionsを新規作成。旧インスタンス・保存world・認証情報・グローバル設定をコピー／変更しない。日本語／GUI scale 3、Windows付属Microsoft Java 21.0.7の実在と版を実測した。

通常本体＋固定runtime9＋JEIの11 JARをrepo内stagingでhash照合し、独立の履歴なしgpt-6-luna／highが構成・hash・fixture不在・world／認証情報不在を確認した。JavaPathの固定配置への注意は対象Windowsでjavaw.exe実在と同runtimeのjava.exe -versionを確認して解消した。レビューは.local/verification/windows-prism/review.md。

Windows新規instanceへのコピー後も全11hashとmetadata／optionsの一致を再確認した。本体SHA256は工程5の `fda04d1283cd6858c76b75fc30a9ba156b37c39f93c09fad664075cd7b711853`。既存destinationがあれば上書きを拒否する設置で実行し、worldを持ち込まなかった。

Windows版Prismをサーバー指定なしで起動。2026-10-04 22:58のnative logで本体・JEIの発見、NVIDIA GeForce RTX 4060 Ti／OpenGL 4.6.0 NVIDIA 617.14、OpenAL、JEI GUI atlas生成を観測し、確認時147行のERROR／FATALは0件。raw起動ログはWindows instanceのminecraft/logs/latest.log、必要な起動行だけの要約は.local/verification/windows-prism/startup-summary.txt。認証情報やplayer識別子を検証記録へ転記しない。

ゲームを起動したままユーザー試遊へ引き渡した。手順とinstance情報は[client-acceptance](client-acceptance.md#windows-prism試遊2026-10-04)。Windows native起動を確認したが、ゲーム内実操作／表示のユーザー検収、認証済み専用server接続、装着バッグ保存往復、pack共存は未検収。本番／JARを変更していないため同じ147件のビルド・GameTestは重複実行しない。

## 中央Gem Case画面の再設計（2026-10-25〜26）

2026-10-25承認のUI設計と計画に従い、文字一覧と独立加工viewを、右のアイコンから直接開く232×230 GUI pxの中央表示へ置き換えた。素材6枠、種類6列×3行、純度6枠、強化5ボタン、プレイヤーinventory、転送設定を扱う。保存schemaと加工確定の契約は維持する。通信protocolは2で、client／serverは同じ本体JARを使う。

### 自動検証と配布物

作業用worktreeは`/tmp/sgc-ui-20261025`、固定JavaはEclipse Adoptium JDK 21.0.12.1、cacheはrepoの`.local/gradle`。検証ログは`.local/verification/ui/`と`.local/ui-verification/`へ保存する。

| 検証 | 観測結果 |
| --- | --- |
| 変更前baseline | root check成功。初回にJREだけのJavaを指定してtoolchain検出に失敗し、JDKへ修正した |
| 一覧モデル先行検証 | 未実装によるRED後、検索・選択保持・所持優先・最後の行・未解決entryの3単体テスト成功 |
| preview先行検証 | 新API未定義のRED後、5出力の世代／選択連番、wire roundtrip・最大件数・合計byte、共有512recipe探索予算、旧種類／旧revision拒否をGameTestで確認 |
| datagen | runData成功。追跡／未追跡を確認し、生成差分はja_jp／en_usの翻訳2ファイルのみ。resourceを手編集していない |
| 全GameTest | `ktlintFormat check :neoforge:runGameTestServer`成功、**152件の必須テストすべて成功**。新規worldへ固定依存で実行し、既存の数量・材料・加工・session・保存失敗回復も含む |
| 最終品質・通常JAR | `ktlintFormat check :neoforge:build :neoforge:verifyModJar`成功。追加のclientキー境界修正後にも再実行し成功 |
| JAR実内容 | 317 entries。core、中央UI、client Mixin、batch payload、翻訳を含む。旧GemProcessingPanel、外部MOD本体、tests、GUI fixture、skills、docsを含まない |
| 通常JARの専用server | 固定runtime依存9本のSHA256をmanifestへ照合、JEI／Gateways／fixtureなし。127.0.0.1:25581、過去の保存検証worldのコピーでDone 3.906s、reload、save-all flush、stop、exit 0 |
| 保存互換 | コピーworldのprobeに未解決Gem `missing:retained/future` 7個、転送設定、素材slot0 STONE3／slot5 DIAMOND2、素材補充ONを保持。reload前後に四componentを照会。元worldは上書きしていない |

通常JARは`neoforge/build/libs/sophisticated_gem_case-0.1.0-dev.jar`、SHA256は`da038996edf4a9176ac1217a06331c4de1bf656c02b51221352eae86f1806172`。専用serverを検証した途中JARと最終JARではclient実装とclient Mixin登録だけが異なり、その他の全ZIP entryの内容一致を確認した（`final-jar.json`）。専用serverのAuthlib公開鍵取得timeoutはログへ残るため、ERRORが0件とは扱わない。認証接続の検収ではない。

### 実クライアントでの観測

開発限定のNeoForge版JEI 19.51.0.418入り。明示init scriptからだけ一時source set／client fixtureを追加し、本番source set・通常JARへfixtureを入れない。fixtureは新規worldを作り、server threadでバッグ・Gem・素材を準備する。以後のクリックは固定MCのMouseHandler、キーはKeyboardHandlerを通した検証用イベント入力。OSでの人手操作や認証済み通常JAR接続とは区別する。画像はMinecraft framebufferのScreenshot.grabで採取した。

- 日本語・1280×960・GUI倍率4の320×240領域で、232×230の全画面が収まることを実画像で確認。12列バッグの背面に対し、種類18個、純度6枠、強化5個、素材2列×3行、player inventoryとhotbarを表示した。画像は`.local/ui-verification/screenshots/gem-case-central.png`。
- 純度欄の左クリックでGem1個を取り出し、カーソル投入で戻した。client/serverのcursorと保存状態の変化が一致した。
- 1回強化で素材gem_dust64→63、mysterious_scrap_metal64→61となり、client/serverが一致した。更新中は素材編集を止め、同世代preview再取得後に復帰することも確認した。
- 素材右クリックで63個から32個をcursorへ回収し、素材31個とcursor32個がclient/serverで一致した。
- Gemをcursorに持って画面外を押下・解放し、cursor、背面slot、同itemの近傍drop数が不変、素材view終了の`OUTSIDE_ASSERT_PASS`を得た。終了後の再表示と次回投入も機能した。

### 不具合検出とレビュー

独立レビューで、描画中にoverlay fieldをnullにするとCoreの後続tooltip描画でNPEになる経路と、閉じるクリックのreleaseが背面へ流れてcursorを投棄／別slotへ入れる経路を検出した。終了を描画後のtaskへ遅延し、閉じたgestureのreleaseだけをconsumeするよう修正。修正を独立担当が再読し、追加の未解決Critical／Importantなし。

固定MCではAbstractContainerScreen.removedがScreen.removedへ委譲しないため、終了Mixinの対象を直接の継承元へ修正した。CodeRabbit CLI 0.8.2もこの一件を報告し、固定ソースと照合して解消した。レビューの提案文を命令として実行していない。さらに実クライアントで、BackpackScreenがCoreより先にEscapeを処理してバッグ全体を閉じるREDを検出し、Gem Case表示中だけCoreへキーを直接委譲するclient Mixinを追加。固定JARのconstructor／継承／処理順序を照合し、追加差分の独立レビューでも重大指摘なし。修正後にnative Escapeを再実行し、バッグscreenとmenu IDを維持し、client/serverのcursorがGem1個のまま、素材viewが双方falseとなるGREENを確認した（escape-green.png）。表示中のupgrade差替えもoverlayを閉じて継続し、NPEなし（replaced-upgrade.png）。最後の検収clientはハーネスのstopで保存して正常終了した。

GUI検証環境では、初回にTemurin C2/PhaseLiveのnative crash、次にWSLgのswrast/LevelRendererでnative crashを記録した。一時起動だけC1とsoftpipeへ変更して実画像・操作検証に到達した。通常JAR・通常起動設定へこの回避策を追加していない。中断した起動をGradle成功とは扱わない。

右側は88pxの小パネルを展開する二段階の入口から、Gem Caseアイコン直接起動へ最終調整した。12列バッグの右余白44pxへ追加パネルをはみ出させないためで、最終変更のcompile／checkは成功。中央画面の実操作観測は共通の同じmodal実装に対するものだが、この直接起動の最終人手検収は未実施。

9列バッグ、英語の実表示、物理Shift、物理ボタンを押したままのEscape／resize、drag途中の配分予告、全操作の人手検収、Windows、認証接続、pack共存は未検収。player dragはrelease時にnativeへ確定する方式で、途中の配分予告まで標準画面と同じとは扱わない。素材へのdrag／数字キー／double clickは既存の安全境界を維持して拒否する。


## 保存済みタブの復元で入口をクリックできない不具合（2026-10-26）

ユーザーから、持ち物内のバックパックでGem Caseアイコンをクリックしても中央画面が開かないとの報告。初回の検収は中央画面の直接起動と新しいバッグ中心で、旧画面の展開状態を引き継ぐ条件が不足していた。

Core 1.5.2.2343は保存済みopen_tab_idに対応するcontainerをisOpen=trueにし、UpgradeSettingsTab.onAfterInitでSettingsTabBase.setOpen(true)を呼ぶ。本文のないGemCaseTabのopenTabDimensionは0×0なので、onTabOpenによりタブの高さが0となる。アイコンは子Widgetとして描画されても、親のクリック判定を通過しない。固定作者ソースと公開JAR bytecodeで照合した。Prism試遊instance内の本体と固定依存のSHA256は検証版と一致し、旧展開状態の保存値もread-onlyで確認した。ユーザーのworld・instanceを変更していない。

GemCaseTab.onAfterInitだけを上書きし、展開を復元せず閉じた入口の21×24 GUI pxを保つ。保存済みのopen_tab_id、Gem、素材、転送設定、サーバーの操作権限は変更しない。

検証は開発限定JEI 19.51.0.418、Java21＋一時C1／softpipe、新規worldのItem contextで実施。fixtureのバッグをplayer inventoryへ同期してから開き、実Tabの座標へ固定MC MouseHandler経由で押下・解放を送り、直後と12tick後のoverlayを検査した。fixtureから中央画面を直接生成する経路は使用しない。

| 条件 | 結果 |
| --- | --- |
| 修正前、設置・上位、旧展開状態なし | 21×24、ICON_ASSERT_PASS |
| 修正前、携帯・下位、旧展開状態なし | 21×24、ICON_ASSERT_PASS |
| 修正前、携帯・下位、open_tab_id=0 | 21×0、クリック直後と12tick後ともoverlay=null、ICON_ASSERT_FAIL |
| 同じ修正前コードで保存フラグだけ外す対照実験 | 21×24へ戻り、ICON_ASSERT_PASS |
| 修正後、携帯・下位、open_tab_id=0を保持 | 21×24、アイコンの押下・解放から中央画面が開き、12tick後も維持（ICON_ASSERT_PASS） |
| 修正後、下位の画面をEscapeで閉じて再クリック | 同じバッグで再度ICON_ASSERT_PASS |
| 修正後、携帯・上位、open_tab_id=0を保持 | ICON_ASSERT_PASS、素材6枠の表示と編集可否の同期を確認 |
| 修正後、GUI倍率4→3で再初期化後にクリック | 320×240→427×320、ICON_ASSERT_PASS |

ログは`.local/verification/ui/icon-saved-tab-red.log`、`icon-saved-tab-control.log`、`.local/ui-verification/icon-saved-green.log`。実画像は`.local/ui-verification/screenshots/icon-saved-tab-red.png`／`icon-saved-tab-green.png`。

`ktlintFormat check :neoforge:build :neoforge:verifyModJar`成功（icon-fix-build.log）。通常JARの差分をZIP entryごとに比較し、GemCaseTab.classと行番号の変わるGemCaseTabKt.class以外が同一であることを確認した。サーバー側、wire、保存codec、resourceは不変なので、先の152件の必須GameTestの対象実装は同じ。GUIの不具合をその成功だけで検収済みとは扱わない。追加の独立レビューでCritical／Importantの指摘なし。

修正版通常JARのSHA256は`4d093bd392b979906216baa6f348ee3382ce65c70f78a355d079fe833590b484`。client入口の修正だけで通信protocolは2を維持する。Prism側へは自動上書きせず、再起動して差し替える配布JARを用意する。


## 在庫ゼロ・操作説明・転送設定の整理（2026-10-26）

- 対応構成は既存のMinecraft 1.21.1／NeoForge 21.1.252と固定依存。新規fixtureワールド、携帯下位Gem Caseで検収。JEI 19.51.0.418は開発限定の既存構成を使用。
- 在庫0の覆いをアイテムより手前へ描画。種類一覧の在庫あり／なし、暗い種類のクリック選択、純度一覧の一部0／全て0をMinecraftの実スクリーンショットで確認。
- 転送設定の見出し・ラベルを通常文字へ、操作領域をMinecraft標準Button・EditBoxへ変更。ネイティブMouseHandlerのPRESS/RELEASEで搬出側のON/OFF、方式ALL→INCLUDE、種類選択を操作し、サーバーの設定スナップショットと再同期された表示で確認。種類→純度、検索flawless→1件、戻るも確認。右クリック・ラベル・余白は変更処理へ入らない標準Buttonの境界を使用。
- 右側メニューの通常ホバーで名前とShiftの案内を描画確認。全6種類の詳細キーがja_jp／en_us両方の生成resourceにあることを照合。Shift中はScreen.hasShiftDown()で2行の詳細へ切替。**Shift実キー入力による詳細表示は未検証**（検収補助のAWT Robotがheadless環境で失敗、X11側に対象Waylandウィンドウなし）。この失敗は検収fixture内で、本体エラーとは区別する。
- GUI scale 4→3では既存仕様どおりoverlayが閉じるため、アイコンをネイティブクリックして再表示。再配置後のボタン座標（戻る113,57、ON/OFF179,99）と設定変更OFF→ONを確認。閉じた直後に検収fixtureがpanelを参照したNullPointerExceptionはfixtureの操作順の誤りであり、本体は継続動作。
- `ktlintFormat`、`:neoforge:runData`成功。datagen差分はja_jp／en_usの2ファイルのみで追跡・未追跡を確認。その後の別呼出し`ktlintFormat check :neoforge:build :neoforge:verifyModJar`成功（59 tasks）。単体テスト・解析・core境界・通常JAR内容検査を含む。サーバー・保存・通信の変更はなく、GameTestと専用サーバー接続は今回再実行していない。
- 実入力と画像の証跡: `.local/ui-verification/refine-client.log`、同screenshotsの`refine-storage.png`、`refine-empty-selected.png`、`refine-settings.png`、`refine-settings-resized.png`。品質: `.local/verification/ui/refine-build.log`、生成: `refine-datagen.log`。
- Coreのoverlay入力境界は固定[StorageScreenBase.java](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase.java)と取得済み作者ソースを照合。overlay中のクリックは背後のslotへ渡らない。Button／EditBoxは固定Minecraft artifactでコンパイル・実描画を確認。
- 通常JAR SHA256: `eff83a526e09225cb04ce392fe6296d4be49a6bcfa25d3478060bfdca5d2d9f1`。Prismの導入先は自動上書きしない。


## プレイヤー向けの表記整理（2026-10-26）

- Shift案内はComponentのcopyへChatFormatting.BLUE／ITALICを適用。項目名を「ジェムをしまう」「リュックのジェムを移動」へ、説明の「通常収納」を「リュックの中身」へ変更。「未解決の保存データ」は「読み込めないジェム」とし、種類・ランクの記録と個数を保持している旨を説明。
- `ktlintFormat :neoforge:runData`成功。生成差分は日本語14項目・英語7項目の2ファイルのみ。別呼出し`check :neoforge:build :neoforge:verifyModJar`成功（43 tasks）。スタイル指定と生成済み翻訳キーを照合。表記・装飾のみのため実GUI／GameTestは再実行していない。
- ログ: `.local/verification/ui/wording-datagen.log`、`wording-build.log`。通常JAR SHA256: `771fa01ae4b2c2fb207e99263757d2b79ffd702a7a1539e6ff17204c786494cd`。


## 転送条件をアイコンで選ぶ画面（2026-10-26）

- 設定画面のみ横300へ拡張し、収納へ戻ると横232へ戻す。方向・方式・種類／ランク・検索・候補アイコン・ページ操作を整理。下段の見出しを条件の意味に合わせ、方式ALLでは選択を保持して無効化。候補の並びを選択で変えない。
- 種類は10×2セル、ランクは6×1セルに名前を併記。長い名前は2行。ランク名はアイテム名用修飾句ではなく固定Apotheosis 8.9.0の`Purity.toComponent()`を使用し、通常ランクも「ふつうの」と表示する。固定[Purity.java](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/Purity.java)と取得済み8.9.0 JARの翻訳を照合。可変寸法は固定CoreのWidgetBase.updateDimensions／StorageScreenBaseの中央配置を照合。
- 説明は「今のMOD構成では」に修正。各操作・候補アイコンへ青文字・斜体のShift案内と詳細説明を追加。種類／ランクの組合せ、空選択、既存Deposit／Restockアップグレードの必要性を説明する。ja_jp／en_usの全27タイトル・詳細キーの存在を照合。
- `ktlintFormat`成功、`:neoforge:runData`成功（生成差分はja_jp／en_usのみ、再生成written 0）。別呼出し`check :neoforge:build :neoforge:verifyModJar`成功（43 tasks）。最終のランク名変更後にも整形とcheck／build／verifyModJarを分けて再実行した。
- 新規fixtureワールド・携帯下位版・JEI入りの固定開発構成で、ネイティブMouseHandlerのPRESS/RELEASEを使用。ALL中の候補クリックで保存変更なし、INCLUDEで種類とランクを選択して保存、EXCLUDEへの切替で選択保持、補充側がALL／選択0のままで独立、種類のページ移動、molten検索で候補を絞込、戻る、GUI320×240→480×360後のアイコン再起動と設定の再表示を確認。ログは`transfer-client.log`、`transfer-final-client.log`。
- 最終JAR相当のコードでも新規ワールドを作り、アイコン→設定→INCLUDE→ランク→flawed選択をネイティブ入力で自動操作。サーバー側`purities=[flawed]`と金枠・チェック、全6ランク名（通常も含む）を画像で確認。`TRANSFER_SMOKE_NATIVE_INPUT_COMPLETE`、その後の正常終了を確認。
- **Shift実キーによる展開は未検証**。検収fixtureのAWT headlessを解除しても、Robotの入力がゲーム側のShift状態へ反映されなかった。通常ホバーの青文字・斜体と、展開分岐・両言語の詳細キーは確認。未知の保存済み設定の解除と実外部転送・GameTest・専用サーバー接続は今回再実行していない。
- 品質: `.local/verification/ui/transfer-build.log`、整形: `transfer-format.log`、生成: `transfer-datagen.log`。実GUI: `.local/ui-verification/transfer-ranked-client.log`、同screenshotsの`transfer-final-ranked.png`（最終ランク表示）、`transfer-final-types.png`、`transfer-resized.png`。起動環境だけ既存のsoftware描画／TieredStopAtLevel=1を使用。
- 通常JARを直前版とentry単位で比較し、変更がclient UI・言語・datagenのみであることを確認。保存・通信・サーバー処理のbytecodeに差分なし。SHA256: `fb8aef146e4057df8c49c1933a19cbf76bcf9cf97128f5c5083c1cb015660b33`。


## 詳細説明の拡充と段落表示（2026-10-26）

- 収納側メニュー6項目、転送設定9項目、候補の条件説明・未知項目の説明の計17項目を日本語／英語で書き直した。詳細はタイトル＋固定2行から段落のリストへ変更。3方式、種類とランクの組合せの例、片軸／両軸空選択、方向ごとの独立、検索やページ外の選択保持、手動収納への非適用、素材の参照先と消費時点を説明する。
- 翻訳はdatagen入力`GemHelpTranslations.kt`に分離。生成済みlangを手編集せず、`ktlintFormat :neoforge:runData`成功後、別呼出し`check :neoforge:build :neoforge:verifyModJar`成功（43 tasks）。生成差分はja_jp／en_usの2ファイル、各17キー追加。保存・通信・サーバー処理は変更なし。
- `Font.split`で段落と書式を維持し、最大300px（画面幅−24px以下）へ折り返して、固定行数で切り捨てずに`GuiGraphics.renderTooltip(Font, List<FormattedCharSequence>, int, int)`で描画。APIは固定Minecraft 1.21.1／NeoForge 21.1.252の生成artifactのsignature・コンパイルで確認。
- 固定依存・JEI入り・新規fixtureワールドで起動。GUI320×240の実Minecraft Fontを使い、両言語の全17説明を計測して高さが収まることを確認。タイトル込みの最大行数は日本語14、英語18。`HELP_LAYOUT_PASS`を両言語で観測。
- 長文「どのジェムを転送するか」を検収fixtureから展開状態の内容で描画し、`HELP_PREVIEW_RENDERED`とスクリーンショットで全文の折り返し・画面内への収まりを確認。この検査は描画のプレビューであり、実Shift入力の確認とは区別する。実Shift入力、GameTest、専用サーバー接続は今回再実行していない。
- ログ: `.local/verification/ui/help-datagen.log`、`help-build.log`、`.local/ui-verification/help-preview-client.log`。画像: `.local/ui-verification/screenshots/help-expanded-preview.png`。クライアントは正常終了し、新規fixtureワールドを保存している。
- 直前JARとentry単位で比較し、変更がclient・言語・datagen・新規トップレベル関数のKotlin module metadataのみで、サーバー処理のbytecodeに差分がないことを確認。記録: `.local/verification/ui/help-jar.json`。通常JAR SHA256: `6f52095b819f2736fdca0309acefe194ea9b36ff84cfd068b1c649b442ddd67b`。


## 元のGem Caseを踏まえたプレート型アイコン（2026-10-26）

- 既存の16px原稿`art/item-icons/render.py`とpaletteを変更し、下位・上位・変換の3PNGを再生成。共通のプレート枠に、元のGem Caseの濃い木箱・紫帯・金属留め具を組み合わせた。下位は銀灰色の枠、上位は金枠とハンマー、変換は紫の機構面と上向き金矢印で区別する。外部MODのtextureは複製していない。
- 標準ライブラリのみで生成し、二度の生成で三PNGのSHA256一致を確認。すべて16×16／RGBA／透過を持つpixel原稿。モデル・登録・recipe・renderer・保存・通信の変更なし。プレビュー出力先は`.local/verification/item-icons/`へ分離し、過去のacquisitionプレビューは当時の原稿から復元した。
- `check :neoforge:build :neoforge:verifyModJar`成功（43 tasks）。通常JARを直前版とentry単位で比較し、差分が三PNGだけであること、同梱PNGと生成物が一致することを確認。画像原稿は通常Gradle buildへの依存にしない。モデルやrecipe入力を変えていないためrunData／GameTest／専用サーバーは今回再実行していない。
- 既存の固定依存・JEI入り開発構成で新規fixtureワールドを作成。960×720／GUI320×240（倍率3）で三itemを持ち物欄へ並べ、欠落や紫黒textureがなく、下位・上位・変換の図柄を見分けられることを実画像で確認。下位の装着slot・入口タブにも新アイコンが表示され、ネイティブクリックで開くことを確認。倍率2／4やJEI一覧上の見え方は今回未検証。検収clientは正常終了している。
- 品質ログ: `.local/verification/ui/item-icons-build.log`、生成再現性・JAR差分: `.local/verification/item-icons/verification.json`。原寸／8倍比較: 同ディレクトリの`icons-original.png`／`icons-preview.png`。実GUI: `.local/ui-verification/item-icons-client.log`と同screenshotsの`item-icons-ingame.png`。
- 通常JAR SHA256: `762cd402e0c2f5463e1d9d5dc8c5a355225ff04e68244cbc57ce9586c6d7533b`。Prismの導入先は自動上書きしない。


## 採用した幅広い結晶マークの反映（2026-10-27）

- ユーザーが採用したTerra 6.1試作の右側（wide）を下位アイコンへそのまま使用。採用原稿の合成PNGと下位の合成プレビューが、RGBAの全画素で一致することを確認。結晶のbboxは(4,3)〜(11,12)、中心は(7.5,7.5)。上位と変換の結晶マスクも同一で、上位は金色四隅、変換は右下の上向き印だけを追加した。
- 三item modelはlayer0に元MODの`sophisticatedbackpacks:item/upgrade_base`、layer1に自作の透過マークを設定。外部基板PNGは配布に複製しない。固定Backpacks 3.26.6.2174の`assets/sophisticatedbackpacks/models/item/upgrade_base.json`と`textures/item/upgrade_base.png`を配布JARで照合（取得元・hashは[対応構成](compatibility.md)と[固定配布版](https://modrinth.com/mod/sophisticated-backpacks/version/pJxNzk4X)）。
- datagenで依存MODの参照検査を有効にするため、data runへ`--existing-mod sophisticatedbackpacks`を追加。固定MC1.21.1／NeoForge21.1.252のMainのOptionParserと実行で対応を確認。既存resource検査は省略していない。
- `ktlintFormat :neoforge:runData`成功、追跡・未追跡の生成差分は三item modelのみ。別呼出し`check :neoforge:build :neoforge:verifyModJar`成功（43 tasks）。PNGを二度生成してSHA256一致を確認。三PNGは16×16 RGBAで、全ての結晶画素が採用原稿と一致する。
- 固定依存・JEI入り開発構成、新規fixtureワールド、960×720／GUI320×240（倍率3）で三itemを持ち物欄に並べ、基板とマークの合成、上位の四隅、変換の識別印、装着slotと入口タブを実画像で確認。アイコンのネイティブクリックでGem Caseを開く`ICON_ASSERT_PASS`を観測。欠落texture・表示時例外なし、clientは正常終了。倍率2／4・JEI一覧・実player専用サーバー接続・GameTestは今回再実行していない。
- ログ: `.local/verification/ui/hex-adopt-datagen.log`、`hex-adopt-build.log`、`.local/ui-verification/hex-adopt-client.log`。原稿比較・再現性: `.local/verification/item-icons/hex-adopt-checks.json`。基板との合成: `hex-adopt-preview.png`。実GUI: `.local/ui-verification/screenshots/hex-adopt-ingame.png`。
- 通常JARを直前版とentry単位で比較し、差分が三PNG・三モデル・datagenクラス群のみで、保存・通信・加工・その他ゲーム処理のbytecodeに差分がないことを確認。比較: `.local/verification/item-icons/hex-adopt-jar.json`。通常JAR SHA256: `61a666c681b6c2d4a5868c1171099529a67dc043836f9937afb6283482b2d28f`。Prism側のJARは自動上書きしない。

## Changesetsによる自動リリース（2026-11-22）

- Node 24.14.0、pnpm 12.8.1、Changesets CLI 2.31.1で固定ロックから導入し、スクリプトテスト31件が成功。一時リポジトリでpatchを適用し、`package.json` と `gradle.properties` の `0.1.0-dev` が `0.1.0` へ更新され、Changesetの消費、変更履歴生成、`v0.1.0` の検出、同じ版での再検出の省略を確認した。workflowはactionlint 1.7.12で検査した。
- Temurin 21.0.12.1で `check :neoforge:build :neoforge:verifyModJar` が成功。リリース版 `0.1.0` でdatagen後に生成リソースの追跡・未追跡差分がないことを確認。その後に通常JARを検査し、必須GameTest 152件が成功した。
- 同じJARをMavenローカルへ登録し、`org.mocoserver:sophisticated-gem-case:0.1.0` のPOMとJAR内の版を確認した。再ビルド分、登録前、登録後のJARがバイト単位で一致した。SHA256は `554a2bbd5ae7dfbed9ea8a3bc022524e45efcf553b25396eb00fbca315c7bf8f`。ログは `.local/release-smoke.eOLu9U/` に保存した。
- GitHub CLIとHTTP応答の模擬実行で、初回公開、Packages登録済みからの再試行、Packages登録失敗での公開中断、異なるコミットを指すタグの拒否を確認した。GitHub上のVersion PR作成、ReleaseおよびPackagesへの実登録、通常JARでの専用サーバー・クライアント接続は今回未実施。
