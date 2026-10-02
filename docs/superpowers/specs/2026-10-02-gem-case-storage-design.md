# 下位Gem Caseアップグレードの設計案

状態: 2026-10-02の対話で承認済み。起点はmainの`51f69c9`。実装計画の確認へ進む。

## 目的と完了条件

[現在の設計](../../design.md)の工程2を対象にする。バッグ内にGem専用収納を追加し、装着した下位アップグレードから種類・純度別の個数を確認し、手動投入、取出、通常収納からの一括移動を行えるようにする。容量は種類×純度ごとに32,767個。バッグtierとStackアップグレードの倍率を適用しない。

今回は加工・材料6枠・上位アイテム・装着中変換・専用Deposit／Restock設定を実装しない。Pickupなど既存アップグレードとの連携は工程3で検収する。完成した下位版として配布する区切りは、その連携工程も終わった後とする。

## 比較した保存方法

推奨はアップグレードItemStackの独自data componentへ個数を保存する方法。SophisticatedのUpgradeWrapperBaseの保存callbackを使え、後続の上位化にも保存状態を渡せる。通常スロットを占有せず、独自のワールド保存ファイルやUUID管理を増やさない。

バッグの独自保存領域へ直接置く方法は、取り外した空アップグレードの状態とバッグ側の残存データを別に管理する必要がある。通常GemCaseTileの再利用は超過分を失う契約を含むため採用しない。

### 追加componentを持つGem

提案する既定方針は、標準Gemだけを個数保存し、名前変更など追加componentを持つGemは通常収納へ残すこと。実Gemアイテムであること、種類が解決済みであること、純度が種類の最低純度以上であることを確認した上で、作者APIで同じ種類・純度から作ったcount=1の標準stackとcomponentを比較する。入力個数は比較対象から除外する。純度を暗黙に補正して受け入れない。

これにより取出時の再生成で追加データを失わない。追加component付きGemを通常収納に残す方針を設計とともに承認済み。将来それらも専用収納へ入れる場合は、component別の保存と取出選択を別途設計する。

## coreとNeoForgeの役割

coreにはGem種類ID・純度文字列をキーとする個数モデル、容量判定、投入／取出の計算だけを置く。入力は変更しない値として扱い、変更後の値と受入数／残量を返す。個数は0から32,767、0のキーは保存しない。不正な要求個数を負数やoverflowに変換して計算しない。内部にもItemStack・NBT・loader・外部MODの型を入れない。

NeoForge側はGemの識別・標準stack比較・取出stack生成、data componentとの変換、実インベントリへの変更確定、アップグレードの登録、menu／通信を担当する。画面・描画・入力イベントのクラスはclient packageへ隔離する。ゲームから独立しない汎用repositoryやinventory抽象をcoreへ先行追加しない。

## 保存形式と互換性

独自componentのIDは`sophisticated_gem_case:gem_storage`。payloadは`schema_version: 1`と`entries`の列で構成し、各entryは`gem_id`、`purity`、`count`を持つ。変更時は新しいpayloadを作ってcomponentを置き換え、wrapperの保存callbackを呼ぶ。共有された可変NBTや外から受け取ったMapをそのまま保持しない。

schema 1の処理上限は4,096 entry、ID／純度文字列はそれぞれUTF-8で256 byte、payloadはエンコード相当の1 MiB、構造の深さは8とする。adapterは上限付きの走査で検証し、超過を検知したら走査を打ち切る。新規entryを追加できない場合は投入元へ残量を返す。上限超過の既存payloadは原文を変更せずロックし、画面には概要のエラーだけを出す。復旧のために黙って切り詰めない。これはアドオンの処理量の制約であり、ゲーム本体のNBT読込制約を置き換えない。

種類IDと純度の組を重複保存しない。保存順は種類ID、次に純度文字列で固定し、表示順と分離する。上位専用材料や転送設定の空欄は追加しない。後続でschemaを拡張する場合はversionに対する移行処理を用意する。

data componentがない空アップグレードだけを空収納として扱う。未知のschema、重複キー、不正個数、不正な文字列などを持つpayloadは原文を保持して収納変更を禁止する。読込失敗を空収納に置き換えたり、正常化して上書きしたりしない。componentのpayload自体は保持できる形式とし、意味の検証はadapterで行う。

形式が正しい未解決Gem ID／未知純度は数を保持し、解決できる他のentryの操作を妨げない。取出と新規投入はそのentryについて禁止し、一覧にはIDと個数、利用できない理由を表示する。データパック変更で最低純度が変わったentryも自動変換しない。再解決したら操作可能になる。これらも中身として取り外し禁止の判定に含める。

