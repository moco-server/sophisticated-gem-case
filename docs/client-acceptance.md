# クライアント検収

## 今回用意したものと確認範囲

`.local/client-trial/mods`へ通常JAR、KFF配布JAR、対象MOD・関連依存、クライアント限定のJEIを揃えた。公開依存の版・取得URL・hash・sideは[dependency-manifest.json](dependency-manifest.json)、本アドオンのJAR hashは[verification.md](verification.md)を参照する。GatewaysとGameTest fixtureは試遊clientへ入れていない。

開発クライアントはMinecraft 1.21.1／NeoForge 21.1.252／Java 21で起動し、NeoForge版JEI 19.51.0.418の検出とJEI GUI atlas生成まで確認した。認証済みランチャーでの接続、画面の見た目、日本語表示、レシピ検索、実クラフトは未検収。現在Gemアップグレードは未実装なので、Gem収納・転送・加工・強化の操作はまだ検収できない。

## 基盤の試遊手順

1. 認証済みランチャーでMinecraft 1.21.1／NeoForge 21.1.252の新しいinstanceを作る。既存instanceや保存ワールドを上書きしない。
2. `.local/client-trial/mods`のJARをコピーし、manifestとhashを照合する。`.local`はGitへ追加しない。
3. サンプルトークンの表示と、JEIで鉄インゴット1個＋紙1個から1個作るレシピを確認する。
4. 必要なら新規の専用サーバーへ接続し、実クラフトを確認する。基盤検証サーバーは通常JARと固定依存だけで127.0.0.1:25579へ起動確認済みだが、検証後は停止した。
5. client／serverの版、表示・接続・クラフトの観測結果を検証記録へ追加する。開発起動やGameTest成功を実操作の検収と置き換えない。

今回の検証サーバーはローカルの新規ワールド、online-mode=falseであり、アカウント認証を検証したものではない。一般公開するサーバー設定として使わない。

## Gem機能の後続検収

下位・上位を実装した工程で、専用収納優先と残量返却、別々のGemフィルタ、Pickup／Deposit／Restockの装着条件、材料不足時の無変更、装着中上位化、再起動とバッグ設置／回収を検収する。通常JARの専用サーバーへの接続を含め、対象版のGem Caseとの比較結果を残す。
