# クライアント検収

試遊ZIPは親packのdist/mod-template-trial/minecraft-mod-template-0.1.0-client-trial.zip。Minecraft 1.21.1／NeoForge 21.1.252／Java 21の新規クライアントへmodsを追加する。既存の試遊instanceや保存ワールドを上書きしない。

ZIPは通常MOD JAR・KotlinForForge配布用5.5.0・クライアント専用JEI 19.51.0.418と構成記録を含む。Minecraft本体・loader installer・認証情報・ワールドは含まない。個々のSHA256はmanifest.jsonで照合できる。

1. 認証済みランチャーで新規の対象版instanceを作り、ZIPのmodsを導入する。
2. 日本語表示でサンプルトークンの名前・アイコンを確認する。JEIで検索し、鉄インゴット1個＋紙1個からトークン1個のレシピを確認する。
3. 新規の専用サーバーへ接続し、作業台で実際にクラフトして1個取得する。試遊サーバーの起動時だけ127.0.0.1:25568を使用する。停止中は起動を依頼する。
4. client／server両側の版・接続・クラフト結果と観測時刻を記録する。GameTest成功だけで表示や実操作を検収済みとしない。

専用サーバーは通常JAR＋KotlinForForge配布JARだけ。JEIとGameTest fixtureは入れない。ローカル検証サーバーはloopbackの新規ワールド・online-mode=falseであり、アカウント認証の検証とは扱わない。

表示・認証済みclient接続・実操作はユーザー検収待ち。実装と自動検証が成功しても完全検収済みとは記載しない。