## 装着と取り外し

下位item IDは`sophisticated_gem_case:gem_case_upgrade`、表示名は「Gem Caseアップグレード」とする。バッグに合わせて一個までを個別条件で強制し、設定によって上限を増やせないようにする。将来の上位版と同じ判定を共有できるよう、専用の識別条件を定義する。今回は上位itemを登録しない。

canAddUpgradeTo、canRemoveUpgradeFrom、canSwapUpgradeForの公開APIで、装着先、既存の専用アップグレード、中身を確認する。同一itemへの交換も中身を失う操作なら拒否する。中身ゼロのときだけ通常取り外し・交換を許可する。読めないpayloadも安全な空とはみなさず拒否する。表示だけをclient側で判断しても、最終判断はserver側で再実施する。

## 投入と移動の契約

手動でバッグへ投入する標準Gemは専用収納、通常収納、投入元の順で振り分ける。専用収納が満杯でも通常収納へ入る分は受け入れる。無効Gem・追加component付きGem・非Gemは通常収納の既存処理へ渡す。専用タブの投入操作も、入らない分をカーソルに残す。simulateでは専用データ、元stack、通常収納、保存callbackの呼出数を変更しない。

通常収納からの一括移動はそのバッグだけをslot順に走査し、標準Gemの受入可能分だけ移す。同じ操作でプレイヤーの持ち物や別バッグを走査しない。既存slotのアクセス制限に従い、移動できない分は元slotへ残す。

工程2のcapability対象は、設置バッグのblock capabilityと携帯バッグのitem capabilityが外部へ公開する`getInventoryForInputOutput`系handlerである。その全体／slot別投入に同じ優先規則を適用する。既存アップグレードが内部で使う`getInventoryForUpgradeProcessing`は対象外とし、工程3で接続する。外部MODが公開capabilityへ直接入れる操作は工程2の対象だが、自動回収能力や独自Deposit／Restockを追加するものではない。slot別投入は元handlerのslot範囲、isItemValidとその他の投入先制限を先に守り、拒否されるslotから専用収納へ迂回しない。通常slotの単なる容量不足はこの拒否条件に含めず、投入が許可されるslotなら専用収納の空きへ受け入れる。範囲外slotの例外／拒否は元handlerの契約を維持し、専用側を変更しない。全体投入でも公開handlerのアクセス制限を保持する。専用収納を通常slotとして公開せず、通常収納からの抽出の意味は変えない。専用収納の取出は本工程ではmenu操作だけで提供する。

公開APIだけでは手動GUIの全経路を捕捉できないため、固定版StorageContainerMenuBaseのクリック／Shift移動／merge経路に限定したMixinを使う。アドオン未装着、他のアイテム、対象外操作では既存処理へ渡す。バッグ設置時と携帯時のcapabilityの生成箇所は実装計画作成時に対象版ソースと照合し、挿入経路の実行テストを作る。通常slotへの左／右クリック、Shift移動、ドラッグ分配、数字キー交換は別経路として確認し、交換ではslotの元アイテムを消さずに処理する。操作全体を安全に扱えないケースでは変更前に拒否し、専用への部分投入後に通常処理を再実行しない。IInsertResponseUpgradeだけに依存しない。

本工程では独立した自動回収イベントやDeposit／Restock参加wrapperを追加しない。通常の処理用インベントリに専用収納を含める接続と、その副作用の検証は工程3で行う。

## 画面と操作

既存バッグ画面に専用アップグレードタブを追加する。種類一覧と名前検索、選択種類の6純度の個数、投入操作、一括移動ボタンを置く。解決済み種類は表示名と種類IDで並べ、未解決entryは別に識別表示する。表示用stackの個数を保存数として扱わない。

純度の取出は左クリックでカーソルへ一つ、右クリックでカーソルへ最大一stack、Shift＋左クリックでプレイヤー通常インベントリへ最大一stackを移す。取出先に入る分だけ減らし、空きがなければ無変更。stack最大数は取出stackの実際の上限を使う。ドロップや他プレイヤーへの直接転送操作は追加しない。数量、容量、入力対象の制約、拒否理由はja_jp／en_usを用意する。

入力は瞬間的に収納へ移す操作とし、アップグレード内に別の一時入力inventoryを保存しない。タブを閉じたり装着を外したりしても、一時枠の残りを扱う経路を増やさない。

## 通信と確定処理

clientからは操作種別、選択キー、開いているmenuと表示revisionだけを送る。保存payload、変更後の個数、出力stackを信用しない。サーバースレッドで現在のmenu、同じバッグとupgrade slot、アクセス権、装着状態、キー、受入・取出先を再確認する。古いrevisionの要求は無変更で最新表示へ同期する。

