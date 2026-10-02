# クライアント検収

## 開発clientの構成と既存確認範囲

`.local/client-trial/mods`へ通常JAR、KFF配布JAR、対象MOD・関連依存、クライアント限定のJEIを揃えた。公開依存の版・取得URL・hash・sideは[dependency-manifest.json](dependency-manifest.json)、本アドオンのJAR hashは[verification.md](verification.md)を参照する。GatewaysとGameTest fixtureは試遊clientへ入れていない。

開発クライアントはMinecraft 1.21.1／NeoForge 21.1.252／Java 21で起動し、NeoForge版JEI 19.51.0.418の検出とJEI GUI atlas生成まで確認した。認証済みランチャーでの接続、画面の見た目、日本語表示、レシピ検索、実クラフトは未検収。この既存確認はGem Case機能の実操作を確認した結果ではない。工程2の試遊状況は下記チェックと[検証記録](verification.md)に記録する。

## 基盤の試遊手順

1. 認証済みランチャーでMinecraft 1.21.1／NeoForge 21.1.252の新しいinstanceを作る。既存instanceや保存ワールドを上書きしない。
2. `.local/client-trial/mods`のJARをコピーし、manifestとhashを照合する。`.local`はGitへ追加しない。
3. サンプルトークンの表示と、JEIで鉄インゴット1個＋紙1個から1個作るレシピを確認する。
4. 必要なら新規の専用サーバーへ接続し、実クラフトを確認する。基盤検証サーバーは通常JARと固定依存だけで127.0.0.1:25579へ起動確認済みだが、検証後は停止した。
5. client／serverの版、表示・接続・クラフトの観測結果を検証記録へ追加する。開発起動やGameTest成功を実操作の検収と置き換えない。

今回の検証サーバーはローカルの新規ワールド、online-mode=falseであり、アカウント認証を検証したものではない。一般公開するサーバー設定として使わない。

## Gem Case工程2の試遊手順

通常JARと固定依存を使い、新規client instance／新規worldで確認する。試遊に使うJARの配置場所は`.local/client-trial/mods`。今回の成果物のhashは検証記録と照合する。JEIは開発client限定とし、本体の必須依存にしない。

1. 上記の基盤試遊手順に従い、Minecraft 1.21.1／NeoForge 21.1.252／Java 21の新規instanceを作る。通常JARとmanifest記載の依存、JEIの版・取得元・hash・sideを照合する。既存worldを使わない。
2. 新規worldで創造モードまたは管理コマンドからGem Caseアップグレードを入手し、バッグへ装着する。recipeはない。仮texture・item名・タブ表示、日本語表示を確認する。
3. Apotheosisの標準Gem、追加component付きGem、非Gemを分け、画面のカーソル入力と通常slotへの左／右クリック、Shift移動を試す。専用へ入る数、通常収納へ回る数、残る数が入力元と一致することを確認する。
4. 純度別の表示、検索、スクロール、一個取出、最大stack取出、Shift取出、一括移動を試す。満杯のcursor／player inventoryでは保存数と出力が変わらないことを確認する。
5. 上限付近、未解決ID／純度、ロックpayload、二個目装着、中身あり取外し・交換を安全なテストデータまたはGameTestで確認する。通常操作で破損payloadを作らない。
6. バッグを設置／回収し、tier変更と再起動後に保存内容を確認する。必要なら新規の専用サーバーへ通常JARのみで接続し、clientとserverの版・接続結果を記録する。
7. 各項目を「確認済み」「未確認」「失敗」として観測結果付きで[検証記録](verification.md)へ記録する。起動やGameTestの成功を画面操作の確認として扱わない。

### 試遊チェック

以下は検収項目であり、チェックが空欄の間は実操作の成功を意味しない。

- [ ] 構成・JAR hash・依存sideを照合
- [ ] 創造／管理入手、装着、二個目拒否、取り外し条件
- [ ] 仮texture、upgrade tab、一覧、純度別個数、日本語表示
- [ ] 標準Gem入力、追加component付きGemと非Gemの通常収納経路
- [ ] 左／右クリック、Shift移動、slot制限・フィルタ拒否、残量保持
- [ ] 検索、スクロール、一個／stack／Shift取出、一括移動
- [ ] 出力先満杯時の無変更
- [ ] 保存・再起動、設置／回収、tier変更
- [ ] 通常JARの専用サーバー接続（実施した場合）

Pickup／Deposit／Restock連携と専用設定・フィルタ、上位版の6材料枠・加工・補充・上位化は後続工程の検収対象であり、この工程2の試遊で確認済みとはしない。
