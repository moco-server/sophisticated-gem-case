# Sophisticated Gem Caseの検証

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