初回表示と更新時の一覧はページ単位で送る。一ページは最大64 entryかつエンコード後32 KiBとし、少ない方で区切る。同じ種類が複数ページに分かれる場合はclientが一覧を結合する。表示名はclientが種類IDから解決し、serverから巨大な翻訳済み文字列を送らない。client入力のページ番号とUTF-8で256 byte以内の検索文字列を検証する。未解決IDもserverから送る。検索の対象と総件数はserverの一覧を基準にし、選択・数量は常にserverが正とする。原文の破損payloadをclientへ送らない。専用材料枠は存在しないので同期しない。

専用収納・プレイヤー・バッグ通常収納の変更は、対象の現在状態から受入可能数と変更予定を組み立ててから確定する。途中拒否や古い状態を検出した場合は素材・出力を変更しない。任意の外部inventoryとの原子的転送をこの工程の保証に含めない。capability投入は標準的なaccepted/remainder契約を守り、呼出元stackを直接変更しない。

## 入手と見た目

本工程では創造モードと管理コマンドで入手して検証する。サバイバル用レシピの素材・コストと最終textureは後続の仕上げ工程で決める。仮のitem modelとタブ表示で下位版を識別可能にする。sample_tokenは基盤検証用として保持する。

## 受け入れ検証

- core単体: 0／1／32,766／32,767境界、超過残量、キー別容量、負数・overflow拒否、値の所有権と入力不変。
- 保存GameTest: component往復、再取得wrapper、設置／回収、バッグtier変更で個数維持。未知IDと未知純度の保持、破損／将来schemaを変更しない。
- 装着GameTest: 二個目拒否、中身あり取外し／同一item交換拒否、空で取り外し許可、無効データでも拒否。
- 移動GameTest: 通常収納満杯でも専用空なら投入可、専用満杯から通常収納へ、双方満杯の残量、simulate無変更、一括移動の部分受入、標準以外のGemのcomponent保持。
- menu／通信: カーソルとShift取出、取出先満杯、同一要求再送、古いrevision、別バッグ・閉じたmenu・不正キーを拒否。携帯／設置バッグの手動操作、slot別capability投入、拒否slotと範囲外slotから専用収納へ迂回しないことを検証する。
- 品質・配布: check、datagenと再生成差分、通常JAR内容検査、GameTestの実行件数、新規専用サーバー起動。対話的client＋開発限定JEIで表示／操作を確認し、未実施なら明記する。
- 独立した安価なサブエージェントが最終差分とログをレビューする。実装担当との会話履歴は渡さない。

## 対象版の根拠

Minecraft 1.21.1、NeoForge 21.1.252、Apotheosis 8.9.0、Sophisticated Backpacks 3.26.6.2174、Sophisticated Core 1.5.2.2343。[対応構成](../../compatibility.md)を変更しない。

公開APIとMixin候補の根拠は[連携調査](../../integration-research.md)。今回追加確認した保存callbackは[UpgradeWrapperBase](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/upgrades/UpgradeWrapperBase.java)、標準stackの生成・識別は[GemItem](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/GemItem.java)と[UnsocketedGem](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/UnsocketedGem.java)。API採用とMixinの具体的な適用箇所は対象JARにも照合し、実装計画へ残す。

## 設計レビュー記録

独立したgpt-6-lunaによるレビューで、工程2のcapability境界と、保存・同期データの量的上限を明確にする指摘を受けた。上記に具体的なhandler境界と処理上限を追加し、再確認で解消された。再確認で追加されたslot別投入の制限迂回の指摘にも、元handlerの制限を先に守る契約と受け入れテストを追加した。その後ユーザーが本設計を承認した。本番コードはまだ追加していない。

## 実装計画作成時の確認

通常ItemStack同期でもcomponentのnetwork codecが使われる。保存原文の転送を避けるため、永続codecとnetwork codecを分け、後者は中身有無とロック状態だけの要約を送る。一覧は上記のページ通信を使う。要約をサーバーの保存原文として受け入れない。[対象版のData Components公式資料](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)とローカルのMinecraft 1.21.1／NeoForge 21.1.252ソースで照合した。

InventoryIOHandlerは内部でgetInventoryForUpgradeProcessingを取得する。工程2では処理用handler自体を変更せず、BackpackWrapper.getInventoryForInputOutputの戻り値へ専用層を置く。block capabilityの方向制限と失敗投入cache、item capabilityのUUID未付与時の拒否は既存側へ残す。具体的な対象メソッドとテストは[実装計画](../plans/2026-10-02-gem-case-storage.md)へ記載する。
